package com.njydsz.agent.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.locales.config.EnableYdszI18n;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * AI Agent 智能体服务启动类
 *
 * <p>提供 LLM 对话、Agent 编排、Tool Calling、RAG 知识增强、记忆管理等 AI 能力。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@ComponentScan(basePackages = {"com.njydsz.agent", "com.njydsz.common"}, excludeFilters = @ComponentScan.Filter(type = FilterType.ANNOTATION, classes = org.springframework.boot.autoconfigure.AutoConfiguration.class))
@SpringBootApplication
@EnableDiscoveryClient
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszAudit
@EnableYdszFeign
@EnableYdszI18n
@MapperScan("com.njydsz.agent.infra.mapper")
public class AgentApplication {

  public static void main(String[] args) {
    SpringApplication.run(AgentApplication.class, args);
  }
}
