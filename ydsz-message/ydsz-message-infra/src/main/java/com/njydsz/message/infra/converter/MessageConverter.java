package com.njydsz.message.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.message.infra.entity.MsgLog;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.domain.vo.MsgBatchVO;
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
import com.njydsz.message.domain.vo.MsgTraceVO;
import com.njydsz.message.domain.vo.MsgUserChannelVO;
import com.njydsz.message.domain.vo.MsgVariableSourceVO;
import com.njydsz.message.infra.entity.MsgAggregateDO;
import com.njydsz.message.infra.entity.MsgBatchDO;
import com.njydsz.message.infra.entity.MsgFeedbackDO;
import com.njydsz.message.infra.entity.MsgLogDO;
import com.njydsz.message.infra.entity.MsgNotificationDO;
import com.njydsz.message.infra.entity.MsgOfflineDO;
import com.njydsz.message.infra.entity.MsgPreferenceDO;
import com.njydsz.message.infra.entity.MsgReceiptDO;
import com.njydsz.message.infra.entity.MsgRouteRuleDO;
import com.njydsz.message.infra.entity.MsgSubscriptionDO;
import com.njydsz.message.infra.entity.MsgTemplateDO;
import com.njydsz.message.infra.entity.MsgTemplateVersionDO;
import com.njydsz.message.infra.entity.MsgTraceDO;
import com.njydsz.message.infra.entity.MsgUserChannelDO;
import com.njydsz.message.infra.entity.MsgVariableSourceDO;

/**
 * 消息模块统一 MapStruct 转换器（Infra 层）。
 *
 * <p>承担「消息模块」所有 Entity ↔ VO ↔ DTO 的双向转换，遵循云顶编码规范的<b>单一转换器</b>模式：
 * 同一业务域的转换规则集中维护，避免散落在各 Service 的 BeanUtils.copyProperties 调用。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>DO → VO 方向：DO 的 String 类型字段（如 channel、status 等）直接映射到 VO 的 String 字段
 *   <li>VO → DO 方向：VO 的 String 字段直接映射到 DO 的 String 字段
 *   <li>使用 MapStruct 注解处理器，编译期生成实现类，性能优于反射
 *   <li>通过 {@link #INSTANCE} 单例访问，零依赖注入，开箱即用
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MessageConverter {

  MessageConverter INSTANCE = Mappers.getMapper(MessageConverter.class);

  // ===== MsgLog =====
  MsgLogVO doToVO(MsgLogDO entity);

  List<MsgLogVO> logDoListToVO(List<MsgLogDO> entities);

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

  // ===== 领域实体 → DO 方向（用于 Repository 层转换） =====

  /**
   * 消息发送日志领域实体 → 消息发送日志 DO。
   *
   * <p>用于 Repository 层将领域实体转换为 DO 后委托 Mapper 执行数据库操作。
   * 枚举类型字段（如 status、channel）通过 {@code name()} 方法转换为 String。
   *
   * @param entity 消息发送日志领域实体
   * @return 消息发送日志 DO
   */
  MsgLogDO entityToDO(MsgLog entity);

  /**
   * 消息发送日志领域实体列表 → 消息发送日志 DO 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param entities 消息发送日志领域实体列表
   * @return 消息发送日志 DO 列表
   */
  List<MsgLogDO> logEntityListToDO(List<MsgLog> entities);

  // ===== DO → 领域实体 方向（用于 Repository 层查询结果转换） =====

  /**
   * 消息发送日志 DO → 消息发送日志领域实体。
   *
   * <p>用于 Repository 层将 DO 转换为领域实体后返回给 Server 层。
   * String 类型字段通过枚举 {@code valueOf()} 方法转换为枚举类型。
   *
   * @param entity 消息发送日志 DO
   * @return 消息发送日志领域实体
   */
  MsgLog doToEntity(MsgLogDO entity);

  /**
   * 消息发送日志 DO 列表 → 消息发送日志领域实体列表。
   *
   * @param entities 消息发送日志 DO 列表
   * @return 消息发送日志领域实体列表
   */
  List<MsgLog> logDoListToEntity(List<MsgLogDO> entities);
}
