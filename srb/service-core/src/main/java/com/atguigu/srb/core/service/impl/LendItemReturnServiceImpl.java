package com.atguigu.srb.core.service.impl;

import com.atguigu.srb.core.mapper.LendItemMapper;
import com.atguigu.srb.core.mapper.LendItemReturnMapper;
import com.atguigu.srb.core.mapper.LendMapper;
import com.atguigu.srb.core.pojo.entity.Lend;
import com.atguigu.srb.core.pojo.entity.LendItem;
import com.atguigu.srb.core.pojo.entity.LendItemReturn;
import com.atguigu.srb.core.service.LendItemReturnService;
import com.atguigu.srb.core.service.UserBindService;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * <p>
 * 标的出借回款记录表 服务实现类
 * </p>
 *
 * @author zack
 * @since 2022-07-01
 */
@Slf4j
@Service
public class LendItemReturnServiceImpl extends ServiceImpl<LendItemReturnMapper, LendItemReturn> implements LendItemReturnService {

    @Resource
    private LendItemReturnMapper lendItemReturnMapper;

    @Resource
    private LendMapper lendMapper;

    @Resource
    private LendItemMapper lendItemMapper;

    @Resource
    private UserBindService userBindService;

    @Override
    public List<LendItemReturn> selectByLendId(Long lendId, Long userId) {
        QueryWrapper<LendItemReturn> lendItemReturnQueryWrapper = new QueryWrapper<>();
        lendItemReturnQueryWrapper.eq("lend_id", lendId).eq("invest_user_id", userId)
        .orderByDesc("current_period").orderByDesc("id");
        return lendItemReturnMapper.selectList(lendItemReturnQueryWrapper);
    }


    @Override
    public List<Map<String, Object>> addReturnDetail(Long lendReturnId) {
        // 创建还款明细变量
        List<Map<String, Object>> returnDetails = new ArrayList<>();
        // 获取投资收益计划表
        List<LendItemReturn> lendItemReturns = this.selectLendItemReturnList(lendReturnId);
        // 获取标的
        Lend lend = lendMapper.selectById(lendItemReturns.get(0).getLendId());
        for (LendItemReturn lendItemReturn : lendItemReturns) {
            // 获取投资记录
            LendItem lendItem = lendItemMapper.selectById(lendItemReturn.getLendItemId());
            // 创建还款明细
            HashMap<String, Object> returnDetail = new HashMap<>();
            returnDetail.put("agentProjectCode", lend.getLendNo());
            returnDetail.put("voteBillNo", lendItem.getLendItemNo());
            returnDetail.put("toBindCode", userBindService.getBindCodeByUserId(lendItemReturn.getInvestUserId()));
            returnDetail.put("transitAmt", lendItemReturn.getTotal());
            log.info("transitAmt:{}", lendItemReturn.getTotal());
            returnDetail.put("baseAmt", lendItemReturn.getPrincipal());
            returnDetail.put("benifitAmt", lendItemReturn.getInterest());
            returnDetail.put("feeAmt", new BigDecimal(0));

            returnDetails.add(returnDetail);
        }
        return returnDetails;
    }

    @Override
    public List<LendItemReturn> selectLendItemReturnList(Long lendReturnId) {
        QueryWrapper<LendItemReturn> lendItemReturnQueryWrapper = new QueryWrapper<>();
        lendItemReturnQueryWrapper.eq("lend_return_id", lendReturnId);
        return lendItemReturnMapper.selectList(lendItemReturnQueryWrapper);
    }
}
