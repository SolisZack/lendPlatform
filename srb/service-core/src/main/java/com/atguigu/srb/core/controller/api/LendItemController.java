package com.atguigu.srb.core.controller.api;


import com.alibaba.fastjson.JSON;
import com.atguigu.common.result.R;
import com.atguigu.srb.base.util.JwtUtils;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.pojo.entity.LendItem;
import com.atguigu.srb.core.pojo.vo.InvestVO;
import com.atguigu.srb.core.service.LendItemService;
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
 * 标的出借记录表 前端控制器
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Api(tags = "标的出借")
@RestController
@RequestMapping("/api/core/lendItem")
public class LendItemController {

    @Resource
    private LendItemService lendItemService;

    @ApiOperation("会员投资提交数据")
    @PostMapping("/auth/commitInvest")
    public R commitInvest(@RequestBody InvestVO investVO, HttpServletRequest request) {
        String token = request.getHeader("token");
        Long userId = JwtUtils.getUserId(token);
        String userName = JwtUtils.getUserName(token);
        investVO.setInvestUserId(userId);
        investVO.setInvestName(userName);

        String formStr = lendItemService.commitInvest(investVO);
        return R.ok().data("formStr", formStr);
    }

    @ApiOperation("会员投资回调")
    @PostMapping("/notify")
    public String notify(HttpServletRequest request) {
        Map<String, Object> paramMap = RequestHelper.switchMap(request.getParameterMap());
        log.info("会员投资回调结果:{}", JSON.toJSONString(paramMap));

        if (paramMap.get("sign").equals(RequestHelper.getSign(paramMap))) {
            if ("0001".equals(paramMap.get("resultCode")))
                return lendItemService.notify(paramMap);
            else {
                log.info("会员投资交易失败");
                return lendItemService.failureNotify(paramMap);
            }
        }else {
            log.info("会员投资回调回调签名错误");
            return "false";
        }
    }

    @ApiOperation("展示投资记录")
    @GetMapping("/list/{lendId}")
    public R listLendItemsByLendId(@PathVariable Long lendId) {
        List<LendItem> lendItems = lendItemService.selectAllByLendId(lendId);
        return R.ok().data("list", lendItems);
    }

}

