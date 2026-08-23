package com.njydsz.message.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.message.domain.dto.MsgCanaryDTO;
import com.njydsz.message.domain.dto.MsgLogDTO;
import com.njydsz.message.domain.dto.MsgNotificationDTO;
import com.njydsz.message.domain.dto.MsgTemplateDTO;
import com.njydsz.message.domain.dto.MsgTenantConfigDTO;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.domain.vo.MsgBatchVO;
import com.njydsz.message.domain.vo.MsgCanaryVO;
import com.njydsz.message.domain.vo.MsgFeedbackVO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.domain.vo.MsgOfflineVO;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.domain.vo.MsgReceiptVO;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.domain.vo.MsgTemplateVersionVO;
import com.njydsz.message.domain.vo.MsgTenantConfigVO;
import com.njydsz.message.domain.vo.MsgTraceVO;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.domain.vo.MsgVariableSourceVO;
import com.njydsz.message.infra.entity.MsgAggregateDO;
import com.njydsz.message.infra.entity.MsgBatchDO;
import com.njydsz.message.infra.entity.MsgCanaryDO;
import com.njydsz.message.infra.entity.MsgFeedbackDO;
import com.njydsz.message.infra.entity.MsgLog;
import com.njydsz.message.infra.entity.MsgLogDO;
import com.njydsz.message.infra.entity.MsgNotificationDO;
import com.njydsz.message.infra.entity.MsgOfflineDO;
import com.njydsz.message.infra.entity.MsgPreferenceDO;
import com.njydsz.message.infra.entity.MsgReceiptDO;
import com.njydsz.message.infra.entity.MsgRouteRuleDO;
import com.njydsz.message.infra.entity.MsgSubscriptionDO;
import com.njydsz.message.infra.entity.MsgTemplateDO;
import com.njydsz.message.infra.entity.MsgTemplateVersionDO;
import com.njydsz.message.infra.entity.MsgTenantConfigDO;
import com.njydsz.message.infra.entity.MsgTraceDO;
import com.njydsz.message.infra.entity.MsgUserChannelDO;
import com.njydsz.message.infra.entity.MsgVariableSourceDO;

/**
 * 消息模块统一 MapStruct 转换器（Infra 层）。
 *
 * <p>承担「消息模块」所有 DO ↔ VO ↔ DTO 的双向转换，遵循云顶编码规范的<b>单一转换器</b>模式：
 * 同一业务域的转换规则集中维护，避免散落在各 Service 的 BeanUtils.copyProperties 调用。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>DO → VO 方向：用于查询结果转换</li>
 *   <li>VO → DO 方向：用于 Repository 层将 VO 转换为 DO</li>
 *   <li>DTO → DO 方向：用于 Repository 层将 CUD 入参 DTO 转换为 DO</li>
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MessageConverter {

  /** MapStruct 单例实例 */
  MessageConverter INSTANCE = Mappers.getMapper(MessageConverter.class);

  // ===== MsgLog =====
  MsgLogVO doToVO(MsgLogDO entity);

  List<MsgLogVO> logDoListToVO(List<MsgLogDO> entities);

  /**
   * 消息发送日志 VO → 消息发送日志（领域实体）。
   *
   * <p>用于 Service 层将 Repository 返回的 VO 转换为领域实体，供通道分发等场景使用。
   *
   * @param vo 消息发送日志 VO
   * @return 消息发送日志领域实体
   */
  MsgLog voToLog(MsgLogVO vo);

  // ===== MsgNotification =====
  MsgNotificationVO doToVO(MsgNotificationDO entity);

  List<MsgNotificationVO> notificationDoListToVO(List<MsgNotificationDO> entities);

  // ===== MsgTemplate =====
  MsgTemplateVO doToVO(MsgTemplateDO entity);

  List<MsgTemplateVO> templateDoListToVO(List<MsgTemplateDO> entities);

  // ===== MsgRouteRule =====
  MsgRouteRuleVO doToVO(MsgRouteRuleDO entity);

  List<MsgRouteRuleVO> routeRuleDoListToVO(List<MsgRouteRuleDO> entities);

  // ===== MsgOffline =====
  MsgOfflineVO doToVO(MsgOfflineDO entity);

  List<MsgOfflineVO> offlineDoListToVO(List<MsgOfflineDO> entities);

  // ===== MsgSubscription =====
  MsgSubscriptionVO doToVO(MsgSubscriptionDO entity);

  List<MsgSubscriptionVO> subscriptionDoListToVO(List<MsgSubscriptionDO> entities);

  // ===== MsgPreference =====
  MsgPreferenceVO doToVO(MsgPreferenceDO entity);

  List<MsgPreferenceVO> preferenceDoListToVO(List<MsgPreferenceDO> entities);

  // ===== MsgUserChannel =====
  MsgUserChannelVO doToVO(MsgUserChannelDO entity);

  List<MsgUserChannelVO> userChannelDoListToVO(List<MsgUserChannelDO> entities);

  // ===== MsgTrace =====
  MsgTraceVO doToVO(MsgTraceDO entity);

  List<MsgTraceVO> traceDoListToVO(List<MsgTraceDO> entities);

  // ===== MsgReceipt =====
  MsgReceiptVO doToVO(MsgReceiptDO entity);

  List<MsgReceiptVO> receiptDoListToVO(List<MsgReceiptDO> entities);

  // ===== MsgFeedback =====
  MsgFeedbackVO doToVO(MsgFeedbackDO entity);

  List<MsgFeedbackVO> feedbackDoListToVO(List<MsgFeedbackDO> entities);

  // ===== MsgBatch =====
  MsgBatchVO doToVO(MsgBatchDO entity);

  List<MsgBatchVO> batchDoListToVO(List<MsgBatchDO> entities);

  // ===== MsgAggregate =====
  MsgAggregateVO doToVO(MsgAggregateDO entity);

  List<MsgAggregateVO> aggregateDoListToVO(List<MsgAggregateDO> entities);

  // ===== MsgTemplateVersion =====
  MsgTemplateVersionVO doToVO(MsgTemplateVersionDO entity);

  List<MsgTemplateVersionVO> templateVersionDoListToVO(List<MsgTemplateVersionDO> entities);

  // ===== MsgVariableSource =====
  MsgVariableSourceVO doToVO(MsgVariableSourceDO entity);

  List<MsgVariableSourceVO> variableSourceDoListToVO(List<MsgVariableSourceDO> entities);

  // ===== VO → DO 方向（用于 Repository 层转换） =====

  /**
   * 消息发送日志 VO → 消息发送日志 DO。
   *
   * <p>用于 Repository 层将 VO 转换为 DO 后委托 Mapper 执行数据库操作。
   *
   * @param vo 消息发送日志 VO
   * @return 消息发送日志 DO
   */
  MsgLogDO voToDO(MsgLogVO vo);

  /**
   * 消息发送日志 VO 列表 → 消息发送日志 DO 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param vos 消息发送日志 VO 列表
   * @return 消息发送日志 DO 列表
   */
  List<MsgLogDO> logVoListToDO(List<MsgLogVO> vos);

  // ===== DTO → DO 方向（用于 Repository 层 CUD 操作） =====

  /**
   * 消息发送日志 DTO → 消息发送日志 DO。
   *
   * <p>用于 Repository 层将 CUD 入参 DTO 转换为 DO 后委托 Mapper 执行数据库操作。
   *
   * @param dto 消息发送日志 DTO
   * @return 消息发送日志 DO
   */
  MsgLogDO dtoToDO(MsgLogDTO dto);

  /**
   * 消息发送日志 DTO 列表 → 消息发送日志 DO 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param dtos 消息发送日志 DTO 列表
   * @return 消息发送日志 DO 列表
   */
  List<MsgLogDO> logDtoListToDO(List<MsgLogDTO> dtos);

  /**
   * 站内通知 DTO → 站内通知 DO。
   *
   * <p>用于 Repository 层将 CUD 入参 DTO 转换为 DO 后委托 Mapper 执行数据库操作。
   *
   * @param dto 站内通知 DTO
   * @return 站内通知 DO
   */
  MsgNotificationDO dtoToDO(MsgNotificationDTO dto);

  /**
   * 站内通知 DTO 列表 → 站内通知 DO 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param dtos 站内通知 DTO 列表
   * @return 站内通知 DO 列表
   */
  List<MsgNotificationDO> notificationDtoListToDO(List<MsgNotificationDTO> dtos);

  /**
   * 消息模板 DTO → 消息模板 DO。
   *
   * <p>用于 Repository 层将 CUD 入参 DTO 转换为 DO 后委托 Mapper 执行数据库操作。
   *
   * @param dto 消息模板 DTO
   * @return 消息模板 DO
   */
  MsgTemplateDO dtoToDO(MsgTemplateDTO dto);

  // ===== MsgTenantConfig =====
  MsgTenantConfigVO doToVO(MsgTenantConfigDO entity);

  MsgTenantConfigDO dtoToDO(MsgTenantConfigDTO dto);

  // ===== MsgCanary =====
  MsgCanaryVO doToVO(MsgCanaryDO entity);

  MsgCanaryDO dtoToDO(MsgCanaryDTO dto);

  // ===== 通用类型转换 =====

  /**
   * Boolean → Integer 转换（逻辑删除字段）。
   *
   * <p>领域层 {@code deleted} 使用 Boolean，持久层 {@code deleted} 使用 Integer（0/1）。
   *
   * @param value Boolean 值
   * @return Integer 值（true → 1，false → 0，null → null）
   */
  default Integer mapBooleanToInteger(Boolean value) {
    if (value == null) {
      return null;
    }
    return value ? 1 : 0;
  }

  /**
   * Integer → Boolean 转换（逻辑删除字段）。
   *
   * <p>持久层 {@code deleted} 使用 Integer（0/1），领域层 {@code deleted} 使用 Boolean。
   *
   * @param value Integer 值
   * @return Boolean 值（1 → true，其他 → false，null → null）
   */
  default Boolean mapIntegerToBoolean(Integer value) {
    if (value == null) {
      return null;
    }
    return value == 1;
  }
}
