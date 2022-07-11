package com.atguigu.srb.core.service;

import com.atguigu.srb.core.pojo.dto.ExcelDictDTO;
import com.atguigu.srb.core.pojo.entity.Dict;
import com.baomidou.mybatisplus.extension.service.IService;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * <p>
 * 数据字典 服务类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
public interface DictService extends IService<Dict> {
    void importData(InputStream inputStream);

    List<Dict> listByParentId(Long parentId);

    ArrayList<ExcelDictDTO> listDictData();

    List<Dict> findByDictCode(String dictCode);

    String getNameByParentDictCodeAndValue(String dictCode, Integer value);

}
