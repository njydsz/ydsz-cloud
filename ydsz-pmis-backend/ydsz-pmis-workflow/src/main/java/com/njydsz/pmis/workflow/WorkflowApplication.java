package com.njydsz.pmis.workflow;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * 工作流服务启动类
 *
 * <p>基于自研 pmis_flow_* 引擎的流程引擎服务（兼容 BPMN 2.0 标准），提供：
 * <ul>
 *   <li>流程定义管理（部署 BPMN XML / 发布 / 停用）</li>
 *   <li>流程实例管理（启动 / 挂起 / 激活 / 终止）</li>
 *   <li>任务管理（待办 / 已办 / 签收 / 完成 / 退回 / 转办 / 委派）</li>
 *   <li>流程业务关联（业务单据 ↔ 流程实例）</li>
 *   <li>事件监听器（项目立项等业务联动）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@SpringBootApplication(scanBasePackages = {"com.njydsz.pmis.workflow", "com.njydsz.pmis.common"})
@EnableDiscoveryClient
@EnableFeignClients(basePackages = "com.njydsz.pmis.workflow.feign")
@MapperScan({"com.njydsz.pmis.workflow.mapper", "com.njydsz.pmis.workflow.flow.mapper"})
public class WorkflowApplication {

    public static void main(String[] args) {
        SpringApplication.run(WorkflowApplication.class, args);
    }
}
