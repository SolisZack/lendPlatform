package com.atguigu.srb.core.controller.api;


import com.atguigu.common.exception.Assert;
import com.atguigu.common.result.R;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.common.util.RegexValidateUtils;
import com.atguigu.srb.base.util.JwtUtils;
import com.atguigu.srb.core.pojo.vo.LoginVO;
import com.atguigu.srb.core.pojo.vo.RegisterVO;
import com.atguigu.srb.core.pojo.vo.UserIndexVO;
import com.atguigu.srb.core.pojo.vo.UserInfoVO;
import com.atguigu.srb.core.service.UserInfoService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

/**
 * <p>
 * 用户基本信息 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Api(tags = "user api")
@RestController
@RequestMapping("/api/core/userInfo")
@Slf4j
//@CrossOrigin
public class UserInfoController {

    @Resource
    private RedisTemplate redisTemplate;

    @Resource
    private UserInfoService userInfoService;

    @ApiOperation("register")
    @PostMapping("/register")
    public R register(@RequestBody RegisterVO registerVO) {
        // Check if the sms code is correct
        String code = (String)redisTemplate.opsForValue().get("srb:sms:code:" + registerVO.getMobile());
        // Password not empty
        Assert.notEmpty(registerVO.getPassword(), ResponseEnum.PASSWORD_NULL_ERROR);
        // Code not empty
        Assert.notEmpty(registerVO.getCode(), ResponseEnum.CODE_NULL_ERROR);
        // Mobile phone not empty
        Assert.notEmpty(registerVO.getMobile(), ResponseEnum.MOBILE_NULL_ERROR);
        // Mobile phone valid
        Assert.isTrue(RegexValidateUtils.checkCellphone(registerVO.getMobile()), ResponseEnum.MOBILE_ERROR);
        // Code correct
        Assert.equals(code, registerVO.getCode(), ResponseEnum.CODE_ERROR);
        // Register
        userInfoService.register(registerVO);
        return R.ok();
    }

    @ApiOperation("login")
    @PostMapping("/login")
    public R login(@RequestBody LoginVO loginVO, HttpServletRequest request) {
        // Not empty
        Assert.notEmpty(loginVO.getMobile(), ResponseEnum.MOBILE_NULL_ERROR);
        Assert.notEmpty(loginVO.getPassword(), ResponseEnum.PASSWORD_NULL_ERROR);
        // login
        UserInfoVO userInfoVO = userInfoService.login(loginVO, request.getRemoteAddr());

        return R.ok().data("userInfo", userInfoVO);
    }

    @ApiOperation("checkToken")
    @GetMapping("/checkToken")
    public R checkToken(HttpServletRequest httpServletRequest) {
        String token = httpServletRequest.getHeader("token");
        Assert.isTrue(JwtUtils.checkToken(token), ResponseEnum.LOGIN_AUTH_ERROR);
        return R.ok();
    }


    @ApiOperation("checkMobile")
    @GetMapping("/checkMobile/{mobile}")
    public R checkMobile(@PathVariable String mobile) {
        boolean result = userInfoService.checkMobile(mobile);
        return R.ok().data("isExist", result);
    }

    @ApiOperation("getUserInfo")
    @GetMapping("/auth/getIndexUserInfo")
    public R getIndexUserInfo(HttpServletRequest request) {
        String token = request.getHeader("token");
        Long userId = JwtUtils.getUserId(token);
        UserIndexVO userIndexVO = userInfoService.getIndexUserInfo(userId);
        return R.ok().data("userIndexVO", userIndexVO);
    }

}

