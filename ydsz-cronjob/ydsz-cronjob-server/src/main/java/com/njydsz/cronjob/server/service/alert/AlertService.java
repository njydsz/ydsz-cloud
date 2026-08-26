package com.njydsz.cronjob.server.service.alert;

import java.time.LocalDateTime;
import java.util.List;

import com.njydsz.cronjob.domain.dto.alert.AlertRuleSaveDTO;
import com.njydsz.cronjob.domain.vo.JobAlertLogVO;
import com.njydsz.cronjob.domain.vo.JobAlertRuleVO;

/**
 * 告警规则 Service
 *
 * <p>为定时任务提供"任务执行异常自动告警"能力。支持多种触发条件(连续失败 N 次、 成功率低于阈值、平均耗时超阈值等)和多种通知通道(站内通知/短信/邮件/企业微信/IM)。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #createRule} / {@link #updateRule} / {@link #deleteRule} / {@link
 *       #getRuleById}
 *   <li><b>查询</b>：{@link #listRules} / {@link #queryAlertLogs}
 *   <li><b>启停</b>：{@link #toggleRule} — 启用/禁用告警规则
 * </ul>
 *
 * <p><b>告警规则：</b>每条规则绑定一个或多个任务 ID,配置触发条件(规则表达式)、 通知通道(支持组合)、接收人(角色/用户/部门)。规则修改不影响历史告警日志。
 *
 * <p><b>告警日志：</b>触发后写入 {@code ydsz_job_alert_dispatch}（P3-1-merge，source_type='CRONJOB'），包含触发时间、条件命中值、通知发送结果。
 *
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see JobService 任务 Service(执行后由告警调度器评估规则)
 */
public interface AlertService {

  /**
   * 创建告警规则。
   *
   * @param dto 规则表单
   * @return 规则 ID
   */
  String createRule(AlertRuleSaveDTO dto);

  /**
   * 更新告警规则。
   *
   * @param id 规则 ID
   * @param dto 规则表单
   */
  void updateRule(String id, AlertRuleSaveDTO dto);

  /**
   * 删除告警规则（逻辑删除）。
   *
   * @param id 规则 ID
   */
  void deleteRule(String id);

  /**
   * 查询规则详情。
   *
   * @param id 规则 ID
   * @return 规则详情 VO
   */
  JobAlertRuleVO getRuleById(String id);

  /**
   * 查询全部告警规则。
   *
   * @return 规则列表
   */
  List<JobAlertRuleVO> listRules();

  /**
   * 启用/禁用规则。
   *
   * @param id 规则 ID
   * @param enabled 0 禁用 / 1 启用
   */
  void toggleRule(String id, Integer enabled);

  /**
   * 查询指定任务的告警历史。
   *
   * @param jobId 任务 ID
   * @param since 时间窗口起点（NULL 表示查询全部）
   * @return 告警日志列表
   */
  List<JobAlertLogVO> queryAlertLogs(String jobId, LocalDateTime since);
}
