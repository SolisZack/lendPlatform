package com.atguigu.srb.core;

import com.atguigu.srb.core.mapper.LendMapper;
import com.atguigu.srb.core.pojo.entity.Lend;
import lombok.extern.slf4j.Slf4j;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.junit4.SpringRunner;

import javax.annotation.Resource;

@Slf4j
@SpringBootTest
@RunWith(SpringRunner.class)
public class LockTests {
    @Resource
    LendMapper lendMapper;

    @Test
    public void lockTest() {
        Lend lend = lendMapper.selectById(7);
        lend.setTitle("旅游");
        Lend lend2 = lendMapper.selectById(7);
        lend2.setTitle("traveling");
        lendMapper.updateById(lend2);
        lendMapper.updateById(lend);
    }
}
