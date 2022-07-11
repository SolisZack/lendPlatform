package com.atguigu.srb.core.listener;

import com.alibaba.excel.context.AnalysisContext;
import com.alibaba.excel.event.AnalysisEventListener;
import com.atguigu.srb.core.mapper.DictMapper;
import com.atguigu.srb.core.pojo.dto.ExcelDictDTO;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;

@Slf4j
@NoArgsConstructor
public class ExcelDictDTOListener extends AnalysisEventListener<ExcelDictDTO> {

    // params for DTO save
    private ArrayList<ExcelDictDTO> excelDictDTOArrayList = new ArrayList<>();
    private static final int MAX_SAVE_BATCH = 5;

    // mapper
    private DictMapper dictMapper;

    public ExcelDictDTOListener(DictMapper dictMapper) {
        this.dictMapper = dictMapper;
    }

    @Override
    public void invoke(ExcelDictDTO excelDictDTO, AnalysisContext analysisContext) {
        log.info("Analyzing one context {}", excelDictDTO);
        excelDictDTOArrayList.add(excelDictDTO);
        if(excelDictDTOArrayList.size() >= MAX_SAVE_BATCH) {
            saveDTO();
            excelDictDTOArrayList.clear();
        }
    }

    @Override
    public void doAfterAllAnalysed(AnalysisContext analysisContext) {
        // save last data in list
        saveDTO();
        excelDictDTOArrayList.clear();
        log.info("Analyze excel complete");

    }

    public void saveDTO() {
        log.info("saving {} data to mysql", excelDictDTOArrayList.size());
        dictMapper.insertBatch(excelDictDTOArrayList);
        log.info("saved {} data to mysql", excelDictDTOArrayList.size());
    }
}
