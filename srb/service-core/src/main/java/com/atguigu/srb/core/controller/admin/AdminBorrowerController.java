package com.atguigu.srb.core.controller.admin;


import com.atguigu.common.result.R;
import com.atguigu.srb.core.pojo.entity.Borrower;
import com.atguigu.srb.core.pojo.vo.BorrowerApprovalVO;
import com.atguigu.srb.core.pojo.vo.BorrowerDetailVO;
import com.atguigu.srb.core.service.BorrowerService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 借款人 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Api(tags = "Borrower")
@RestController
@RequestMapping("/admin/core/borrower")
public class AdminBorrowerController {
    @Resource
    private BorrowerService borrowerService;

    @ApiOperation("listPage")
    @GetMapping("/list/{page}/{limit}")
    public R listPage(@PathVariable Long page,
                      @PathVariable Long limit,
                      @RequestParam String keyword)
    {
        Page<Borrower> pageParams = new Page<>(page, limit);
        IPage<Borrower> pageModel = borrowerService.listPage(pageParams, keyword);
        return R.ok().data("pageModel", pageModel);
    }

    @ApiOperation("show")
    @GetMapping("/show/{id}")
    public R show(@PathVariable Long id) {
        BorrowerDetailVO borrowerDetailVO = borrowerService.getBorrowerDetailVOById(id);
        return R.ok().data("borrowerDetailVO", borrowerDetailVO);
    }

    @ApiOperation("approval")
    @PostMapping("/approval")
    public R approval(@RequestBody BorrowerApprovalVO borrowerApprovalVO) {
        borrowerService.approval(borrowerApprovalVO);
        return R.ok().message("approval done");
    }
}

