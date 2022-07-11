package com.atguigu.srb.core.service.impl;

import com.atguigu.common.exception.Assert;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.common.util.MD5;
import com.atguigu.srb.base.util.JwtUtils;
import com.atguigu.srb.core.mapper.UserAccountMapper;
import com.atguigu.srb.core.mapper.UserInfoMapper;
import com.atguigu.srb.core.mapper.UserLoginRecordMapper;
import com.atguigu.srb.core.pojo.entity.UserAccount;
import com.atguigu.srb.core.pojo.entity.UserInfo;
import com.atguigu.srb.core.pojo.entity.UserLoginRecord;
import com.atguigu.srb.core.pojo.query.UserInfoQuery;
import com.atguigu.srb.core.pojo.vo.LoginVO;
import com.atguigu.srb.core.pojo.vo.RegisterVO;
import com.atguigu.srb.core.pojo.vo.UserIndexVO;
import com.atguigu.srb.core.pojo.vo.UserInfoVO;
import com.atguigu.srb.core.service.UserInfoService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.StringUtils;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;

/**
 * <p>
 * 用户基本信息 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Service
@Slf4j
public class UserInfoServiceImpl extends ServiceImpl<UserInfoMapper, UserInfo> implements UserInfoService {

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private UserLoginRecordMapper userLoginRecordMapper;

    @Resource
    private UserAccountMapper userAccountMapper;

    @Transactional(rollbackFor = Exception.class)
    @Override
    public void register(RegisterVO registerVO) {
        // Check if phone number has been registered
        QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
        userInfoQueryWrapper.eq("mobile", registerVO.getMobile());
        Assert.isTrue(userInfoMapper.selectCount(userInfoQueryWrapper) <= 0, ResponseEnum.MOBILE_EXIST_ERROR);

        // Insert table user_info
        UserInfo userInfo = new UserInfo();
        userInfo.setName("defaultName");
        userInfo.setNickName("defaultNickName");
        userInfo.setUserType(registerVO.getUserType());
        userInfo.setMobile(registerVO.getMobile());
        userInfo.setPassword(MD5.encrypt(registerVO.getPassword()));
        userInfo.setStatus(UserInfo.STATUS_NORMAL);
        userInfo.setHeadImg(UserInfo.USER_DEFAULT_AVATAR);
        userInfoMapper.insert(userInfo);

        // Insert table user_account
        UserAccount userAccount = new UserAccount();
        userAccount.setUserId(userInfo.getId());
        userAccountMapper.insert(userAccount);
    }

    @Override
    public UserInfoVO login(LoginVO loginVO, String remoteAddr) {
        // User exists
        QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
        userInfoQueryWrapper
                .eq("mobile", loginVO.getMobile())
                .eq("user_type", loginVO.getUserType());
        UserInfo userInfo = userInfoMapper.selectOne(userInfoQueryWrapper);
        Assert.notNull(userInfo, ResponseEnum.LOGIN_MOBILE_ERROR);

        // Password true
        Assert.equals(userInfo.getPassword(), MD5.encrypt(loginVO.getPassword()), ResponseEnum.LOGIN_PASSWORD_ERROR);

        // User banned
        Assert.isTrue(userInfo.getStatus() == 1, ResponseEnum.LOGIN_LOKED_ERROR);

        // Record in user_login_record
        UserLoginRecord record = new UserLoginRecord();
        record.setIp(remoteAddr);
        record.setUserId(userInfo.getId());
        userLoginRecordMapper.insert(record);

        // Gen token
        String token = JwtUtils.createToken(userInfo.getId(), userInfo.getName());

        // Create userInfoVO
        UserInfoVO userInfoVO = new UserInfoVO();
//        userInfoVO.setName(userInfo.getName());
//        userInfoVO.setMobile(userInfo.getMobile());
//        userInfoVO.setUserType(userInfo.getUserType());
//        userInfoVO.setNickName(userInfo.getNickName());
//        userInfoVO.setHeadImg(userInfo.getHeadImg());
        userInfoVO.setToken(token);
        BeanUtils.copyProperties(userInfo, userInfoVO);
        // Return userInfoVO
        return userInfoVO;
    }

    @Override
    public IPage<UserInfo> listPage(Page<UserInfo> pageParams, UserInfoQuery userInfoQuery) {
        if(userInfoQuery == null) {
            return userInfoMapper.selectPage(pageParams, null);
        }
        else {
            QueryWrapper<UserInfo> queryWrapper = new QueryWrapper<>();
            String mobile = userInfoQuery.getMobile();
            Integer status = userInfoQuery.getStatus();
            Integer userType = userInfoQuery.getUserType();
            queryWrapper.eq(StringUtils.isNotBlank(mobile), "mobile", mobile);
            queryWrapper.eq(status != null, "status", status);
            queryWrapper.eq(userType != null, "user_type", userType);
            return userInfoMapper.selectPage(pageParams, queryWrapper);
        }
    }

    @Override
    public void lock(Long id, Integer status) {
        UserInfo userInfo = new UserInfo();
        userInfo.setId(id);
        userInfo.setStatus(status);
        userInfoMapper.updateById(userInfo);
    }

    @Override
    public boolean checkMobile(String mobile) {
        QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
        userInfoQueryWrapper.eq("mobile", mobile);
        Integer count = userInfoMapper.selectCount(userInfoQueryWrapper);
        return count > 0;

    }

    @Override
    public UserIndexVO getIndexUserInfo(Long userId) {
        UserIndexVO userIndexVO = new UserIndexVO();
        // 获取用户基础信息
        UserInfo userInfo = userInfoMapper.selectById(userId);
        BeanUtils.copyProperties(userInfo, userIndexVO);
        // 获取用户资金
        QueryWrapper<UserAccount> userAccountQueryWrapper = new QueryWrapper<>();
        userAccountQueryWrapper.eq("user_id", userId);
        UserAccount userAccount = userAccountMapper.selectOne(userAccountQueryWrapper);
        userIndexVO.setAmount(userAccount.getAmount());
        userIndexVO.setFreezeAmount(userAccount.getFreezeAmount());
        // 获取用户登陆时间
        QueryWrapper<UserLoginRecord> userLoginRecordQueryWrapper = new QueryWrapper<>();
        userLoginRecordQueryWrapper.eq("user_id", userId).orderByDesc("id").last("limit 1");
        UserLoginRecord loginRecord = userLoginRecordMapper.selectOne(userLoginRecordQueryWrapper);
        userIndexVO.setLastLoginTime(loginRecord.getCreateTime());

        return userIndexVO;
    }

    @Override
    public String getMobileByBindCode(String bindCode) {
        QueryWrapper<UserInfo> userInfoQueryWrapper = new QueryWrapper<>();
        userInfoQueryWrapper.eq("bind_code", bindCode);
        return userInfoMapper.selectOne(userInfoQueryWrapper).getMobile();
    }


}
