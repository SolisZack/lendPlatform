package com.atguigu.srb.core.service.impl;

import com.atguigu.common.exception.Assert;
import com.atguigu.common.result.ResponseEnum;
import com.atguigu.srb.core.enums.UserBindEnum;
import com.atguigu.srb.core.hfb.FormHelper;
import com.atguigu.srb.core.hfb.HfbConst;
import com.atguigu.srb.core.hfb.RequestHelper;
import com.atguigu.srb.core.mapper.UserBindMapper;
import com.atguigu.srb.core.mapper.UserInfoMapper;
import com.atguigu.srb.core.pojo.entity.UserBind;
import com.atguigu.srb.core.pojo.entity.UserInfo;
import com.atguigu.srb.core.pojo.vo.UserBindVO;
import com.atguigu.srb.core.service.UserBindService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * <p>
 * 用户绑定表 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Service
public class UserBindServiceImpl extends ServiceImpl<UserBindMapper, UserBind> implements UserBindService {

    @Resource
    private UserBindMapper userBindMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String commitBindUser(UserBindVO userBindVO, Long userId) {
        // One idCard can only bind by one user
        QueryWrapper<UserBind> idCardQueryWrapper = new QueryWrapper<>();
        idCardQueryWrapper
                .eq("id_card", userBindVO.getIdCard())
                .ne("user_id", userId);
        UserBind sameCardDiffId = userBindMapper.selectOne(idCardQueryWrapper);
        Assert.isNull(sameCardDiffId, ResponseEnum.USER_BIND_IDCARD_EXIST_ERROR);

        // Check if user has written bind form before
        QueryWrapper<UserBind> userBindQueryWrapper = new QueryWrapper<>();
        userBindQueryWrapper.eq("user_id", userId);
        UserBind userBind = userBindMapper.selectOne(userBindQueryWrapper);
        if(userBind == null) {
            // Gen user bind record
            UserBind newUserBind = new UserBind();
            BeanUtils.copyProperties(userBindVO, newUserBind);
            newUserBind.setUserId(userId);
            newUserBind.setStatus(UserBindEnum.NO_BIND.getStatus());  // Not Bind Yet
            userBindMapper.insert(newUserBind);
        }else {
            BeanUtils.copyProperties(userBindVO, userBind);
            userBindMapper.updateById(userBind);

        }

        // Gen userBind form
        HashMap<String, Object> userBindHashMap = new HashMap<>();
        userBindHashMap.put("agentId", HfbConst.AGENT_ID);
        userBindHashMap.put("agentUserId", userId);
        userBindHashMap.put("idCard", userBindVO.getIdCard());
        userBindHashMap.put("personalName", userBindVO.getName());
        userBindHashMap.put("bankType", userBindVO.getBankType());
        userBindHashMap.put("bankNo", userBindVO.getBankNo());
        userBindHashMap.put("mobile", userBindVO.getMobile());
        userBindHashMap.put("returnUrl", HfbConst.USERBIND_RETURN_URL);
        userBindHashMap.put("notifyUrl", HfbConst.USERBIND_NOTIFY_URL);
        userBindHashMap.put("timestamp", RequestHelper.getTimestamp());
        userBindHashMap.put("sign", RequestHelper.getSign(userBindHashMap));
        String formStr = FormHelper.buildForm(HfbConst.USERBIND_URL, userBindHashMap);
        return formStr;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void notifyBind(Map<String, Object> paramMap) {
        String bindCode = (String) paramMap.get("bindCode");
        String userId = (String)  paramMap.get("agentUserId");
        // Update table user_bind
        QueryWrapper<UserBind> userBindQueryWrapper = new QueryWrapper<>();
        userBindQueryWrapper.eq("user_id", userId);
        UserBind userBind = userBindMapper.selectOne(userBindQueryWrapper);
        userBind.setStatus(UserBindEnum.BIND_OK.getStatus());
        userBind.setBindCode(bindCode);
        userBindMapper.updateById(userBind);

        // Update table user_info
        UserInfo userInfo = userInfoMapper.selectById(userId);
        userInfo.setBindCode(bindCode);
        userInfo.setName(userBind.getName());
        userInfo.setIdCard(userBind.getIdCard());
        userInfo.setBindStatus(UserBindEnum.BIND_OK.getStatus());
        userInfoMapper.updateById(userInfo);
    }

    @Override
    public String getBindCodeByUserId(Long userId) {
        QueryWrapper<UserBind> userBindQueryWrapper = new QueryWrapper<>();
        userBindQueryWrapper.eq("user_id", userId);
        return userBindMapper.selectOne(userBindQueryWrapper).getBindCode();

    }
}
