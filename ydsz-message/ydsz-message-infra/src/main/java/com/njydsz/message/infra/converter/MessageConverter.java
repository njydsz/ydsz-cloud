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
import com.njydsz.message.infra.entity.MsgAggregate;
import com.njydsz.message.infra.entity.MsgBatch;
import com.njydsz.message.infra.entity.MsgCanary;
import com.njydsz.message.infra.entity.MsgFeedback;
import com.njydsz.message.infra.entity.MsgLog;
import com.njydsz.message.infra.entity.MsgNotification;
import com.njydsz.message.infra.entity.MsgOffline;
import com.njydsz.message.infra.entity.MsgPreference;
import com.njydsz.message.infra.entity.MsgReceipt;
import com.njydsz.message.infra.entity.MsgRouteRule;
import com.njydsz.message.infra.entity.MsgSubscription;
import com.njydsz.message.infra.entity.MsgTemplate;
import com.njydsz.message.infra.entity.MsgTemplateVersion;
import com.njydsz.message.infra.entity.MsgTenantConfig;
import com.njydsz.message.infra.entity.MsgTrace;
import com.njydsz.message.infra.entity.MsgUserChannel;
import com.njydsz.message.infra.entity.MsgVariableSource;

/**
 * 消息模块统一 MapStruct 转换器（Infra 层）。
 *
 * <p>承担「消息模块」所有 Entity ↔ VO ↔ DTO 的双向转换，遵循云顶编码规范的<b>单一转换器</b>模式：
 * 同一业务域的转换规则集中维护，避免散落在各 Service 的 BeanUtils.copyProperties 调用。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>Entity → VO 方向：用于查询结果转换</li>
 *   <li>VO → Entity 方向：用于 Repository 层将 VO 转换为 Entity</li>
 *   <li>DTO → Entity 方向：用于 Repository 层将 CUD 入参 DTO 转换为 Entity</li>
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
  MsgLogVO doToVO(MsgLog entity);

  List<MsgLogVO> logListToVO(List<MsgLog> entities);

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
  MsgNotificationVO doToVO(MsgNotification entity);

  List<MsgNotificationVO> notificationListToVO(List<MsgNotification> entities);

  // ===== MsgTemplate =====
  MsgTemplateVO doToVO(MsgTemplate entity);

  List<MsgTemplateVO> templateListToVO(List<MsgTemplate> entities);

  // ===== MsgRouteRule =====
  MsgRouteRuleVO doToVO(MsgRouteRule entity);

  List<MsgRouteRuleVO> routeRuleListToVO(List<MsgRouteRule> entities);

  // ===== MsgOffline =====
  MsgOfflineVO doToVO(MsgOffline entity);

  List<MsgOfflineVO> offlineListToVO(List<MsgOffline> entities);

  // ===== MsgSubscription =====
  MsgSubscriptionVO doToVO(MsgSubscription entity);

  List<MsgSubscriptionVO> subscriptionListToVO(List<MsgSubscription> entities);

  // ===== MsgPreference =====
  MsgPreferenceVO doToVO(MsgPreference entity);

  List<MsgPreferenceVO> preferenceListToVO(List<MsgPreference> entities);

  // ===== MsgUserChannel =====
  MsgUserChannelVO doToVO(MsgUserChannel entity);

  List<MsgUserChannelVO> userChannelListToVO(List<MsgUserChannel> entities);

  // ===== MsgTrace =====
  MsgTraceVO doToVO(MsgTrace entity);

  List<MsgTraceVO> traceListToVO(List<MsgTrace> entities);

  // ===== MsgReceipt =====
  MsgReceiptVO doToVO(MsgReceipt entity);

  List<MsgReceiptVO> receiptListToVO(List<MsgReceipt> entities);

  // ===== MsgFeedback =====
  MsgFeedbackVO doToVO(MsgFeedback entity);

  List<MsgFeedbackVO> feedbackListToVO(List<MsgFeedback> entities);

  // ===== MsgBatch =====
  MsgBatchVO doToVO(MsgBatch entity);

  List<MsgBatchVO> batchListToVO(List<MsgBatch> entities);

  // ===== MsgAggregate =====
  MsgAggregateVO doToVO(MsgAggregate entity);

  List<MsgAggregateVO> aggregateListToVO(List<MsgAggregate> entities);

  // ===== MsgTemplateVersion =====
  MsgTemplateVersionVO doToVO(MsgTemplateVersion entity);

  List<MsgTemplateVersionVO> templateVersionListToVO(List<MsgTemplateVersion> entities);

  // ===== MsgVariableSource =====
  MsgVariableSourceVO doToVO(MsgVariableSource entity);

  List<MsgVariableSourceVO> variableSourceListToVO(List<MsgVariableSource> entities);

  // ===== VO → Entity 方向（用于 Repository 层转换） =====

  /**
   * 消息发送日志 VO → 消息发送日志 Entity。
   *
   * <p>用于 Repository 层将 VO 转换为 Entity 后委托 Mapper 执行数据库操作。
   *
   * @param vo 消息发送日志 VO
   * @return 消息发送日志 Entity
   */
  MsgLog voToEntity(MsgLogVO vo);

  /**
   * 消息发送日志 VO 列表 → 消息发送日志 Entity 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param vos 消息发送日志 VO 列表
   * @return 消息发送日志 Entity 列表
   */
  List<MsgLog> logVoListToEntity(List<MsgLogVO> vos);

  // ===== DTO → Entity 方向（用于 Repository 层 CUD 操作） =====

  /**
   * 消息发送日志 DTO → 消息发送日志 Entity。
   *
   * <p>用于 Repository 层将 CUD 入参 DTO 转换为 Entity 后委托 Mapper 执行数据库操作。
   *
   * @param dto 消息发送日志 DTO
   * @return 消息发送日志 Entity
   */
  MsgLog dtoToEntity(MsgLogDTO dto);

  /**
   * 消息发送日志 DTO 列表 → 消息发送日志 Entity 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param dtos 消息发送日志 DTO 列表
   * @return 消息发送日志 Entity 列表
   */
  List<MsgLog> logDtoListToEntity(List<MsgLogDTO> dtos);

  /**
   * 站内通知 DTO → 站内通知 Entity。
   *
   * <p>用于 Repository 层将 CUD 入参 DTO 转换为 Entity 后委托 Mapper 执行数据库操作。
   *
   * @param dto 站内通知 DTO
   * @return 站内通知 Entity
   */
  MsgNotification dtoToEntity(MsgNotificationDTO dto);

  /**
   * 站内通知 DTO 列表 → 站内通知 Entity 列表。
   *
   * <p>用于 Repository 层批量转换。
   *
   * @param dtos 站内通知 DTO 列表
   * @return 站内通知 Entity 列表
   */
  List<MsgNotification> notificationDtoListToEntity(List<MsgNotificationDTO> dtos);

  /**
   * 消息模板 DTO → 消息模板 Entity。
   *
   * <p>用于 Repository 层将 CUD 入参 DTO 转换为 Entity 后委托 Mapper 执行数据库操作。
   *
   * @param dto 消息模板 DTO
   * @return 消息模板 Entity
   */
  MsgTemplate dtoToEntity(MsgTemplateDTO dto);

  // ===== MsgTenantConfig =====
  MsgTenantConfigVO doToVO(MsgTenantConfig entity);

  MsgTenantConfig dtoToEntity(MsgTenantConfigDTO dto);

  // ===== MsgCanary =====
  MsgCanaryVO doToVO(MsgCanary entity);

  MsgCanary dtoToEntity(MsgCanaryDTO dto);

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
