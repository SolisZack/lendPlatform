package com.atguigu.srb.core.controller.api;


import com.alibaba.fastjson.JSON;
import com.atguigu.common.result.R;
import com.atguigu.srb.base.util.JwtUtils;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.pojo.vo.UserBindVO;
import com.atguigu.srb.core.service.UserBindService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * <p>
 * 用户绑定表 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Api(tags = "UserBind")
@Slf4j
@RestController
@RequestMapping("/api/core/userBind")
public class UserBindController {

    @Resource
    private UserBindService userBindService;

    @ApiOperation("bindUser")
    @PostMapping("/auth/bind")
    public R bind(@RequestBody UserBindVO userBindVO, HttpServletRequest httpServletRequest) {
        // Verify jwt token and get userId from token
        String token = httpServletRequest.getHeader("token");
        Long userId = JwtUtils.getUserId(token);
        // Bind user
        String formStr = userBindService.commitBindUser(userBindVO, userId);
        return R.ok().data("formStr", formStr);

    }

    @ApiOperation("notify")
    @PostMapping("/notify")
    public String notifyBind(HttpServletRequest request) {
        Map<String, Object> paramMap = RequestHelper.switchMap(request.getParameterMap());
        log.info("Get paramMap from hfb:{}", JSON.toJSONString(paramMap));
        if(!RequestHelper.isSignEquals(paramMap)) {
            log.error("Wrong sign");
            return "fail";
        }

        log.info("Success verifying sign, start to bind user");
        userBindService.notifyBind(paramMap);


        return "success";
    }
}

