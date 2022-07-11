package com.atguigu.srb.core.controller.api;


import com.atguigu.common.result.R;
import com.atguigu.srb.base.util.JwtUtils;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.pojo.entity.LendReturn;
import com.atguigu.srb.core.service.LendReturnService;
import io.swagger.annotations.Api;
import io.swagger.annotations.ApiOperation;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 还款记录表 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Api(tags = "还款记录")
@RestController
@RequestMapping("/api/core/lendReturn")
public class LendReturnController {
    @Resource
    private LendReturnService lendReturnService;

    @ApiOperation("获取还款计划")
    @GetMapping("/list/{lendId}")
    public R listLendReturnByLendId(@PathVariable Long lendId) {
        List<LendReturn> lendReturnList = lendReturnService.selectByLendId(lendId);
        return R.ok().data("list", lendReturnList);
    }

    @ApiOperation("还款")
    @PostMapping("/auth/commitReturn/{lendReturnId}")
    public R commitReturn(@PathVariable Long lendReturnId, HttpServletRequest request) {
        String token = request.getHeader("token");
        Long userId = JwtUtils.getUserId(token);
        String formStr = lendReturnService.commitReturn(lendReturnId, userId);
        return R.ok().data("formStr", formStr);
    }

    @ApiOperation("还款异步回调")
    @PostMapping("/notifyUrl")
    public String notifyUrl(HttpServletRequest request) {
        Map<String, Object> params = RequestHelper.switchMap(request.getParameterMap());
        if (RequestHelper.isSignEquals(params)) {
            if ("0001".equals(params.get("resultCode")))
                return lendReturnService.notifyUrl(params);
            else {
                log.info("还款异步回调失败");
                return "success";
            }
        } else {
            log.info("还款异步回调签名错误");
            return "false";
        }
    }

}

