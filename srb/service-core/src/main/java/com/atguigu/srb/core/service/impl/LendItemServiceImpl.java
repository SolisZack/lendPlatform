package com.atguigu.srb.core.service.impl;

import com.atguigu.common.exception.Assert;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.core.enums.LendStatusEnum;
import com.atguigu.srb.core.enums.TransTypeEnum;
import com.atguigu.srb.core.hfb.FormHelper;
import com.atguigu.srb.core.hfb.HfbConst;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.mapper.LendItemMapper;
import com.atguigu.srb.core.mapper.LendMapper;
import com.atguigu.srb.core.mapper.UserAccountMapper;
import com.atguigu.srb.core.pojo.bo.TransFlowBO;
import com.atguigu.srb.core.pojo.entity.Lend;
import com.atguigu.srb.core.pojo.entity.LendItem;
import com.atguigu.srb.core.pojo.vo.InvestVO;
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
 * 标的出借记录表 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Service
public class LendItemServiceImpl extends ServiceImpl<LendItemMapper, LendItem> implements LendItemService {

    @Resource
    private LendMapper lendMapper;

    @Resource
    private UserAccountMapper userAccountMapper;

    @Resource
    private LendItemMapper lendItemMapper;

    @Resource
    private LendService lendService;

    @Resource
    private UserBindService userBindService;

    @Resource
    private TransFlowService transFlowService;

    @Resource
    private UserAccountService userAccountService;


    @Override
    @Transactional(rollbackFor = Exception.class)
    public String commitInvest(InvestVO investVO) {
        String lendItemNo = LendNoUtils.getLendItemNo();
        log.info("生成投资单号:{}", lendItemNo);
        Lend lend = lendMapper.selectById(investVO.getLendId());

        // 检查标的状态
        Assert.isTrue(
                lend.getStatus().intValue() == LendStatusEnum.INVEST_RUN.getStatus().intValue(),
                ResponseEnum.LEND_INVEST_ERROR
        );
        // 检查是否超卖
        BigDecimal investSum = lend.getInvestAmount().add(new BigDecimal(investVO.getInvestAmount()));
        Assert.isTrue(
                investSum.doubleValue() <= lend.getAmount().doubleValue(),
                ResponseEnum.LEND_FULL_SCALE_ERROR
        );
        // 检查投资人余额
        BigDecimal investerAccount = userAccountService.getAccount(investVO.getInvestUserId());
        Assert.isTrue(
                investerAccount.doubleValue() >= Double.parseDouble(investVO.getInvestAmount()),
                ResponseEnum.LEND_FULL_SCALE_ERROR
        );

        // 组装表单
        String investBindeCode = userBindService.getBindCodeByUserId(investVO.getInvestUserId());
        String benefitBindCode = userBindService.getBindCodeByUserId(lend.getUserId());
        Map<String, Object> param = new HashMap<>();
        param.put("agentId", HfbConst.AGENT_ID);
        param.put("voteBindCode", investBindeCode);
        param.put("benefitBindCode", benefitBindCode);
        param.put("agentProjectCode", lend.getLendNo());
        param.put("agentProjectName", lend.getTitle());
        param.put("agentBillNo", lendItemNo);
        param.put("voteAmt", investVO.getInvestAmount());
        param.put("votePrizeAmt", 0);
        param.put("voteFeeAmt", 0);
        param.put("projectAmt", lend.getAmount());
        param.put("note", "testMotherFucker");
        param.put("notifyUrl", HfbConst.INVEST_NOTIFY_URL);
        param.put("returnUrl", HfbConst.INVEST_RETURN_URL);
        param.put("timestamp", RequestHelper.getTimestamp());
        param.put("sign", RequestHelper.getSign(param));

        // 生成投资记录
        LendItem lendItem = new LendItem();
        lendItem.setLendId(investVO.getLendId());
        lendItem.setInvestUserId(investVO.getInvestUserId());
        lendItem.setInvestName(investVO.getInvestName());
        lendItem.setLendItemNo(lendItemNo);
        lendItem.setInvestAmount(new BigDecimal(investVO.getInvestAmount()));
        lendItem.setLendYearRate(lend.getLendYearRate());
        lendItem.setInvestTime(LocalDateTime.now());
        lendItem.setLendStartDate(lend.getLendStartDate());
        lendItem.setLendEndDate(lend.getLendEndDate());
        // 计算预期收益
        BigDecimal expectAmount = lendService.getInterestCount(
                lendItem.getInvestAmount(),
                lendItem.getLendYearRate(),
                lend.getPeriod(),
                lend.getReturnMethod()
                );
        lendItem.setExpectAmount(expectAmount);
        // 实际收益
        lendItem.setRealAmount(new BigDecimal(0));
        // 出借状态
        lendItem.setStatus(0);
        // 插入数据库
        lendItemMapper.insert(lendItem);

        return FormHelper.buildForm(HfbConst.INVEST_URL, param);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String notify(Map<String, Object> paramMap) {
        // 幂等性返回
        String agentBillNo = (String) paramMap.get("agentBillNo");
        log.info("获取投资单号agentBillNo:{}", agentBillNo);
        boolean transFlowAlreadyExist = transFlowService.isSaveTransFlow(agentBillNo);
        if (transFlowAlreadyExist)
            return "false";

        // 修改账户金额：余额减少，冻结金额增加
        String voteBindCode = (String) paramMap.get("voteBindCode");
        String voteAmt = (String) paramMap.get("voteAmt");;
        userAccountMapper.updateUserAccount(
                voteBindCode,
                new BigDecimal("-" + voteAmt),
                new BigDecimal(voteAmt)
        );

        // 修改标的出借状态
        LendItem lendItem = this.getByLendItemNo(agentBillNo);
        if (lendItem == null)
            throw new BusinessException("找不到投资记录");
        lendItem.setStatus(1);
        lendItemMapper.updateById(lendItem);

        // 修改标的已投资金额、投资人数
        Lend lend = lendMapper.selectById(lendItem.getLendId());
        lend.setInvestAmount(lendItem.getInvestAmount().add(lend.getInvestAmount()));
        lend.setInvestNum(lend.getInvestNum() + 1);
        lendMapper.updateById(lend);

        // 新增交易流水
        TransFlowBO transFlowBO = new TransFlowBO(
                agentBillNo,
                voteBindCode,
                new BigDecimal(voteAmt),
                TransTypeEnum.INVEST_LOCK,
                "项目编号: " + lend.getLendNo() + " 项目名称：" + lend.getTitle()
        );
        transFlowService.saveTransFlow(transFlowBO);

        return "success";

    }

    @Override
    public List<LendItem> selectByLendId(Long lendId) {
        QueryWrapper<LendItem> lendItemQueryWrapper = new QueryWrapper<>();
        lendItemQueryWrapper.eq("lend_id", lendId).eq("status", 1);
        return lendItemMapper.selectList(lendItemQueryWrapper);
    }

    @Override
    public List<LendItem> selectAllByLendId(Long lendId) {
        QueryWrapper<LendItem> lendItemQueryWrapper = new QueryWrapper<>();
        lendItemQueryWrapper.eq("lend_id", lendId);
        return lendItemMapper.selectList(lendItemQueryWrapper);
    }

    private LendItem getByLendItemNo(String lendItemNo) {
        QueryWrapper<LendItem> lendItemQueryWrapper = new QueryWrapper<>();
        lendItemQueryWrapper.eq("lend_item_no", lendItemNo);
        return lendItemMapper.selectOne(lendItemQueryWrapper);
    }
}
