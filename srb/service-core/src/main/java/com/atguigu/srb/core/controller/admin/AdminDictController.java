package com.atguigu.srb.core.controller.admin;


import com.alibaba.excel.EasyExcel;
import com.atguigu.common.exception.Assert;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.result.R;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.core.pojo.dto.ExcelDictDTO;
import com.atguigu.srb.core.pojo.entity.Dict;
import com.atguigu.srb.core.service.DictService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.util.List;

/**
 * <p>
 * 数据字典 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Api(tags = "dict API")
//@CrossOrigin
@RestController
@RequestMapping("/admin/core/dict")
public class AdminDictController {

    @Resource
    private DictService dictService;

    @ApiOperation("Excel import in batch")
    @PostMapping("/import")
    public R batchImport(@RequestParam("file") MultipartFile file)  {
        try {
            InputStream inputStream = file.getInputStream();
            dictService.importData(inputStream);
            return R.ok();
        } catch (Exception e) {
            throw new BusinessException(ResponseEnum.UPLOAD_ERROR, e);
        }
    }

    @ApiOperation("Excel data list by parentId")
    @GetMapping("/listByParentId/{parentId}")
    public R listByParentId(@PathVariable Long parentId) {
        List<Dict> dictArrayList = dictService.listByParentId(parentId);
        Assert.notNull(dictArrayList, ResponseEnum.BAD_SQL_GRAMMAR_ERROR);
        return R.ok().data("list", dictArrayList);
    }

    @ApiOperation("Excel data export") // don't use in swagger, cause bug
    @GetMapping("/export")
    public R dictExport(HttpServletResponse response) throws IOException {
        // set content type for excel
        response.setContentType("application/vnd.ms-excel");
        response.setCharacterEncoding("utf-8");
        // set execel downloadable
        String fileName = URLEncoder.encode("dict", "utf-8").replaceAll("\\+", "%20");
        response.setHeader("Content-disposition", "attachment;filename*=utf-8''" + fileName + ".xlsx");
        // write excel
        EasyExcel.write(response.getOutputStream(), ExcelDictDTO.class).sheet("sheet1").doWrite(dictService.listDictData());
        return R.ok();
    }
}

