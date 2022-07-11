package com.atguigu.srb.core.service.impl;

import com.atguigu.common.exception.Assert;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.base.dto.SmsDTO;
import com.atguigu.srb.core.enums.TransTypeEnum;
import com.atguigu.srb.core.hfb.FormHelper;
import com.atguigu.srb.core.hfb.HfbConst;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.mapper.UserAccountMapper;
import com.atguigu.srb.core.mapper.UserInfoMapper;
import com.atguigu.srb.core.pojo.bo.TransFlowBO;
import com.atguigu.srb.core.pojo.entity.UserAccount;
import com.atguigu.srb.core.service.TransFlowService;
import com.atguigu.srb.core.service.UserAccountService;
import com.atguigu.srb.core.service.UserBindService;
import com.atguigu.srb.core.service.UserInfoService;
import com.atguigu.srb.core.util.LendNoUtils;
import com.atguigu.srb.rabbitutil.constant.MQConst;
import com.atguigu.srb.rabbitutil.service.MQService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 用户账户 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Service
public class UserAccountServiceImpl extends ServiceImpl<UserAccountMapper, UserAccount> implements UserAccountService {

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private UserAccountMapper userAccountMapper;

    @Resource
    private MQService mqService;

    @Resource
    private UserBindService userBindService;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private TransFlowService transFlowService;

    @Override
    public String commitCharge(BigDecimal chargeAmount, Long userId) {
        Map<String, Object> params = new HashMap<>();

        params.put("agentId", HfbConst.AGENT_ID);
        params.put("returnUrl", HfbConst.RECHARGE_RETURN_URL);
        params.put("notifyUrl", HfbConst.RECHARGE_NOTIFY_URL);

        params.put("agentBillNo", LendNoUtils.getChargeNo());
        params.put("bindCode", userInfoMapper.selectById(userId).getBindCode());
        params.put("chargeAmt", chargeAmount);
        params.put("feeAmt", new BigDecimal(0));
        params.put("timestamp", RequestHelper.getTimestamp());
        params.put("sign", RequestHelper.getSign(params));

        return FormHelper.buildForm(HfbConst.RECHARGE_URL, params);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String notify(Map<String, Object> paramMap) {
        // 幂等性判断
        boolean transFlowExist = transFlowService.isSaveTransFlow((String) paramMap.get("agentBillNo"));
        if (transFlowExist) {
            log.warn("TransFlow Number Already Exist");
            return "success";
        }

        // 账户余额处理
        userAccountMapper.updateUserAccount(
                paramMap.get("bindCode").toString(),
                new BigDecimal(paramMap.get("chargeAmt").toString()),
                new BigDecimal(0));

        // 记录流水
        TransFlowBO transFlowBO = new TransFlowBO(
                (String) paramMap.get("agentBillNo"),
                (String) paramMap.get("bindCode"),
                new BigDecimal((String) paramMap.get("chargeAmt")),
                TransTypeEnum.RECHARGE,
                "充值"
        );
        transFlowService.saveTransFlow(transFlowBO);

        // 幂等性测试
//        return "false";

        // 发送充值成功短信给用户
        String mobile = userInfoService.getMobileByBindCode((String) paramMap.get("bindCode"));
        SmsDTO smsDTO = new SmsDTO();
        smsDTO.setMobile(mobile);
        smsDTO.setMessage("0001");
        mqService.sendMessage(MQConst.EXCHANGE_TOPIC_SMS, MQConst.ROUTING_SMS_ITEM, smsDTO);

        return "success";
    }

    @Override
    public BigDecimal getAccount(Long userId) {
        QueryWrapper<UserAccount> userAccountQueryWrapper = new QueryWrapper<>();
        userAccountQueryWrapper.eq("user_id", userId);
        UserAccount userAccount = userAccountMapper.selectOne(userAccountQueryWrapper);
        return userAccount.getAmount();

    }

    @Override
    public String commitWithdraw(BigDecimal fetchAmt, Long userId) {
        // 获取账户余额
        BigDecimal account = this.getAccount(userId);
        // 检查提现金额是否小于等于账户余额
        Assert.isTrue(account.doubleValue() >= fetchAmt.doubleValue(), ResponseEnum.NOT_SUFFICIENT_FUNDS_ERROR);

        // 组装表单参数
        Map<String, Object> params = new HashMap<>();
        String bindCode = userBindService.getBindCodeByUserId(userId);
        params.put("agentId", HfbConst.AGENT_ID);
        params.put("agentBillNo", LendNoUtils.getWithdrawNo());
        params.put("bindCode", bindCode);
        params.put("fetchAmt", fetchAmt);
        params.put("notifyUrl", HfbConst.WITHDRAW_NOTIFY_URL);
        params.put("returnUrl", HfbConst.WITHDRAW_RETURN_URL);
        params.put("timestamp", RequestHelper.getTimestamp());
        params.put("sign", RequestHelper.getSign(params));

        // 构建表单
        return FormHelper.buildForm(HfbConst.WITHDRAW_URL, params);

    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String notifyWithdraw(Map<String, Object> params) {
        // 获取提现单号
        String agentBillNo = (String) params.get("agentBillNo");

        // 幂等判断
        boolean transFlowExist = transFlowService.isSaveTransFlow(agentBillNo);
        if (transFlowExist)
            return "success";

        // 账户金额同步
        userAccountMapper.updateUserAccount(
                (String) params.get("bindCode"),
                new BigDecimal((String)params.get("fetchAmt")).negate(),
                new BigDecimal(0)
        );

        // 创建交易流水
        TransFlowBO transFlowBO = new TransFlowBO(
                agentBillNo,
                (String) params.get("bindCode"),
                new BigDecimal((String)params.get("fetchAmt")),
                TransTypeEnum.WITHDRAW,
                "用户提现"
        );
        transFlowService.saveTransFlow(transFlowBO);
        return "success";
    }
}
