package com.atguigu.srb.core.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.common.exception.Assert;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.core.enums.LendStatusEnum;
import com.atguigu.srb.core.enums.TransTypeEnum;
import com.atguigu.srb.core.hfb.FormHelper;
import com.atguigu.srb.core.hfb.HfbConst;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.mapper.*;
import com.atguigu.srb.core.pojo.bo.TransFlowBO;
import com.atguigu.srb.core.pojo.entity.Lend;
import com.atguigu.srb.core.pojo.entity.LendItem;
import com.atguigu.srb.core.pojo.entity.LendItemReturn;
import com.atguigu.srb.core.pojo.entity.LendReturn;
import com.atguigu.srb.core.service.*;
import com.atguigu.srb.core.util.LendNoUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 还款记录表 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Service
public class LendReturnServiceImpl extends ServiceImpl<LendReturnMapper, LendReturn> implements LendReturnService {
    @Resource
    private LendReturnMapper lendReturnMapper;

    @Resource
    private UserAccountMapper userAccountMapper;

    @Resource
    private LendMapper lendMapper;

    @Resource
    private LendItemMapper lendItemMapper;

    @Resource
    private LendItemReturnMapper lendItemReturnMapper;

    @Resource
    private TransFlowService transFlowService;

    @Resource
    private UserBindService userBindService;

    @Resource
    private UserAccountService userAccountService;

    @Resource
    private LendItemReturnService lendItemReturnService;

    @Override
    public List<LendReturn> selectByLendId(Long lendId) {
        QueryWrapper<LendReturn> lendReturnQueryWrapper = new QueryWrapper<>();
        lendReturnQueryWrapper.eq("lend_id", lendId);
        return lendReturnMapper.selectList(lendReturnQueryWrapper);

    }

    @Override
    // 根据本月还款计划生成还款表单
    public String commitReturn(Long lendReturnId, Long userId) {
        LendReturn lendReturn = lendReturnMapper.selectById(lendReturnId);
        Lend lend = lendMapper.selectById(lendReturn.getLendId());
        String bindCode = userBindService.getBindCodeByUserId(userId);

        // 校验用户余额
        BigDecimal account = userAccountService.getAccount(userId);
        Assert.isTrue(account.doubleValue() >= lendReturn.getTotal().doubleValue(),
                ResponseEnum.NOT_SUFFICIENT_FUNDS_ERROR);

        // 组装表单参数
        Map<String, Object> params = new HashMap<>();
        params.put("agentId", HfbConst.AGENT_ID);
        params.put("agentBatchNo", lendReturn.getReturnNo());
        params.put("agentGoodsName", lend.getTitle());
        params.put("fromBindCode", bindCode);
        params.put("totalAmt", lendReturn.getTotal());
        params.put("note", "");
        params.put("voteFeeAmt", new BigDecimal(0));
        params.put("notifyUrl", HfbConst.BORROW_RETURN_NOTIFY_URL);
        params.put("returnUrl", HfbConst.BORROW_RETURN_RETURN_URL);
        params.put("timestamp", RequestHelper.getTimestamp());
        // 还款明细 因为本月可能要还给多个投资人
        List<Map<String, Object>> lendItemReturnDetailList = lendItemReturnService.addReturnDetail(lendReturnId);
        params.put("data", JSONObject.toJSONString(lendItemReturnDetailList));
        // 生成签名
        params.put("sign", RequestHelper.getSign(params));
        // 生成表单
        return FormHelper.buildForm(HfbConst.BORROW_RETURN_URL, params);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String notifyUrl(Map<String, Object> params) {
        // 还款编号
        String returnBatchNo = (String) params.get("agentBatchNo");

        // 🌾等性判断
        if (transFlowService.isSaveTransFlow(returnBatchNo))
            return "success";

        // 更新还款状态
        QueryWrapper<LendReturn> lendReturnQueryWrapper = new QueryWrapper<>();
        lendReturnQueryWrapper.eq("return_no", returnBatchNo);
        LendReturn lendReturn = lendReturnMapper.selectOne(lendReturnQueryWrapper);
        lendReturn.setStatus(1);
        lendReturn.setFee(new BigDecimal(0));
        lendReturn.setRealReturnTime(LocalDateTime.now());
        lendReturnMapper.updateById(lendReturn);

        // 更新标的信息
        Lend lend = lendMapper.selectById(lendReturn.getLendId());
        // 如果是最后一次还款, 则更新标的状态
        if (lendReturn.getLast()) {
            lend.setStatus(LendStatusEnum.PAY_OK.getStatus());
            lendMapper.updateById(lend);
        }

        // 还款账号转出金额
        String borrowerBindCode = userBindService.getBindCodeByUserId(lend.getUserId());
        userAccountMapper.updateUserAccount(
                borrowerBindCode,
                new BigDecimal((String) params.get("totalAmt")).negate(),
                new BigDecimal(0));

        // 还款流水
        TransFlowBO transFlowBO = new TransFlowBO(
                returnBatchNo,
                borrowerBindCode,
                new BigDecimal((String) params.get("totalAmt")),
                TransTypeEnum.RETURN_DOWN,
                "用户还款"
        );
        transFlowService.saveTransFlow(transFlowBO);

        // 回款（投资收益）明细获取
        List<LendItemReturn> lendItemReturns = lendItemReturnService.selectLendItemReturnList(lendReturn.getId());
        lendItemReturns.forEach(lendItemReturn -> {
            // 更新投资收益状态
            lendItemReturn.setStatus(1);
            lendItemReturn.setRealReturnTime(LocalDateTime.now());
            lendItemReturnMapper.updateById(lendItemReturn);

            // 投资账号转入金额
            String investBindCode = userBindService.getBindCodeByUserId(lendItemReturn.getInvestUserId());
            userAccountMapper.updateUserAccount(
                    investBindCode,
                    lendItemReturn.getTotal(),
                    new BigDecimal(0)
            );

            // 更新投资信息中的实际收益和状态
            LendItem lendItem = lendItemMapper.selectById(lendItemReturn.getLendItemId());
            lendItem.setRealAmount(lendItem.getRealAmount().add(lendItemReturn.getInterest()));
            if (lendReturn.getLast())
                lendItem.setStatus(2);
            lendItemMapper.updateById(lendItem);

            // 投资收益流水
            TransFlowBO investTransFlowBO = new TransFlowBO(
                    LendNoUtils.getReturnItemNo(),
                    investBindCode,
                    lendItemReturn.getTotal(),
                    TransTypeEnum.INVEST_BACK,
                    "投资人获得投资收益"
            );
            transFlowService.saveTransFlow(investTransFlowBO);

        });
        return "success";
    }
}
