package com.atguigu.srb.core.controller.admin;


import com.atguigu.common.result.R;
import com.atguigu.srb.core.pojo.entity.Lend;
import com.atguigu.srb.core.service.LendService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 标的准备表 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Api(tags = "标的管理")
@RestController
@RequestMapping("/admin/core/lend")
public class AdminLendController {
    @Resource
    private LendService lendService;

    @ApiOperation("list")
    @GetMapping("/list")
    public R list() {
        List<Lend> lendList = lendService.selectList();
        return R.ok().data("list", lendList);
    }

    @ApiOperation("showDetailedLend")
    @GetMapping("/show/{id}")
    public R show(@PathVariable Long id) {
        Map<String, Object> result = lendService.getLendDetail(id);
        return R.ok().data("lendDetail", result);
    }

    @ApiOperation("makeLoan")
    @GetMapping("/makeLoan/{id}")
    public R makeLoan(@PathVariable Long id) {
        lendService.makeLoan(id);
        return R.ok().message("make loan success");
    }
}

