package com.njydsz.workflow.server.service;

import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.domain.entity.FlowRunTask;
import java.util.Map;

/**
 * SLA 服务。
 *
 * <p>配置/计算/告警 SLA。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface FlowSlaService {

  /**
   * 扫描所有到点任务并执行 SLA 策略（cronjob 每 60s 调用一次）
   *
   * @return 实际处理的任务数
   */
  int scanAndProcess();

  /**
   * 处理单条任务的 SLA（外部可主动触发）
   *
   * @param task 当前任务（必须已 setAssigneeId + dueAt）
   * @return true=已处理或无需处理；false=处理异常
   */
  boolean processOverdue(FlowRunTask task);

  /**
   * 应用 SLA 配置到任务（创建任务时调用，解析 node.slaConfig 设置 dueAt）
   *
   * @param task 待设置 dueAt 的任务
   * @param node 当前节点（slaConfig 字段从 ext 中读取）
   */
  void applySlaConfig(FlowRunTask task, FlowNode node);

  /**
   * 解析 SLA 配置（同时支持 slaConfig JSON 和 ext.slaConfig 嵌套）
   *
   * @param slaConfigJson slaConfig 字段原始 JSON
   * @return 配置 Map（缺省时返回空 Map）
   */
  Map<String, Object> parseSlaConfig(String slaConfigJson);
}
