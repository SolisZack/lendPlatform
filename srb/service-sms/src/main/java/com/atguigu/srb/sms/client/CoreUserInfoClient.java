package com.atguigu.srb.sms.client;

//import com.atguigu.srb.sms.client.fallback.CoreUserInfoClientFallback;
import com.atguigu.common.result.R;
import com.atguigu.srb.sms.client.fallback.CoreUserInfoClientFallback;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

// value = service provider name, fallback = fallback.class(sentinel)
@FeignClient(value = "service-core", fallback = CoreUserInfoClientFallback.class)
public interface CoreUserInfoClient {

    @GetMapping("/api/core/userInfo/checkMobile/{mobile}")
    R checkMobile(@PathVariable String mobile);
}
