package com.njydsz.userinfo.domain.alert;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.query.SecurityAlertPageQuery;

/**
 * 安全告警仓储接口（领域契约层）。
 *
 * <p>定义安全告警的数据访问能力，实现类位于 {@code ydsz-userinfo-infra} 模块。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface SecurityAlertRepository {

  /**
   * 保存安全告警。
   *
   * @param alert 安全告警聚合根
   * @return 保存后的安全告警（含生成的 ID）
   */
  SecurityAlert save(SecurityAlert alert);

  /**
   * 根据 ID 查询安全告警。
   *
   * @param id 告警 ID
   * @return 安全告警；不存在返回 {@code Optional.empty()}
   */
  Optional<SecurityAlert> findById(String id);

  /**
   * 分页查询安全告警。
   *
   * @param query 分页查询条件（状态/风险等级/时间范围/分页参数）
   * @return 分页结果
   */
  PageResponse<List<SecurityAlert>> page(SecurityAlertPageQuery query);

  /**
   * 统计指定时间范围内指定类型的告警数量（用于告警去重和频率控制）。
   *
   * @param alertType 告警类型
   * @param userId 用户 ID（可为 null）
   * @param sourceIp 来源 IP（可为 null）
   * @param since 起始时间
   * @return 告警数量
   */
  long countRecentAlerts(
      SecurityAlert.AlertType alertType,
      String userId,
      String sourceIp,
      LocalDateTime since);

  /**
   * 更新告警状态。
   *
   * @param id 告警 ID
   * @param status 目标状态
   * @param handlerNote 处理备注
   * @return 更新成功返回 true
   */
  boolean updateStatus(String id, SecurityAlert.AlertStatus status, String handlerNote);

  /**
   * 查询待处理的告警列表。
   *
   * @param riskLevel 风险等级（可为 null 表示不过滤）
   * @param limit 限制数量
   * @return 告警列表
   */
  List<SecurityAlert> findPendingAlerts(SecurityAlert.RiskLevel riskLevel, int limit);
}
