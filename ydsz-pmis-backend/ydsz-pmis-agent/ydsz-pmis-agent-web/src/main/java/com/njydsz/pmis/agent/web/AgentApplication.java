package com.njydsz.pmis.agent.web;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.njydsz.pmis.common.auth.annotation.EnableYdszAuth;
import com.njydsz.pmis.common.feign.annotation.EnableYdszFeign;

/**
 * AI Agent 智能体服务启动类
 *
 * <p>提供 LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理等 AI 能力。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.agent", "com.njydsz.pmis.common"})
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszFeign(basePackages = {"com.njydsz.pmis.agent.api", "com.njydsz.pmis.common.feign"})
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
