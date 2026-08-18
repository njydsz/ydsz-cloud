package com.njydsz.message.infra.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

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

/**
 * 消息模块统一 MapStruct 转换器（Infra 层）。
 *
 * <p>提供所有消息模块 DO ↔ VO 的转换方法。
 *
 * <p>DO → VO 方向：DO 的 String 类型字段（如 channel、status 等）直接映射到 VO 的 String 字段。
 * VO → DO 方向：VO 的 String 字段直接映射到 DO 的 String 字段。
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
}
