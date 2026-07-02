package com.njydsz.pmis.common;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 公共模块占位启动类（实际为 Library）
 *
 * <p>Common 模块为 Library，不应独立启动。提供此启动类仅为 IDE 友好。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication
public class CommonApplication {

    /**
     * 占位启动入口（实际为 Library，不应独立启动）。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(CommonApplication.class, args);
    }
}
