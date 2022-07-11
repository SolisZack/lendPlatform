package com.atguigu.srb.core.controller.admin;


import com.atguigu.common.exception.Assert;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.result.R;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.core.pojo.entity.IntegralGrade;
import com.atguigu.srb.core.service.IntegralGradeService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 积分等级表 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
//@CrossOrigin
@RestController
@RequestMapping("/admin/core/integralGrade")
@Api(tags = "integralGrade API")
@Slf4j
public class AdminIntegralGradeController {

    @Resource
    private IntegralGradeService integralGradeService;

    @ApiOperation("积分等级list")
    @GetMapping("/list")
    public R listAll() {
        List<IntegralGrade> integralGradeList = integralGradeService.list();
        return R.ok().data("list", integralGradeList);
    }

    @ApiOperation("积分等级delete")
    @DeleteMapping("/remove/{id}")
    public R removeById(@PathVariable Long id) {
        boolean success = integralGradeService.removeById(id);
        if(success)
            return R.ok();
        else
            return R.error().message("remove IntegralGrade by id failed");
    }

    @ApiOperation("add积分等级")
    @PostMapping("/save")
    public R save(@RequestBody IntegralGrade integralGrade) {
        if(integralGrade.getBorrowAmount() == null)
            throw new BusinessException(ResponseEnum.BORROW_AMOUNT_NULL_ERROR);
        boolean success = integralGradeService.save(integralGrade);
        if(success)
            return R.ok();
        else
            return R.error().message("save IntegralGrade failed");
    }

    @ApiOperation("积分等级get")
    @GetMapping("/get/{id}")
    public R getById(@PathVariable Long id) {
        IntegralGrade integralGrade = integralGradeService.getById(id);
        Assert.notNull(integralGrade, ResponseEnum.LOGIN_MOBILE_ERROR);
        return R.ok().data("record", integralGrade);
    }

    @ApiOperation("积分等级update")
    @PutMapping("/update")
    public R update(@RequestBody IntegralGrade integralGrade) {
        boolean success = integralGradeService.updateById(integralGrade);
        if(success)
            return R.ok();
        else
            return R.error().message("update IntegralGrade by id failed");
    }


}

