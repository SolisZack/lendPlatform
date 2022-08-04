package com.atguigu.srb.core.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/core/test")
public class TestController {

    @Value("${server.port}")
    private String port;

    @GetMapping("/world")
    public String helloWorld() {
        return "hello world";
    }

    @GetMapping("/port")
    public String getPort() {
        return "Curr Port:" + port;
    }

}
