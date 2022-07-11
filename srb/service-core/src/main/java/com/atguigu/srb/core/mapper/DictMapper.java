package com.atguigu.srb.core.mapper;

import com.atguigu.srb.core.pojo.dto.ExcelDictDTO;
import com.atguigu.srb.core.pojo.entity.Dict;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

import java.util.ArrayList;

/**
 * <p>
 * 数据字典 Mapper 接口
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
public interface DictMapper extends BaseMapper<Dict> {

    void insertBatch(ArrayList<ExcelDictDTO> excelDictDTOArrayList);
}
