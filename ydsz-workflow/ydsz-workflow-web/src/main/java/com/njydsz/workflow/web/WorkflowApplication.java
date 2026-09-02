package com.njydsz.workflow.web;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.scheduling.annotation.EnableScheduling;

import com.njydsz.common.audit.annotation.EnableYdszAudit;
import com.njydsz.common.auth.annotation.EnableYdszAuth;
import com.njydsz.common.feign.annotation.EnableYdszFeign;
import com.njydsz.common.safe.annotation.EnableYdszSafe;

/**
 * 工作流服务启动类
 *
 * <p>基于自研 ydsz_flow_* 引擎的流程引擎服务（兼容 BPMN 2.0 标准），提供：
 *
 * <ul>
 *   <li>流程定义管理（部署 BPMN XML / 发布 / 停用）
 *   <li>流程实例管理（启动 / 挂起 / 激活 / 终止）
 *   <li>任务管理（待办 / 已办 / 签收 / 完成 / 退回 / 转办 / 委派）
 *   <li>流程业务关联（业务单据 ↔ 流程实例）
 *   <li>事件监听器（项目立项等业务联动）
 *   <li>P1-2: 中间/边界定时器（@EnableScheduling 启用 @Scheduled 扫描）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@SpringBootApplication(
    scanBasePackages = {"com.njydsz.workflow", "com.njydsz.common"})
@EnableDiscoveryClient
@EnableYdszAudit
@EnableYdszAuth
@EnableYdszSafe
@EnableYdszFeign
// P1-1: 移除 literule Mapper 扫描，跨模块数据访问应通过 Feign 交互而非直接访问 Mapper
@MapperScan({"com.njydsz.workflow.infra.mapper"})
@EnableScheduling
public class WorkflowApplication {

  public static void main(String[] args) {
    SpringApplication.run(WorkflowApplication.class, args);
  }
}
