package com.atguigu.srb.core.service.impl;

import com.atguigu.srb.core.enums.BorrowAuthEnum;
import com.atguigu.srb.core.enums.BorrowerStatusEnum;
import com.atguigu.srb.core.enums.IntegralEnum;
import com.atguigu.srb.core.mapper.BorrowerAttachMapper;
import com.atguigu.srb.core.mapper.BorrowerMapper;
import com.atguigu.srb.core.mapper.UserInfoMapper;
import com.atguigu.srb.core.mapper.UserIntegralMapper;
import com.atguigu.srb.core.pojo.entity.Borrower;
import com.atguigu.srb.core.pojo.entity.BorrowerAttach;
import com.atguigu.srb.core.pojo.entity.UserInfo;
import com.atguigu.srb.core.pojo.entity.UserIntegral;
import com.atguigu.srb.core.pojo.vo.BorrowerApprovalVO;
import com.atguigu.srb.core.pojo.vo.BorrowerAttachVO;
import com.atguigu.srb.core.pojo.vo.BorrowerDetailVO;
import com.atguigu.srb.core.pojo.vo.BorrowerVO;
import com.atguigu.srb.core.service.BorrowerAttachService;
import com.atguigu.srb.core.service.BorrowerService;
import com.atguigu.srb.core.service.DictService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.apache.commons.lang.StringUtils;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 借款人 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Service
public class BorrowerServiceImpl extends ServiceImpl<BorrowerMapper, Borrower> implements BorrowerService {
    @Resource
    private BorrowerMapper borrowerMapper;

    @Resource
    private BorrowerAttachMapper borrowerAttachMapper;

    @Resource
    private BorrowerAttachService borrowerAttachService;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private UserIntegralMapper userIntegralMapper;

    @Resource
    private DictService dictService;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void saveBorrowerVOByUserId(BorrowerVO borrowerVO, Long userId) {
        // Get user info by userId and update its borrow_auth_status
        UserInfo userInfo = userInfoMapper.selectById(userId);
        userInfo.setBorrowAuthStatus(BorrowerStatusEnum.AUTH_RUN.getStatus());
        userInfoMapper.updateById(userInfo);

        // Get borrower info from VO
        Borrower borrower = new Borrower();
        BeanUtils.copyProperties(borrowerVO, borrower);

        // Get extra borrower info from userInfo
        borrower.setUserId(userId);
        borrower.setName(userInfo.getName());
        borrower.setIdCard(userInfo.getIdCard());
        borrower.setMobile(userInfo.getMobile());
        borrower.setStatus(BorrowerStatusEnum.AUTH_RUN.getStatus());

        // Save borrower info into table borrower
        borrowerMapper.insert(borrower);

        // Save borrow attachment
        List<BorrowerAttach> borrowerAttachList = borrowerVO.getBorrowerAttachList();
        borrowerAttachList.forEach(borrowerAttach -> {
            borrowerAttach.setBorrowerId(userId);
            borrowerAttachMapper.insert(borrowerAttach);
        });
    }

    @Override
    public Integer getStatusByUserId(Long userId) {
        QueryWrapper<Borrower> borrowerQueryWrapper = new QueryWrapper<>();
        borrowerQueryWrapper.select("status").eq("user_id", userId);
        List<Object> objects = borrowerMapper.selectObjs(borrowerQueryWrapper);
        if(objects.size() == 0)
            return BorrowerStatusEnum.NO_AUTH.getStatus();
        else
            return (Integer) objects.get(0);
    }

    @Override
    public IPage<Borrower> listPage(Page<Borrower> borrowerPage, String keyword) {
        // Keyword is blank, return all
        if(StringUtils.isBlank(keyword))
            return borrowerMapper.selectPage(borrowerPage, null);
        // Keyword is not blank, return page based on keyword
        QueryWrapper<Borrower> borrowerQueryWrapper = new QueryWrapper<>();
        borrowerQueryWrapper
                .like("name", keyword)
                .or().like("mobile", keyword)
                .or().like("id_card", keyword)
                .orderByDesc("id");

        return borrowerMapper.selectPage(borrowerPage, borrowerQueryWrapper);

    }

    @Override
    public BorrowerDetailVO getBorrowerDetailVOById(Long id) {
        BorrowerDetailVO borrowerDetailVO = new BorrowerDetailVO();
        // Get base borrower info from borrower
        Borrower borrower = borrowerMapper.selectById(id);
        BeanUtils.copyProperties(borrower, borrowerDetailVO);
        // Married to string
        borrowerDetailVO.setMarry(borrower.getMarry() ? "是" : "否");
        // Sex to string
        borrowerDetailVO.setSex(borrower.getSex() == 1 ? "男" : "女");
        // Education to string
        borrowerDetailVO.setEducation(dictService.getNameByParentDictCodeAndValue("education", borrower.getEducation()));
        // Industry to string
        borrowerDetailVO.setIndustry(dictService.getNameByParentDictCodeAndValue("industry", borrower.getIndustry()));
        // Income to string
        borrowerDetailVO.setIncome(dictService.getNameByParentDictCodeAndValue("income", borrower.getIncome()));
        // ReturnSource to string
        borrowerDetailVO.setReturnSource(dictService.getNameByParentDictCodeAndValue("returnSource", borrower.getReturnSource()));
        // ContactsRelation to string
        borrowerDetailVO.setContactsRelation(dictService.getNameByParentDictCodeAndValue("relation", borrower.getContactsRelation()));
        // Get Status
        String status = BorrowerStatusEnum.getMsgByStatus(borrower.getStatus());
        borrowerDetailVO.setStatus(status);
        // Get borrower attachment
        List<BorrowerAttachVO> borrowerAttachVOS = borrowerAttachService.selectBorrowerAttachVOList(borrower.getUserId());
        borrowerDetailVO.setBorrowerAttachVOList(borrowerAttachVOS);

        return borrowerDetailVO;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    // No need to check for duplicate, frontend can make sure that won't happen
    public void approval(BorrowerApprovalVO borrowerApprovalVO) {
        Long borrowerId = borrowerApprovalVO.getBorrowerId();

        // Update borrower status
        Borrower borrower = borrowerMapper.selectById(borrowerId);
        borrower.setStatus(borrowerApprovalVO.getStatus());
        borrowerMapper.updateById(borrower);

        // Calculate integral and update / Insert table user_integral
        Long userId = borrower.getUserId();
        UserIntegral userIntegral = new UserIntegral();
        userIntegral.setUserId(userId);

        // Get user info for further integral update
        UserInfo userInfo = userInfoMapper.selectById(userId);
        Integer integral = userInfo.getIntegral();

        // If auth_fail, update table user_info and return
        if (borrowerApprovalVO.getStatus().equals(BorrowAuthEnum.AUTH_FAIL.getStatus())) {
            userInfo.setBorrowAuthStatus(BorrowAuthEnum.AUTH_FAIL.getStatus());
            userInfoMapper.updateById(userInfo);
            return;
        }

        // Integral from base info
        userIntegral.setIntegral(borrowerApprovalVO.getInfoIntegral());
        userIntegral.setContent("借款人基本信息");
        // Check if duplicate
        QueryWrapper<UserIntegral> userIntegralQueryWrapper = new QueryWrapper<>();
        userIntegralQueryWrapper.eq("content", "借款人基本信息");
        if (userIntegralMapper.selectCount(userIntegralQueryWrapper) == 0) {
            integral += borrowerApprovalVO.getInfoIntegral();
            userIntegralMapper.insert(userIntegral);
        }


        // Integral from idCard
        if (borrowerApprovalVO.getIsIdCardOk()) {
            userIntegral.setIntegral(IntegralEnum.BORROWER_IDCARD.getIntegral());
            userIntegral.setContent(IntegralEnum.BORROWER_IDCARD.getMsg());
            // Check if duplicate
            userIntegralQueryWrapper = new QueryWrapper<>();
            userIntegralQueryWrapper.eq("content", IntegralEnum.BORROWER_IDCARD.getMsg());
            if (userIntegralMapper.selectCount(userIntegralQueryWrapper) == 0) {
                integral += IntegralEnum.BORROWER_IDCARD.getIntegral();
                userIntegralMapper.insert(userIntegral);
            }

        }

        // Integral from card pic
        if (borrowerApprovalVO.getIsCarOk()) {
            userIntegral.setIntegral(IntegralEnum.BORROWER_CAR.getIntegral());
            userIntegral.setContent(IntegralEnum.BORROWER_CAR.getMsg());
            // Check if duplicate
            userIntegralQueryWrapper = new QueryWrapper<>();
            userIntegralQueryWrapper.eq("content", IntegralEnum.BORROWER_CAR.getMsg());
            if (userIntegralMapper.selectCount(userIntegralQueryWrapper) == 0) {
                integral += IntegralEnum.BORROWER_CAR.getIntegral();
                userIntegralMapper.insert(userIntegral);
            }

        }

        // Integral from house pic
        if (borrowerApprovalVO.getIsHouseOk()) {
            userIntegral.setIntegral(IntegralEnum.BORROWER_HOUSE.getIntegral());
            userIntegral.setContent(IntegralEnum.BORROWER_HOUSE.getMsg());
            // Check if duplicate
            userIntegralQueryWrapper = new QueryWrapper<>();
            userIntegralQueryWrapper.eq("content", IntegralEnum.BORROWER_HOUSE.getMsg());
            if (userIntegralMapper.selectCount(userIntegralQueryWrapper) == 0) {
                integral += IntegralEnum.BORROWER_HOUSE.getIntegral();
                userIntegralMapper.insert(userIntegral);
            }
        }

        // Update integral and BorrowAuthStatus in table user_info
        userInfo.setIntegral(integral);
        userInfo.setBorrowAuthStatus(BorrowAuthEnum.AUTH_OK.getStatus());
        userInfoMapper.updateById(userInfo);
    }
}
