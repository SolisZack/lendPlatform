package com.atguigu.srb.core.util;

import com.atguigu.common.exception.BusinessException;

import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class RedisUtils {
    @Resource
    RedisTemplate<String, Object> redisTemplate;

    public boolean getLock(String clientId, String lockName) {
        //如果获取不到锁，则返回失败
        int tryLockTime = 5;
        for (int i = 0; i < tryLockTime; i++) {
            boolean getLockSuccess = Objects.equals(
                    redisTemplate.opsForValue().setIfAbsent(lockName, clientId, 10, TimeUnit.SECONDS), Boolean.TRUE);
            if (getLockSuccess)
                break;
            if (i == tryLockTime - 1)
                return false;
            try {
                TimeUnit.MILLISECONDS.sleep(200);
            }catch (Exception e) {
                throw new BusinessException("获取redis分布式锁过程中线程休眠异常");
            }

        }

        return true;
    }

    public void releaseLock(String lockName, String clientId) {
        //解锁
        //判断当前客户端id与redis分布式中持有的客户端id一致，才能删除锁
        if(clientId.equals(redisTemplate.opsForValue().get(lockName)))
            redisTemplate.delete(lockName);
    }

    public boolean threadSafeSetValue(String clientId, String key, Object value,
                                      Integer times, TimeUnit unit, String lockName) {
        // 加锁
        boolean locked = this.getLock(clientId, lockName);
        if (!locked)
            return false;

        // 获取到锁, 对数据进行操作
        log.info("将{}设置为{}", key, value);
        redisTemplate.opsForValue().set(key, value, times, unit);

        // 解锁
        this.releaseLock(lockName, clientId);

        return true;
    }

    public boolean threadSafeAddNum(String clientId, String key, BigDecimal value, BigDecimal threshold,
                                    Integer times, TimeUnit unit, String lockName) {
        // 加锁
        boolean locked = this.getLock(clientId, lockName);
        if (!locked)
            return false;

        // 获取到锁, 对数据进行操作
        Object redisVal = redisTemplate.opsForValue().get(key);

        // 如果为空则数据不存在
        if (redisVal == null) {
            log.info("redis中不存在key: {}", key);
            this.releaseLock(lockName, clientId);
            return true;
        }

        // 如果不为空则对数据进行加/减
        BigDecimal count = new BigDecimal(redisVal.toString());
        BigDecimal newCount = count.add(value);
        // 操作后数据不能小于0
        if (newCount.compareTo(new BigDecimal(0)) < 0) {
            this.releaseLock(lockName, clientId);
            throw new BusinessException("修改后金额小于0, 修改失败");
        }
        // 操作后数据不能大于threshold
        if (newCount.compareTo(threshold) > 0) {
            this.releaseLock(lockName, clientId);
            throw new BusinessException("修改后金额大于threshold, 修改失败");
        }
        log.info("将{}从{}修改至{}", key, count, newCount);
        redisTemplate.opsForValue().set(key, newCount, times, unit);

        // 解锁
        this.releaseLock(lockName, clientId);

        return true;
    }
}
