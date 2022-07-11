package com.atguigu.srb.core.service.impl;

import com.alibaba.excel.EasyExcel;
import com.atguigu.srb.core.listener.ExcelDictDTOListener;
import com.atguigu.srb.core.mapper.DictMapper;
import com.atguigu.srb.core.pojo.dto.ExcelDictDTO;
import com.atguigu.srb.core.pojo.entity.Dict;
import com.atguigu.srb.core.service.DictService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 数据字典 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Service
public class DictServiceImpl extends ServiceImpl<DictMapper, Dict> implements DictService {

    @Resource
    private DictMapper dictMapper;

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Transactional(rollbackFor = {Exception.class})
    @Override
    public void importData(InputStream inputStream) {
        EasyExcel.read(inputStream, ExcelDictDTO.class, new ExcelDictDTOListener(dictMapper)).sheet().doRead();
        log.info("Importing excel data");
    }

    @Override
    public List<Dict> listByParentId(Long parentId) {

        // query from redis first
        // log if redis connect failed and don't throw exception
        try {
            log.info("Querying dict data from redis");
            List<Dict> dictList = (List<Dict>) redisTemplate.opsForValue().get("srb:core:dictList:" + parentId);
            if(dictList != null)
                return dictList;
        } catch (Exception e) {
            log.error("Redis connect error: {}", ExceptionUtils.getStackTrace(e));
        }

        // query from mysql if can't query from redis
        log.info("Querying dict data from mysql");
        QueryWrapper<Dict> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("parent_id", parentId);
        List<Dict> dictList  = dictMapper.selectList(queryWrapper);
        dictList.forEach(dict -> {
            QueryWrapper<Dict> dictQueryWrapper = new QueryWrapper<>();
            dictQueryWrapper.eq("parent_id", dict.getId());
            dict.setHasChildren(dictMapper.selectCount(dictQueryWrapper) > 0);
        });

        log.info("Saving dict data to redis");
        try {
            redisTemplate.opsForValue().set("srb:core:dictList:" + parentId, dictList, 5, TimeUnit.MINUTES);
        } catch (Exception e) {
            log.error("Redis save data error: {}", ExceptionUtils.getStackTrace(e));
        }


        return dictList;
    }

    @Override
    public ArrayList<ExcelDictDTO> listDictData() {
        List<Dict> dictList = dictMapper.selectList(null);
        ArrayList<ExcelDictDTO> excelDictDTOArrayList = new ArrayList<>();
        dictList.forEach(dict -> {
            ExcelDictDTO excelDictDTO = new ExcelDictDTO();
            BeanUtils.copyProperties(dict, excelDictDTO);
            excelDictDTOArrayList.add(excelDictDTO);
        });

        return excelDictDTOArrayList;
    }

    @Override
    public List<Dict> findByDictCode(String dictCode) {
        QueryWrapper<Dict> dictQueryWrapper = new QueryWrapper<>();
        dictQueryWrapper.eq("dict_code", dictCode);
        Dict dict = dictMapper.selectOne(dictQueryWrapper);
        List<Dict> dictList = this.listByParentId(dict.getId());
        return dictList;

    }

    public String getNameByParentDictCodeAndValue(String dictCode, Integer value) {
        QueryWrapper<Dict> dictQueryWrapper = new QueryWrapper<>();
        dictQueryWrapper.eq("dict_code", dictCode);
        Dict parentDict = dictMapper.selectOne(dictQueryWrapper);

        if (parentDict == null)
            return "";

        dictQueryWrapper = new QueryWrapper<>();
        dictQueryWrapper
                .eq("value", value)
                .eq("parent_id", parentDict.getId());
        Dict dict = dictMapper.selectOne(dictQueryWrapper);
        if (dict == null)
            return "";

        return dict.getName();


    }
}
