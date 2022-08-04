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
import com.atguigu.srb.core.util.RedisUtils;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

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

    @Resource
    RedisTemplate<String, Object> redisTemplate;

    @Resource
    RedisUtils redisUtils;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String commitInvest(InvestVO investVO) {
        String lendItemNo = LendNoUtils.getLendItemNo();
        log.info("生成投资单号:{}", lendItemNo);

        // 基于clientId加锁
        String clientId = UUID.randomUUID().toString();

        // 获取标的信息
        Lend lend = (Lend) redisTemplate.opsForValue().get("srb:core:lend:" + investVO.getLendId());
        if (lend == null) {
            lend = lendMapper.selectById(investVO.getLendId());
            boolean lendSetSuccess = redisUtils.threadSafeSetValue(clientId,
                    "srb:core:lend:" + investVO.getLendId(), lend,
                    1, TimeUnit.DAYS, "lend");
            if (!lendSetSuccess)
                throw new BusinessException("无法将标的投资信息添加至redis");
        }

        // 获取标的已投资金额
        String lendNo = lend.getLendNo();
        BigDecimal investedNum = (BigDecimal) redisTemplate.opsForValue().get("srb:core:lendInvest:" + lendNo);
        // 缓存到redis里
        if (investedNum == null) {
            investedNum = lend.getInvestAmount();
            boolean lendInvestSuccess = redisUtils.threadSafeSetValue(clientId,
                    "srb:core:lendInvest:" + lendNo, investedNum,
                    1, TimeUnit.DAYS, "lendInvest");
            // 如果无法缓存抛出异常
            if (!lendInvestSuccess)
                throw new BusinessException("无法将标的投资金额添加至redis");
        }

        // 检查标的状态
        Assert.isTrue(
                lend.getStatus().intValue() == LendStatusEnum.INVEST_RUN.getStatus().intValue(),
                ResponseEnum.LEND_INVEST_ERROR
        );
        // 检查是否超卖
        BigDecimal investSum = investedNum.add(new BigDecimal(investVO.getInvestAmount()));
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

        // redis中已投资金额增加
        log.info("Before:redis中已投资金额增加");
        boolean lendInvestIncSuccess = redisUtils.threadSafeAddNum(clientId,
                "srb:core:lendInvest:" + lendNo, new BigDecimal(investVO.getInvestAmount()), lend.getAmount(),
                1, TimeUnit.DAYS, "lendInvest");
        // 如果无法缓存抛出异常
        if (!lendInvestIncSuccess)
            throw new BusinessException("无法在redis中修改标的已投资金额");
        log.info("After:redis中已投资金额增加");

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
//        return "";
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
    @Transactional(rollbackFor = Exception.class)
    public String failureNotify(Map<String, Object> paramMap) {
        //通过val,给锁设置唯一id，防止其他线程删除锁
        String clientId = UUID.randomUUID().toString();  //或者雪花生成位置ID

        String agentBillNo = (String) paramMap.get("agentBillNo");
        String lendNo = (String) paramMap.get("agentProjectCode");
        String voteAmt = (String) paramMap.get("voteAmt");
        String expectAmount = (String) paramMap.get("projectAmt");

        // 幂等性返回
        Object notified = redisTemplate.opsForValue().get("srb:core:lendItemFailureNotify:" + agentBillNo);
        if (notified != null)
            return "success";

        // 抢锁
        boolean getLock = redisUtils.getLock(clientId, "lendItemFailureNotifyLock");
        if (!getLock)
            return "false";

        // 基于分布式锁回滚标的已投资金额
        boolean opsSuccess  = redisUtils.threadSafeAddNum(clientId,
                "srb:core:lendInvest:" + lendNo, new BigDecimal("-" + voteAmt), new BigDecimal(expectAmount),
                1, TimeUnit.HOURS, "lendInvestLock");

        // 拿不到锁则说明存在问题
        if (!opsSuccess) {
            log.error("向Redis中减少标的已投资金额失败");
            return "false";
        }

        // 防止重复卖, 幂等
        redisUtils.threadSafeSetValue(clientId,
                "srb:core:lendItemFailureNotify:" + agentBillNo, true,
                1, TimeUnit.DAYS, "lendItemFailureNotify");

        // 释放锁
        redisUtils.releaseLock("lendItemFailureNotifyLock", clientId);

        // 删除投资记录lendItem
        QueryWrapper<LendItem> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq("lend_item_no", agentBillNo);
        lendItemMapper.delete(queryWrapper);

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
