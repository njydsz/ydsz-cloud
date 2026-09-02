package com.njydsz.workflow.server.service;

import java.util.Map;

import com.njydsz.workflow.domain.dto.FlowRunTaskDTO;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowRunTaskVO;

/**
 * SLA 服务。
 *
 * <p>配置/计算/告警 SLA。
 *
 * @author ydsz-team
 * @since 26.09.01
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
   * @param task 当前任务 VO（必须已 setAssigneeId + dueAt）
   * @return true=已处理或无需处理；false=处理异常
   */
  boolean processOverdue(FlowRunTaskVO task);

  /**
   * 应用 SLA 配置到任务（创建任务时调用，解析 node.slaConfig 设置 dueAt）
   *
   * @param task 待设置 dueAt 的任务 VO
   * @param node 当前节点 VO（slaConfig 字段从 ext 中读取）
   */
  void applySlaConfig(FlowRunTaskDTO task, FlowNodeVO node);

  /**
   * 解析 SLA 配置（同时支持 slaConfig JSON 和 ext.slaConfig 嵌套）
   *
   * @param slaConfigJson slaConfig 字段原始 JSON
   * @return 配置 Map（缺省时返回空 Map）
   */
  Map<String, Object> parseSlaConfig(String slaConfigJson);
}
