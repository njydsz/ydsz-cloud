package com.njydsz.pmis.execution;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 项目执行与成本利润服务启动类
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {
        "com.njydsz.pmis.execution",
        "com.njydsz.pmis.common"
})
@EnableFeignClients(basePackages = "com.njydsz.pmis.execution.feign")
@MapperScan("com.njydsz.pmis.execution.mapper")
public class ExecutionApplication {

    /**
     * 服务启动入口
     *
     * @param args 启动参数
     */
    public static void main(String[] args) {
        SpringApplication.run(ExecutionApplication.class, args);
    }
}
