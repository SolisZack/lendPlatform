package com.atguigu.srb.core.service.impl;

import com.alibaba.fastjson.JSONObject;
import com.atguigu.common.exception.BusinessException;
import com.atguigu.srb.core.enums.LendStatusEnum;
import com.atguigu.srb.core.enums.ReturnMethodEnum;
import com.atguigu.srb.core.enums.TransTypeEnum;
import com.atguigu.srb.core.hfb.HfbConst;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.mapper.BorrowerMapper;
import com.atguigu.srb.core.mapper.LendMapper;
import com.atguigu.srb.core.mapper.UserAccountMapper;
import com.atguigu.srb.core.mapper.UserInfoMapper;
import com.atguigu.srb.core.pojo.bo.TransFlowBO;
import com.atguigu.srb.core.pojo.entity.*;
import com.atguigu.srb.core.pojo.vo.BorrowInfoApprovalVO;
import com.atguigu.srb.core.pojo.vo.BorrowerDetailVO;
import com.atguigu.srb.core.service.*;
import com.atguigu.srb.core.util.*;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * <p>
 * 标的准备表 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Service
public class LendServiceImpl extends ServiceImpl<LendMapper, Lend> implements LendService {

    @Resource
    private LendMapper lendMapper;

    @Resource
    private BorrowerMapper borrowerMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private UserAccountMapper userAccountMapper;

    @Resource
    private DictService dictService;

    @Resource
    private BorrowerService borrowerService;

    @Resource
    private LendReturnService lendReturnService;

    @Resource
    private LendItemReturnService lendItemReturnService;

    @Resource
    private TransFlowService transFlowService;

    @Resource
    private LendItemService lendItemService;

    @Override
    public void createLend(BorrowInfoApprovalVO borrowInfoApprovalVO, BorrowInfo borrowInfo) {
        Lend lend = new Lend();
        lend.setUserId(borrowInfo.getUserId());
        lend.setBorrowInfoId(borrowInfo.getId());
        lend.setLendNo(LendNoUtils.getLendNo());
        lend.setTitle(borrowInfoApprovalVO.getTitle());
        lend.setAmount(borrowInfo.getAmount());
        lend.setPeriod(borrowInfo.getPeriod());
        lend.setLendYearRate(borrowInfoApprovalVO.getLendYearRate().divide(new BigDecimal(100)));
        lend.setServiceRate(borrowInfoApprovalVO.getServiceRate().divide(new BigDecimal(100)));
        lend.setReturnMethod(borrowInfo.getReturnMethod());
        lend.setLowestAmount(new BigDecimal(100));
        lend.setInvestAmount(new BigDecimal(0));
        lend.setInvestNum(0);
        lend.setPublishDate(LocalDateTime.now());
        lend.setLendInfo(borrowInfoApprovalVO.getLendInfo());
        lend.setRealAmount(new BigDecimal(0));
        lend.setStatus(LendStatusEnum.INVEST_RUN.getStatus());
        lend.setCheckTime(LocalDateTime.now());
        lend.setCheckAdminId(1L);

        // set lendStartDate
        String lendStartDateStr = borrowInfoApprovalVO.getLendStartDate();
        LocalDate lendStartDate = LocalDate.parse(lendStartDateStr, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        lend.setLendStartDate(lendStartDate);
        // set lendEndDate
        LocalDate lendEndDate = lendStartDate.plusMonths(borrowInfo.getPeriod());
        lend.setLendEndDate(lendEndDate);
        // set exxpectAmount
        BigDecimal serviceMonthRate = lend.getServiceRate().divide(new BigDecimal(12), 8, BigDecimal.ROUND_DOWN);
        BigDecimal expectServiceAmount = lend.getAmount().multiply(serviceMonthRate.multiply(new BigDecimal(lend.getPeriod())));
        lend.setExpectAmount(expectServiceAmount);

        // Insert into table
        lendMapper.insert(lend);

    }

    @Override
    public List<Lend> selectList() {
        List<Lend> lendList = lendMapper.selectList(null);
        lendList.forEach(lend -> {
            lend.getParam().put("returnMethod", dictService.getNameByParentDictCodeAndValue(
                    "returnMethod", lend.getReturnMethod()));
            lend.getParam().put("status", LendStatusEnum.getMsgByStatus(lend.getStatus())) ;
        });

        return lendList;

    }

    @Override
    public Map<String, Object> getLendDetail(Long id) {
        Map<String, Object> result = new HashMap<>();
        Lend lend = lendMapper.selectById(id);

        // Get lend info
        lend.getParam().put("returnMethod", dictService.getNameByParentDictCodeAndValue(
                "returnMethod", lend.getReturnMethod()));
        lend.getParam().put("status", LendStatusEnum.getMsgByStatus(lend.getStatus())) ;
        result.put("lend", lend);

        // Get borrower detail info
        QueryWrapper<Borrower> borrowerQueryWrapper = new QueryWrapper<>();
        borrowerQueryWrapper.eq("user_id", lend.getUserId());
        Borrower borrower = borrowerMapper.selectOne(borrowerQueryWrapper);
        BorrowerDetailVO borrowerDetailVO = borrowerService.getBorrowerDetailVOById(borrower.getId());
        result.put("borrower", borrowerDetailVO);

        return result;
    }

    @Override
    public BigDecimal getInterestCount(BigDecimal invest, BigDecimal yearRate, Integer totalmonth, Integer returnMethod) {
        BigDecimal interestCount = new BigDecimal(0);
        if (returnMethod.intValue() == ReturnMethodEnum.ONE.getMethod().intValue()) {
            interestCount = Amount1Helper.getInterestCount(invest, yearRate, totalmonth);
        }else if (returnMethod.intValue() == ReturnMethodEnum.TWO.getMethod().intValue()) {
            interestCount = Amount2Helper.getInterestCount(invest, yearRate, totalmonth);
        }else if (returnMethod.intValue() == ReturnMethodEnum.THREE.getMethod().intValue()) {
            interestCount = Amount3Helper.getInterestCount(invest, yearRate, totalmonth);
        } else if (returnMethod.intValue() == ReturnMethodEnum.FOUR.getMethod().intValue()) {
            interestCount = Amount4Helper.getInterestCount(invest, yearRate, totalmonth);
        }
        return interestCount;
     }

    @Override
    public void makeLoan(Long id) {
        // 获取标的信息
        Lend lend = lendMapper.selectById(id);

        // 调用汇付宝放款接口
        Map<String, Object> map = new HashMap<>();
        map.put("agentId", HfbConst.AGENT_ID);
        map.put("agentProjectCode", lend.getLendNo());
        map.put("agentBillNo", LendNoUtils.getLoanNo());

        // 计算商户手续费
        BigDecimal monthRate = lend.getServiceRate().divide(new BigDecimal(12), 8, BigDecimal.ROUND_DOWN);
        BigDecimal mchFee = lend.getAmount().multiply(monthRate).multiply(new BigDecimal(lend.getPeriod()));
        map.put("mchFee", mchFee);

        // 添加接口剩余参数
        map.put("timestamp", RequestHelper.getTimestamp());
        map.put("sign", RequestHelper.getSign(map));

        // 发送调用请求
        JSONObject respData = RequestHelper.sendRequest(map, HfbConst.MAKE_LOAD_URL);
        log.info("放款结果: {}", respData.toJSONString());
        String resultCode = (String) respData.get("resultCode");
        if (!"0000".equals(resultCode))
            throw new BusinessException((String)respData.get("resultMsg"));

        // 修改标的状态和平台收益
        lend.setRealAmount(mchFee);
        lend.setStatus(LendStatusEnum.PAY_RUN.getStatus());
        lend.setPaymentTime(LocalDateTime.now());
        lendMapper.updateById(lend);

        // 借款人账号打款
        Long userId = lend.getUserId();
        String bindCode = userInfoMapper.selectById(userId).getBindCode();
        userAccountMapper.updateUserAccount(
                bindCode,
                new BigDecimal((String) respData.get("voteAmt")),
                new BigDecimal(0)
        );

        // 增加借款人交易流水
        TransFlowBO transFlowBO = new TransFlowBO(
                (String) respData.get("agentBillNo"),
                bindCode,
                new BigDecimal((String) respData.get("voteAmt")),
                TransTypeEnum.BORROW_BACK,
                "项目放款,项目编号: " + lend.getLendNo() + " ,项目名称: " + lend.getTitle()
        );
        transFlowService.saveTransFlow(transFlowBO);

        // 解冻并扣除投资人冻结资金, 同时增加投资人交易流水
        List<LendItem> lendItems = lendItemService.selectByLendId(lend.getId());
        lendItems.forEach(lendItem -> {
            Long investUserId = lendItem.getInvestUserId();
            UserInfo userInfo = userInfoMapper.selectById(investUserId);
            String investBindCode = userInfo.getBindCode();

            // 修改投资人账号中的冻结金额
            userAccountMapper.updateUserAccount(
                    investBindCode,
                    new BigDecimal(0),
                    lendItem.getInvestAmount().negate()
            );

            // 增加投资人交易流水, 冻结和解锁的交易单号是分开的
            TransFlowBO investTransFlowBO = new TransFlowBO(
                    LendNoUtils.getTransNo(),
                    investBindCode,
                    lendItem.getInvestAmount(),
                    TransTypeEnum.INVEST_UNLOCK,
                    "项目放款, 冻结资金转出, 项目编号: " + lend.getLendNo() + " 项目名称：" + lend.getTitle()
            );
            transFlowService.saveTransFlow(investTransFlowBO);
        });

        // 生成借款人还款计划, 出借人投资计划
        repaymentPlan(lend);

    }

    @Transactional(rollbackFor = Exception.class)
    // 生成标的还款计划
    void repaymentPlan(Lend lend) {
        // 创建每个月（以月数为下标）的还款计划（贷款人每个月总共要还多少钱, 忽略投资人）
        List<LendReturn> lendReturnList = new ArrayList<>();
        // 根据还款月数构建每月还款基础信息(lendReturn)
        Integer period = lend.getPeriod();
        for (int i = 1; i <= period; i++) {
            LendReturn lendReturn = new LendReturn();

            // 设置基础信息
            lendReturn.setLendId(lend.getId());
            lendReturn.setBorrowInfoId(lend.getBorrowInfoId());
            lendReturn.setReturnNo(LendNoUtils.getReturnNo());
            lendReturn.setAmount(lend.getAmount());  // 注意这个是你借款的总额, 本月还款总额是total
            lendReturn.setBaseAmount(lend.getInvestAmount());  // 实际投资金额, 毕竟可能借不到足够的钱
            lendReturn.setCurrentPeriod(i);
            lendReturn.setLendYearRate(lend.getLendYearRate());
            lendReturn.setReturnMethod(lend.getReturnMethod());
            // 本金 利息 本息得通过投资收益计划来计算
            lendReturn.setFee(new BigDecimal(0));
            lendReturn.setReturnDate(lend.getLendStartDate().plusMonths(i));
            lendReturn.setOverdue(false);
            lendReturn.setOverdueTotal(new BigDecimal(0));
            if (i == period)
                lendReturn.setLast(true);
            else
                lendReturn.setLast(false);
            lendReturn.setStatus(0);

            lendReturnList.add(lendReturn);
        }
        // 保存还款计划到数据库里
        lendReturnService.saveBatch(lendReturnList);

        // 组建包含了 所有投资人 的 每个月收益计划 的 总收益计划
        List<LendItemReturn> allLendItemReturns = new ArrayList<>();

        // 基于投资信息（lend_item, 可能有一笔或多笔）获取每个月的收益计划(lend_item_return)
        // 基于期数和还款计划的id建立对应的map, 这样便于后面每期的收益计划就可以一一对应还款计划
        // 我觉得设计的不合理, 不如单开一个外键表
        Map<Integer, Long> lendReturnMap = lendReturnList.stream().collect(
                Collectors.toMap(LendReturn::getCurrentPeriod, LendReturn::getId));

        List<LendItem> lendItems = lendItemService.selectByLendId(lend.getId());
        for (LendItem lendItem : lendItems) {
            List<LendItemReturn> lendItemReturns = returnInvestPlan(lendItem.getId(), lendReturnMap, lend);
            allLendItemReturns.addAll(lendItemReturns);
        }

        // 通过总收益计划, 结合lend_return的id（即确定月数）来计算借款人当月需要还款总数, 本金, 利息
        for (LendReturn lendReturn : lendReturnList) {
            // 获取总本金
            BigDecimal sumPrincipal = allLendItemReturns.stream()
                    .filter(lendItemReturn -> lendItemReturn.getLendReturnId().longValue() == lendReturn.getId().longValue())
                    .map(LendItemReturn::getPrincipal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 获取总利息
            BigDecimal sumInterest = allLendItemReturns.stream()
                    .filter(lendItemReturn -> lendItemReturn.getLendReturnId().longValue() == lendReturn.getId().longValue())
                    .map(LendItemReturn::getInterest)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 获取总收入
            BigDecimal total = allLendItemReturns.stream()
                    .filter(lendItemReturn -> lendItemReturn.getLendReturnId().longValue() == lendReturn.getId().longValue())
                    .map(LendItemReturn::getTotal)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            // 更新数据
            lendReturn.setPrincipal(sumPrincipal);
            lendReturn.setInterest(sumInterest);
            lendReturn.setTotal(total);
        }

        lendReturnService.updateBatchById(lendReturnList);

    }

    // 当前标的下, 针对某一笔投资的回款计划
    public List<LendItemReturn> returnInvestPlan(
            Long lendItemId,
            Map<Integer, Long> lendReturnMap,
            Lend lend)
    {
        // 获取投资信息
        LendItem lendItem = lendItemService.getById(lendItemId);
        BigDecimal lendYearRate = lendItem.getLendYearRate();
        Integer totalMonth = lend.getPeriod();
        BigDecimal investAmount = lendItem.getInvestAmount();

        // 每月利息
        Map<Integer, BigDecimal> perMonthInterest;
        // 每月本金
        Map<Integer, BigDecimal> perMonthPrincipal;

        // 根据投资金额以及还款方式和期数计算每期本息
        if (lend.getReturnMethod().intValue() == ReturnMethodEnum.ONE.getMethod().intValue()) {
            perMonthInterest = Amount1Helper.getPerMonthInterest(investAmount, lendYearRate, totalMonth);
            perMonthPrincipal = Amount1Helper.getPerMonthPrincipal(investAmount, lendYearRate, totalMonth);
        } else if (lend.getReturnMethod().intValue() == ReturnMethodEnum.TWO.getMethod().intValue()) {
            perMonthInterest = Amount2Helper.getPerMonthInterest(investAmount, lendYearRate, totalMonth);
            perMonthPrincipal = Amount2Helper.getPerMonthPrincipal(investAmount, lendYearRate, totalMonth);
        } else if (lend.getReturnMethod().intValue() == ReturnMethodEnum.THREE.getMethod().intValue()) {
            perMonthInterest = Amount3Helper.getPerMonthInterest(investAmount, lendYearRate, totalMonth);
            perMonthPrincipal = Amount3Helper.getPerMonthPrincipal(investAmount, lendYearRate, totalMonth);
        } else if (lend.getReturnMethod().intValue() == ReturnMethodEnum.FOUR.getMethod().intValue()) {
            perMonthInterest = Amount4Helper.getPerMonthInterest(investAmount, lendYearRate, totalMonth);
            perMonthPrincipal = Amount4Helper.getPerMonthPrincipal(investAmount, lendYearRate, totalMonth);
        } else
            throw new BusinessException("还款方式不存在");

        // 创建收益计划列表
        List<LendItemReturn> lendItemReturns = new ArrayList<>();
        for (Map.Entry<Integer, BigDecimal> entry : perMonthInterest.entrySet()) {
            // 创建本期（月）收益计划
            LendItemReturn lendItemReturn = new LendItemReturn();

            // 填补基础信息
            Integer currentMonth = entry.getKey();
            lendItemReturn.setCurrentPeriod(currentMonth);
            lendItemReturn.setLendYearRate(lend.getLendYearRate());
            lendItemReturn.setReturnMethod(lend.getReturnMethod());

            lendItemReturn.setLendReturnId(lendReturnMap.get(currentMonth));
            lendItemReturn.setLendItemId(lendItemId);
            lendItemReturn.setLendId(lend.getId());
            lendItemReturn.setInvestUserId(lendItem.getInvestUserId());
            lendItemReturn.setInvestAmount(lendItem.getInvestAmount());

            // 计算本金和利息, 如果是最后一个月, 考虑到计算机取整的问题, 最后一个月受益为总收益 - 之前的收益之和
            if (currentMonth.equals(lend.getPeriod())) {
                BigDecimal principalSum = lendItemReturns.stream().map(LendItemReturn::getPrincipal).
                        reduce(BigDecimal.ZERO, BigDecimal::add);
                lendItemReturn.setPrincipal(lendItem.getInvestAmount().subtract(principalSum));

                BigDecimal interestSum = lendItemReturns.stream().map(LendItemReturn::getInterest).
                        reduce(BigDecimal.ZERO, BigDecimal::add);
                lendItemReturn.setInterest(lendItem.getExpectAmount().subtract(interestSum));
            } else {
                lendItemReturn.setPrincipal(perMonthPrincipal.get(currentMonth));
                lendItemReturn.setInterest(perMonthInterest.get(currentMonth));
            }

            // 设置总额 服务费 还款日期
            lendItemReturn.setTotal(lendItemReturn.getInterest().add(lendItemReturn.getPrincipal()));
            lendItemReturn.setFee(new BigDecimal(0));
            lendItemReturn.setReturnDate(lendItem.getLendStartDate().plusMonths(currentMonth));

            // 设置是否逾期, 默认没有
            lendItemReturn.setOverdue(false);
            lendItemReturn.setStatus(0);

            lendItemReturns.add(lendItemReturn);
        }

        lendItemReturnService.saveBatch(lendItemReturns);

        return lendItemReturns;
    }
}
