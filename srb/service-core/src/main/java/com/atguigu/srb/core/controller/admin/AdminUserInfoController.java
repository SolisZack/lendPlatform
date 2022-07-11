package com.atguigu.srb.core.controller.admin;


import com.atguigu.common.result.R;
import com.atguigu.srb.core.pojo.entity.UserInfo;
import com.atguigu.srb.core.pojo.query.UserInfoQuery;
import com.atguigu.srb.core.service.UserInfoService;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 用户基本信息 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Api(tags = "userInfo API")
@RestController
@RequestMapping("/admin/core/userInfo")
@Slf4j
//@CrossOrigin
public class AdminUserInfoController {
    @Resource
    private UserInfoService userInfoService;

    @ApiOperation("list")
    @GetMapping("/list/{page}/{limit}")
    public R listPage (@PathVariable Long page,
                       @PathVariable Long limit,
                       UserInfoQuery userInfoQuery)
    {
        Page<UserInfo> pageParams = new Page<>(page, limit);
        IPage<UserInfo> pageModel = userInfoService.listPage(pageParams, userInfoQuery);
        return R.ok().data("pageModel", pageModel);
    }

    @ApiOperation("lock")
    @PutMapping("/lock/{id}/{status}")
    public R lock(@PathVariable Long id,
                  @PathVariable Integer status)
    {

        userInfoService.lock(id, status);
        return R.ok().message(status == 1 ? "unlock" : "locked");
    }

}

