package com.njydsz.message.domain.converter;

import java.util.List;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;
import com.njydsz.message.domain.entity.batch.MsgAggregate;
import com.njydsz.message.domain.entity.batch.MsgBatch;
import com.njydsz.message.domain.entity.canary.MsgCanary;
import com.njydsz.message.domain.entity.config.MsgFeedback;
import com.njydsz.message.domain.entity.config.MsgPreference;
import com.njydsz.message.domain.entity.config.MsgRouteRule;
import com.njydsz.message.domain.entity.config.MsgSubscription;
import com.njydsz.message.domain.entity.config.MsgUserChannel;
import com.njydsz.message.domain.entity.core.MsgLog;
import com.njydsz.message.domain.entity.core.MsgNotification;
import com.njydsz.message.domain.entity.template.MsgTemplate;
import com.njydsz.message.domain.vo.MsgAggregateVO;
import com.njydsz.message.domain.vo.MsgBatchVO;
import com.njydsz.message.domain.vo.MsgCanaryVO;
import com.njydsz.message.domain.vo.MsgFeedbackVO;
import com.njydsz.message.domain.vo.MsgLogVO;
import com.njydsz.message.domain.vo.MsgNotificationVO;
import com.njydsz.message.domain.vo.MsgPreferenceVO;
import com.njydsz.message.domain.vo.MsgRouteRuleVO;
import com.njydsz.message.domain.vo.MsgSubscriptionVO;
import com.njydsz.message.domain.vo.MsgTemplateVO;
import com.njydsz.message.domain.vo.MsgUserChannelVO;

/**
 * 消息模块统一 MapStruct 转换器。
 *
 * <p>提供所有消息模块 Entity → VO 的转换方法。
 * MpBaseEntity 的自动填充字段（deleted/revision/tenantId/createdBy/createdAt/updatedBy/updatedAt）
 * 在 entityToVO 方向无需 ignore（VO 中不存在的字段自动忽略），仅 DTO→Entity 方向需要 ignore。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface MessageConverter {

    MessageConverter INSTANT = Mappers.getMapper(MessageConverter.class);

    // ===== MsgAggregate =====
    MsgAggregateVO entityToVO(MsgAggregate entity);
    List<MsgAggregateVO> aggregateListToVO(List<MsgAggregate> entities);

    // ===== MsgBatch =====
    MsgBatchVO entityToVO(MsgBatch entity);
    List<MsgBatchVO> batchListToVO(List<MsgBatch> entities);

    // ===== MsgCanary =====
    MsgCanaryVO entityToVO(MsgCanary entity);
    List<MsgCanaryVO> canaryListToVO(List<MsgCanary> entities);

    // ===== MsgLog =====
    MsgLogVO entityToVO(MsgLog entity);
    List<MsgLogVO> logListToVO(List<MsgLog> entities);

    // ===== MsgTemplate =====
    MsgTemplateVO entityToVO(MsgTemplate entity);
    List<MsgTemplateVO> templateListToVO(List<MsgTemplate> entities);

    // ===== MsgNotification =====
    MsgNotificationVO entityToVO(MsgNotification entity);
    List<MsgNotificationVO> notificationListToVO(List<MsgNotification> entities);

    // ===== MsgRouteRule =====
    MsgRouteRuleVO entityToVO(MsgRouteRule entity);
    List<MsgRouteRuleVO> routeRuleListToVO(List<MsgRouteRule> entities);

    // ===== MsgFeedback =====
    MsgFeedbackVO entityToVO(MsgFeedback entity);
    List<MsgFeedbackVO> feedbackListToVO(List<MsgFeedback> entities);

    // ===== MsgPreference =====
    MsgPreferenceVO entityToVO(MsgPreference entity);
    List<MsgPreferenceVO> preferenceListToVO(List<MsgPreference> entities);

    // ===== MsgSubscription =====
    MsgSubscriptionVO entityToVO(MsgSubscription entity);
    List<MsgSubscriptionVO> subscriptionListToVO(List<MsgSubscription> entities);

    // ===== MsgUserChannel =====
    MsgUserChannelVO entityToVO(MsgUserChannel entity);
    List<MsgUserChannelVO> userChannelListToVO(List<MsgUserChannel> entities);
}
