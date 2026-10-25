package com.tycoon.admin;

import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 管理后台服务。
 *
 * <ul>
 *   <li>Dubbo 提供者：{@code GameConfigDubboService}（wheel 读取游戏配置）</li>
 *   <li>Dubbo 消费者：{@code UserDubboService}（用户 / 奖励 / 拉黑）</li>
 *   <li>REST + 静态页面：/api/admin/** 与 /admin/**</li>
 * </ul>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableDubbo
@MapperScan("com.tycoon.admin.mapper")
public class AdminApplication {

    public static void main(String[] args) {
        SpringApplication.run(AdminApplication.class, args);
    }
}
