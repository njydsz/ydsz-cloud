package com.njydsz.agent.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;

/**
 * AI Agent 智能体服务启动类
 *
 * <p>提供 LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理等 AI 能力。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.agent", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.agent.api", "com.njydsz.common.feign"})
@MapperScan("com.njydsz.agent.infra.mapper")
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
