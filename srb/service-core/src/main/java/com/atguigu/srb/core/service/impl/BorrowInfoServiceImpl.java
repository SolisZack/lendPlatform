package com.atguigu.srb.core.service.impl;

import com.atguigu.common.exception.Assert;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.core.enums.BorrowAuthEnum;
import com.atguigu.srb.core.enums.BorrowInfoStatusEnum;
import com.atguigu.srb.core.enums.UserBindEnum;
import com.atguigu.srb.core.mapper.BorrowInfoMapper;
import com.atguigu.srb.core.mapper.BorrowerMapper;
import com.atguigu.srb.core.mapper.IntegralGradeMapper;
import com.atguigu.srb.core.mapper.UserInfoMapper;
import com.atguigu.srb.core.pojo.entity.BorrowInfo;
import com.atguigu.srb.core.pojo.entity.Borrower;
import com.atguigu.srb.core.pojo.entity.IntegralGrade;
import com.atguigu.srb.core.pojo.entity.Lend;
import com.atguigu.srb.core.pojo.vo.BorrowInfoApprovalVO;
import com.atguigu.srb.core.pojo.vo.BorrowerDetailVO;
import com.atguigu.srb.core.service.BorrowInfoService;
import com.atguigu.srb.core.service.BorrowerService;
import com.atguigu.srb.core.service.DictService;
import com.atguigu.srb.core.service.LendService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestBody;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 借款信息表 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Service
public class BorrowInfoServiceImpl extends ServiceImpl<BorrowInfoMapper, BorrowInfo> implements BorrowInfoService {

    @Resource
    private BorrowInfoMapper borrowInfoMapper;

    @Resource
    private BorrowerMapper borrowerMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private IntegralGradeMapper integralGradeMapper;

    @Resource
    private DictService dictService;

    @Resource
    private BorrowerService borrowerService;

    @Resource
    private LendService lendService;


    @Override
    public BigDecimal getBorrowAmount(Long userId) {
        // Get user integral
        Integer integral = userInfoMapper.selectById(userId).getIntegral();
        QueryWrapper<IntegralGrade> integralGradeMapperQueryWrapper = new QueryWrapper<>();
        // Get user borrowAmount
        integralGradeMapperQueryWrapper
                .le("integral_start", integral)
                .ge("integral_end", integral);
        IntegralGrade integralGrade = integralGradeMapper.selectOne(integralGradeMapperQueryWrapper);
        if (integralGrade == null)
            return new BigDecimal(0);

        return integralGrade.getBorrowAmount();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveBorrowInfo(BorrowInfo borrowInfo, Long userId) {
        // Verify user bind status
        Integer bindStatus = userInfoMapper.selectById(userId).getBindStatus();
        Assert.equals(UserBindEnum.BIND_OK.getStatus(), bindStatus, ResponseEnum.USER_NO_BIND_ERROR);

        // Verify borrower auth status
        QueryWrapper<Borrower> borrowerQueryWrapper = new QueryWrapper<>();
        borrowerQueryWrapper.eq("user_id", userId);
        Integer authStatus = borrowerMapper.selectOne(borrowerQueryWrapper).getStatus();
        Assert.equals(BorrowAuthEnum.AUTH_OK.getStatus(), authStatus, ResponseEnum.USER_NO_AMOUNT_ERROR);

        // Check borrowAmount
        BigDecimal borrowAmount = this.getBorrowAmount(userId);
        Assert.isTrue(
                borrowAmount.doubleValue() >= borrowInfo.getAmount().doubleValue(),
                ResponseEnum.USER_AMOUNT_LESS_ERROR);

        // Save borrow info
        borrowInfo.setUserId(userId);
        // Set BorrowYearRate from integer to decimal by divide 100
        borrowInfo.setBorrowYearRate(borrowInfo.getBorrowYearRate()
                .divide(new BigDecimal("100.00")));
        borrowInfo.setStatus(BorrowInfoStatusEnum.CHECK_RUN.getStatus());
        borrowInfoMapper.insert(borrowInfo);
    }

    @Override
    public Integer getStatusByUserId(Long userId) {
        QueryWrapper<BorrowInfo> borrowInfoQueryWrapper = new QueryWrapper<>();
        borrowInfoQueryWrapper.eq("user_id", userId);
        List<BorrowInfo> borrowInfos = borrowInfoMapper.selectList(borrowInfoQueryWrapper);
        if (borrowInfos.size() == 0)
            return BorrowInfoStatusEnum.NO_AUTH.getStatus();
        return borrowInfos.get(0).getStatus();
    }

    @Override
    public List<BorrowInfo> selectList() {
        List<BorrowInfo> borrowInfoList = borrowInfoMapper.selectBorrowInfoList();
        borrowInfoList.forEach(borrowInfo -> {
            // ReturnMethod to string
            String returnMethod = dictService.getNameByParentDictCodeAndValue(
                    "returnMethod", borrowInfo.getReturnMethod());
            // moneyUser to string
            String moneyUse = dictService.getNameByParentDictCodeAndValue(
                    "moneyUse", borrowInfo.getReturnMethod());
            // Status to string
            String status = BorrowInfoStatusEnum.getMsgByStatus(borrowInfo.getStatus());
            // Store string var to map param
            borrowInfo.getParam().put("returnMethod", returnMethod);
            borrowInfo.getParam().put("moneyUse", moneyUse);
            borrowInfo.getParam().put("status", status);

        });
        return borrowInfoList;
    }

    @Override
    public Map<String, Object> getBorrowInfoDetail(Long id) {
        Map<String, Object> result = new HashMap<>();

        // Query BorrowInfo
        BorrowInfo borrowInfo = borrowInfoMapper.selectById(id);

        // ReturnMethod to string
        String returnMethod = dictService.getNameByParentDictCodeAndValue(
                "returnMethod", borrowInfo.getReturnMethod());
        // moneyUser to string
        String moneyUse = dictService.getNameByParentDictCodeAndValue(
                "moneyUse", borrowInfo.getReturnMethod());
        // Status to string
        String status = BorrowInfoStatusEnum.getMsgByStatus(borrowInfo.getStatus());
        // Store string var to map param
        borrowInfo.getParam().put("returnMethod", returnMethod);
        borrowInfo.getParam().put("moneyUse", moneyUse);
        borrowInfo.getParam().put("status", status);
        // Store borrowInfo to result
        result.put("borrowInfo", borrowInfo);

        // Query BorrowerDetailVO
        QueryWrapper<Borrower> borrowerQueryWrapper = new QueryWrapper<>();
        borrowerQueryWrapper.eq("user_id", borrowInfo.getUserId());
        Borrower borrower = borrowerMapper.selectOne(borrowerQueryWrapper);
        BorrowerDetailVO borrowerDetailVO = borrowerService.getBorrowerDetailVOById(borrower.getId());
        result.put("borrower", borrowerDetailVO);

        return result;
    }

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void approval(BorrowInfoApprovalVO borrowInfoApprovalVO) {
        // Change borrowInfo status
        BorrowInfo borrowInfo = borrowInfoMapper.selectById(borrowInfoApprovalVO.getId());
        borrowInfo.setStatus(borrowInfoApprovalVO.getStatus());
        borrowInfoMapper.updateById(borrowInfo);
        // Insert into table lend
        if (borrowInfoApprovalVO.getStatus().equals(BorrowInfoStatusEnum.CHECK_FAIL.getStatus()))
            return;
        lendService.createLend(borrowInfoApprovalVO, borrowInfo);

    }
}
