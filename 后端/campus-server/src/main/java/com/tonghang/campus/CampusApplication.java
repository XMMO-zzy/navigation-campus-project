package com.tonghang.campus;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 通航校园 · 后端启动类。
 *
 * <p>架构基线：《11-系统概要设计文档》§1.1「Spring Boot 3.2 模块化单体（按 M1~M7 分包）」。
 * <p>接口前缀：{@code /api/v1}（见《13》§1），由 application.yml 的
 * {@code server.servlet.context-path} 统一配置。
 */
@SpringBootApplication(scanBasePackages = "com.tonghang.campus")
@MapperScan("com.tonghang.campus.**.mapper")
@EnableScheduling
public class CampusApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusApplication.class, args);
    }
}
