package com.tycoon;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 玩家侧服务（wheel）。
 *
 * <p>Dubbo 消费者：调用 user-service 处理账号/积分/奖励/黑名单，
 * 调用 admin-service 获取后台配置的游戏商品。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableDiscoveryClient
@EnableDubbo
@MapperScan("com.tycoon.mapper")
public class TycoonApplication {
    public static void main(String[] args) {
        SpringApplication.run(TycoonApplication.class, args);
    }
}
