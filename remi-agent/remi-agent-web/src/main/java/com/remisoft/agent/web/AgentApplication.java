package com.remisoft.agent.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

import com.remisoft.common.audit.annotation.EnableRemiAudit;
import com.remisoft.common.auth.annotation.EnableRemiAuth;
import com.remisoft.common.feign.annotation.EnableRemiFeign;
import com.remisoft.common.safe.annotation.EnableRemiSafe;

/**
 * AI Agent 智能体服务启动类
 *
 * <p>提供 LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理等 AI 能力。
 *
 * @author remi-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.remisoft.agent", "com.remisoft.common"})
@EnableDiscoveryClient
@EnableRemiAuth
@EnableRemiSafe
@EnableRemiAudit
@EnableRemiFeign(basePackages = {"com.remisoft.agent.api", "com.remisoft.common.feign", "com.remisoft.userinfo.api", "com.remisoft.nextwiki.api"})
@MapperScan("com.remisoft.agent.infra.mapper")
public class AgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
