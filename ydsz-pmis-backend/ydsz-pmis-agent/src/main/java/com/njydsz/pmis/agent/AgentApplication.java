package com.njydsz.pmis.agent;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AI 智能体服务启动类
 *
 * <p>承载风险预警/资源推荐/利润预测/赢率预测/工时异常识别 5 类 Agent。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "com.njydsz.pmis.agent",
        "com.njydsz.pmis.common",
        "com.njydsz.pmis.project"
})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.pmis.agent.feign")
@EnableAsync
@MapperScan("com.njydsz.pmis.agent.mapper")
public class AgentApplication {

    /**
     * 应用入口方法。
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(AgentApplication.class, args);
    }
}
