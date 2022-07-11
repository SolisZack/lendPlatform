package com.atguigu.srb.sms.client.fallback;

import com.atguigu.common.result.R;
import com.atguigu.srb.sms.client.CoreUserInfoClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class CoreUserInfoClientFallback implements CoreUserInfoClient {
    @Override
    public R checkMobile(String mobile) {
        log.error("远程调用失败，服务熔断");
        return R.ok().data("isExist", false); //手机号不重复
    }
}
