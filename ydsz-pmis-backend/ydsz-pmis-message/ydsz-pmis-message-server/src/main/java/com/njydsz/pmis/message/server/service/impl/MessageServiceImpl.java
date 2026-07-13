package com.njydsz.pmis.message.server.service.impl.core;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.constant.PmisMessageTopics;
import com.njydsz.pmis.common.constant.SystemConstants;
import com.njydsz.pmis.common.domain.query.PageQuery;
import com.njydsz.pmis.common.core.constant.PageConstants;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.message.server.channel.ChannelRouter;
import com.njydsz.pmis.message.server.config.MessageProperties;
import com.njydsz.pmis.message.server.config.RetryStrategyResolver;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.dto.batch.BatchSendResult;
import com.njydsz.pmis.message.domain.dto.core.MessageLogQueryDTO;
import com.njydsz.pmis.message.domain.dto.core.MessageSendDTO;
import com.njydsz.pmis.message.domain.dto.core.RichMediaContent;
import com.njydsz.pmis.message.domain.entity.canary.MsgCanaryDO;
import com.njydsz.pmis.message.domain.entity.config.MsgPreferenceDO;
import com.njydsz.pmis.message.domain.entity.config.MsgRouteRuleDO;
import com.njydsz.pmis.message.domain.entity.config.MsgTraceDO;
import com.njydsz.pmis.message.domain.entity.core.MsgLogDO;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.domain.enums.core.MessageStatusEnum;
import com.njydsz.pmis.message.domain.enums.receipt.RecallStatusEnum;
import com.njydsz.pmis.message.server.filter.SensitiveWordFilter;
import com.njydsz.pmis.message.infra.mapper.core.MsgLogMapper;
import com.njydsz.pmis.message.server.metric.MessageMetrics;
import com.njydsz.pmis.message.server.service.batch.AggregateService;
import com.njydsz.pmis.message.server.service.canary.CanaryService;
import com.njydsz.pmis.message.server.service.core.DedupService;
import com.njydsz.pmis.message.server.service.core.DeliveryTimeOptimizer;
import com.njydsz.pmis.message.server.service.core.MessageService;
import com.njydsz.pmis.message.server.service.core.MessageTraceService;
import com.njydsz.pmis.message.server.service.config.PreferenceService;
import com.njydsz.pmis.message.server.service.config.UserChannelBindingService;
import com.njydsz.pmis.message.server.service.core.RateLimitService;
import com.njydsz.pmis.message.server.service.config.RouteRuleService;
import com.njydsz.pmis.message.server.service.config.SubscriptionService;
import com.njydsz.pmis.message.server.service.template.TemplateService;
import com.njydsz.pmis.message.server.template.RichMediaRenderer;
import com.njydsz.pmis.message.server.template.TemplateEngine;
import com.njydsz.pmis.message.server.producer.MessageQueueOperations;
import com.njydsz.pmis.message.server.service.impl.ChannelSuppressionEngine;
import com.njydsz.pmis.message.server.service.impl.EmailBounceHandler;
import com.njydsz.pmis.message.server.service.impl.SenderQuotaService;
import com.njydsz.pmis.message.server.metrics.MessageServiceMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.math.BigDecimal;
import java.time.Duration;

/**
 * 消息发送核心编排服务实现。
 *
 * <p>发送流程：通道校验 → 路由 → 灰度(P0-7 差异化) → 订阅校验(P0-5) → 偏好(DND/locale/digest, P0-6) →
 * 去重(P2-1 SET NX EX) → 限流 → 模板加载(偏好 locale) → 渲染 → 落库 PENDING → 通道分发 →
 * 成功 SUCCESS / 失败降级 fallback(P0-4) / 失败重试 RETRY(P0-3) → 频率计数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    /** 通道路由器（负责通道选择与消息分发） */
    private final ChannelRouter channelRouter;
    /** 模板引擎（变量占位符渲染） */
    private final TemplateEngine templateEngine;
    /** 模板管理服务（加载/校验模板） */
    private final TemplateService templateService;
    /** 消息日志 Mapper（落库 / 查询） */
    private final MsgLogMapper msgLogMapper;
    /** 路由规则服务（通道动态路由） */
    private final RouteRuleService routeRuleService;
    /** 限流服务（通道 / 用户 / 模板多维限流） */
    private final RateLimitService rateLimitService;
    /** 灰度服务（A/B 实验命中判断） */
    private final CanaryService canaryService;
    /** 消息模块配置属性 */
    private final MessageProperties messageProperties;
    /** 消息指标采集（Prometheus） */
    private final MessageMetrics messageMetrics;
    /** 订阅管理服务（退订校验） */
    private final SubscriptionService subscriptionService;
    /** 用户偏好服务（DND / locale / 聚合） */
    private final PreferenceService preferenceService;
    /** 消息聚合服务（批量摘要发送） */
    private final AggregateService aggregateService;
    /** 敏感词过滤器 */
    private final SensitiveWordFilter sensitiveWordFilter;
    /** 重试策略解析器（按通道解析最大重试次数与退避间隔） */
    private final RetryStrategyResolver retryStrategyResolver;
    /** 去重服务（Redis SET NX EX 幂等去重） */
    private final DedupService dedupService;
    /** 消息全链路追踪服务 */
    private final MessageTraceService messageTraceService;
    /** 智能推送时间优化器（用户活跃度画像） */
    private final DeliveryTimeOptimizer deliveryTimeOptimizer;
    /** 富媒体内容渲染器（HTML / Markdown / 纯文本） */
    private final RichMediaRenderer richMediaRenderer;
    /** P0-1: 用户通道绑定服务（userId → 通道联系方式解析） */
    private final UserChannelBindingService userChannelBindingService;
    /** P0-3: 模板变量校验器 */
    private final com.njydsz.pmis.message.server.template.TemplateVariableValidator templateVariableValidator;
    /** P0-4: 变量数据源解析器 */
    private final com.njydsz.pmis.message.server.service.config.VariableSourceResolver variableSourceResolver;

    /** P2-3: 消息队列操作（可选,未配置 MQ 时为 null） */
    private final ObjectProvider<MessageQueueOperations> mqProducerProvider;

    /** P2-13: 跨渠道抑制引擎 */
    private final ChannelSuppressionEngine channelSuppressionEngine;
    /** P2-16: 邮件退信处理 */
    private final EmailBounceHandler emailBounceHandler;
    /** P2-20: Sender 配额管理 */
    private final SenderQuotaService senderQuotaService;
    /** P3-22~25: 消息服务可观测性指标 */
    private final MessageServiceMetrics messageServiceMetrics;

    @Override
    public MessageResult send(MessageRequest request) {
        return sendInternal(request, 0);
    }

    /**
     * P2-6: 内部发送方法,携带级联深度。
     *
     * <p>顶层消息 depth=0,级联子消息 depth 递增,超过 {@link MessageConstants#MAX_CASCADE_DEPTH} 跳过。
     * 级联触发时机：父消息 {@code doDispatch} 成功后,遍历 {@link MessageRequest#getCascadeTo()},
     * 为每个子消息设置 {@code parentMsgId = 父 msgId} 后递归调用本方法。
     * 单条级联消息失败不影响其他级联消息(try-catch 吞异常记 WARN)。
     *
     * @param request 消息请求
     * @param depth   级联深度(0=顶层消息)
     */
    private MessageResult sendInternal(MessageRequest request, int depth) {
        if (request == null) {
            return MessageResult.fail(null, "消息请求为空");
        }
        // P2-6: 级联深度保护(防御性,正常路径下 triggerCascade 已提前拦截)
        if (depth > MessageConstants.MAX_CASCADE_DEPTH) {
            log.warn("[Message] 级联深度超限,拒绝发送: depth={} max={} receiver={}",
                    depth, MessageConstants.MAX_CASCADE_DEPTH, request.getReceiver());
            return MessageResult.fail(request.getChannel(), "级联深度超限");
        }
        String channel = request.getChannel();
        if (!StringUtils.hasText(channel)) {
            return MessageResult.fail(null, "消息通道不能为空");
        }
        // ① 通道启用校验
        if (!isChannelEnabled(channel)) {
            log.warn("[Message] 通道未启用: {}", channel);
            return MessageResult.fail(channel, "通道未启用: " + channel);
        }
        // P0-2: 记录接收节点轨迹
        messageTraceService.recordTrace(
                StringUtils.hasText(request.getMessageId()) ? request.getMessageId()
                        : (StringUtils.hasText(request.getBizId()) ? request.getBizId() : "unknown"),
                MsgTraceDO.Node.RECEIVED, "SUCCESS", channel,
                "消息已接收: channel=" + channel + " receiver=" + request.getReceiver());

        // ② 路由（命中则覆盖 channel）
        MsgRouteRuleDO matchedRule = routeRuleService.match(request);
        if (matchedRule != null && StringUtils.hasText(matchedRule.getTargetChannel())) {
            channel = matchedRule.getTargetChannel();
            request.setChannel(channel);
        }
        String receiver = request.getReceiver();
        String bizType = request.getBizType();
        String templateCode = request.getTemplateCode();

        // ①-2 P0-1: 用户通道绑定解析（receiver 是 userId 时自动解析为通道联系方式）
        if (StringUtils.hasText(receiver) && StringUtils.hasText(channel)) {
            String resolved = userChannelBindingService.resolveChannelUserId(receiver, channel);
            if (resolved != null) {
                log.debug("[Message] P0-1 通道绑定解析: userId={} channel={} → channelUserId={}",
                        receiver, channel, resolved);
                request.setReceiver(resolved);
                receiver = resolved;
            }
        }

        // ③ 灰度命中差异化处理（P0-7）：命中后切换实验模板/通道
        int canaryFlag = 0;
        // P1-6: 命中时记录原始 canaryKey(=切换前 templateCode),用于 A/B 报表分组;未命中为 null
        String canaryKeyForLog = null;
        if (StringUtils.hasText(templateCode) && StringUtils.hasText(receiver)) {
            MsgCanaryDO canary = canaryService.matchConfig(templateCode, receiver);
            if (canary != null) {
                canaryFlag = 1;
                canaryKeyForLog = templateCode;
                if (StringUtils.hasText(canary.getExperimentTemplateCode())) {
                    log.info("[Message] 灰度命中切换模板: orig={} exp={}",
                            templateCode, canary.getExperimentTemplateCode());
                    request.setTemplateCode(canary.getExperimentTemplateCode());
                    templateCode = canary.getExperimentTemplateCode();
                }
                if (StringUtils.hasText(canary.getExperimentChannel())) {
                    log.info("[Message] 灰度命中切换通道: orig={} exp={}",
                            channel, canary.getExperimentChannel());
                    channel = canary.getExperimentChannel();
                    request.setChannel(channel);
                }
            }
        }

        // ④ 订阅关系校验（P0-5）：用户退订后不发送
        if (StringUtils.hasText(receiver) && StringUtils.hasText(templateCode)
                && subscriptionService.isBlocked(receiver, templateCode, channel)) {
            log.info("[Message] 用户已退订,跳过发送: receiver={} topic={} channel={}",
                    receiver, templateCode, channel);
            messageMetrics.recordSend(channel, "BLOCKED", 0);
            return MessageResult.fail(channel, "用户已退订该消息");
        }

        // ⑤ 用户偏好（P0-6）：DND 时段 / locale / digestEnabled
        MsgPreferenceDO pref = StringUtils.hasText(receiver)
                ? preferenceService.getByUser(receiver, channel, bizType) : null;
        if (pref != null && isInDndPeriod(pref)) {
            MessageProperties.SmartTimingConfig stc = messageProperties.getSmartTiming();
            boolean channelDisruptive = stc != null && stc.isDisruptive(channel);
            boolean urgentBypass = stc != null && stc.isUrgentBypassDnd()
                    && "URGENT".equals(resolvePriority(request));
            if (!channelDisruptive) {
                // 非打扰型通道（EMAIL/INAPP/Webhook）绕过 DND
                log.debug("[Message] 非打扰型通道绕过 DND: channel={}", channel);
            } else if (urgentBypass) {
                log.info("[Message] URGENT 消息绕过 DND: receiver={} channel={}", receiver, channel);
            } else if (stc != null && stc.isEnabled()) {
                // P2-5: 智能定时 —— 延迟到 DND 结束后发送
                LocalDateTime nextTime = calculateDndEndTime(pref);
                if (nextTime == null) {
                    messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
                    return MessageResult.fail(channel, "当前为免打扰时段");
                }
                long deferHours = Duration.between(LocalDateTime.now(), nextTime).toHours();
                if (deferHours > stc.getMaxDeferHours()) {
                    log.info("[Message] DND 延迟超过阈值,丢弃: receiver={} defer={}h max={}h",
                            receiver, deferHours, stc.getMaxDeferHours());
                    messageMetrics.recordSend(channel, "DND_DROPPED", 0);
                    return MessageResult.fail(channel, "免打扰时段消息延迟过久,已丢弃");
                }
                log.info("[Message] DND 延迟发送: receiver={} dnd={}~{} nextSendAt={}",
                        receiver, pref.getDndStart(), pref.getDndEnd(), nextTime);
                messageMetrics.recordSend(channel, "DND_DEFERRED", 0);
                request.setScheduledAt(nextTime);
            } else {
                // 智能定时未启用,走旧的丢弃策略
                messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
                return MessageResult.fail(channel, "当前为免打扰时段");
            }
        }
        String prefLocale = pref != null ? pref.getLocale() : null;

        // ⑤-2 P2-1: 智能去重（SET NX EX）—— 相同 dedupKey 在 TTL 窗口内仅允许一次
        String dedupKey = buildDedupKey(request);
        if (StringUtils.hasText(dedupKey) && !dedupService.tryAcquire(dedupKey)) {
            log.info("[Message] 检测到重复消息,跳过发送: dedupKey={} receiver={}", dedupKey, receiver);
            messageMetrics.recordSend(channel, "DEDUPED", 0);
            return MessageResult.fail(channel, "消息重复,已忽略");
        }

        // ⑤-3 P2-13: 跨渠道抑制 —— 同一 bizType+bizId+receiver 在抑制窗口内只允许首个渠道发送
        if (StringUtils.hasText(bizType) && StringUtils.hasText(request.getBizId())
                && StringUtils.hasText(receiver)
                && channelSuppressionEngine.shouldSuppress(bizType, request.getBizId(), receiver, channel)) {
            log.info("[Message] 跨渠道抑制,跳过发送: bizType={} bizId={} receiver={} channel={}",
                    bizType, request.getBizId(), receiver, channel);
            messageMetrics.recordSend(channel, "SUPPRESSED", 0);
            return MessageResult.fail(channel, "跨渠道抑制: 已有其他渠道发送");
        }

        // ⑥ 限流 + 频率
        // ⑥-1 通道+bizType 维度令牌桶（全局配额）
        if (!rateLimitService.tryAcquire(buildRateLimitKey(channel, bizType), 1)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new SysException(StandardResultCode.RATE_LIMIT, "发送限流，请稍后重试");
        }
        // ⑥-2 P2-5/P0-5: 多维度令牌桶（receiver/templateCode/tenant），优先级感知
        if (!rateLimitService.checkSendLimit(channel, receiver, templateCode,
                TenantContext.getTenantId(), request.getPriority())) {
            messageMetrics.recordSend(channel, "RATE_LIMITED", 0);
            throw new SysException(StandardResultCode.RATE_LIMIT, "多维度限流：receiver/template/tenant 超限");
        }
        // ⑥-3 用户偏好频率（每日/每小时上限）
        if (StringUtils.hasText(receiver)
                && !rateLimitService.checkFrequency(receiver, channel, bizType)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new SysException(StandardResultCode.RATE_LIMIT, "发送频率超限");
        }

        // ⑥-4 P2-20: Sender 配额管理 —— 检查发送方日/小时配额（以 bizType 作为发送方标识）
        String senderId = StringUtils.hasText(bizType) ? bizType : SystemConstants.SYSTEM_USER_ID;
        if (!senderQuotaService.checkQuota(senderId, channel)) {
            messageMetrics.recordSend(channel, "QUOTA_EXCEEDED", 0);
            throw new SysException(StandardResultCode.RATE_LIMIT, "发送方配额已用尽: senderId=" + senderId);
        }

        // ⑦ 加载模板（有 templateCode 时，使用偏好 locale）
        String content = request.getContent();
        String subject = request.getSubject();
        if (StringUtils.hasText(templateCode)) {
            MsgTemplateDO template = templateService.loadByCodeAndChannel(
                    templateCode, channel, prefLocale, TenantContext.getTenantId());
            if (template == null) {
                return MessageResult.fail(channel, "模板不存在: " + templateCode);
            }
            // P0-3: 模板变量类型校验（有 variableDefs 时校验+填充默认值）
            if (StringUtils.hasText(template.getVariableDefs())) {
                var varDefs = templateVariableValidator.parse(template.getVariableDefs());
                if (!varDefs.isEmpty() && request.getParams() != null) {
                    templateVariableValidator.validateAndFill(request.getParams(), varDefs, templateCode);
                }
            }
            // P0-4: 变量数据源自动拉取（params 中缺失的变量从数据源补全）
            if (request.getParams() != null) {
                Map<String, Object> ctx = new HashMap<>();
                if (StringUtils.hasText(request.getBizId())) {
                    ctx.put("bizId", request.getBizId());
                }
                if (StringUtils.hasText(bizType)) {
                    ctx.put("bizType", bizType);
                }
                ctx.put("receiver", receiver);
                variableSourceResolver.resolveVariables(templateCode, request.getParams(), ctx);
            }
            if (StringUtils.hasText(template.getContent())) {
                content = templateEngine.render(template.getContent(), request.getParams());
            }
            if (!StringUtils.hasText(subject) && StringUtils.hasText(template.getSubject())) {
                subject = templateEngine.render(template.getSubject(), request.getParams());
            }
        }

        // ⑦-2 敏感词过滤（P2-1）：对最终 content 做敏感词替换,无论模板渲染还是直传内容
        if (StringUtils.hasText(content)) {
            content = sensitiveWordFilter.filter(content);
        }

        // P1-2: 富媒体消息渲染 —— 检查 params 中是否包含富媒体内容,按通道渲染
        RichMediaContent richMedia = richMediaRenderer.extractFromParams(request.getParams());
        if (richMedia != null) {
            String renderedContent = switch (channel == null ? "" : channel.toUpperCase()) {
                case "EMAIL" -> richMediaRenderer.renderHtml(richMedia);
                case "INAPP", "DINGTALK", "WECOM", "FEISHU" -> richMediaRenderer.renderMarkdown(richMedia);
                case "SMS" -> richMediaRenderer.renderPlainText(richMedia);
                default -> richMediaRenderer.renderPlainText(richMedia);
            };
            if (StringUtils.hasText(renderedContent)) {
                content = renderedContent;
            }
            if (!StringUtils.hasText(subject) && StringUtils.hasText(richMedia.getTitle())) {
                subject = richMedia.getTitle();
            }
        }

        // ⑧ 落库 PENDING
        MsgLogDO logDO = new MsgLogDO();
        logDO.setChannel(channel);
        logDO.setBizType(bizType);
        logDO.setBizId(request.getBizId());
        logDO.setReceiver(receiver);
        logDO.setTemplateCode(templateCode);
        logDO.setTemplateParams(JsonUtils.toJson(request.getParams()));
        logDO.setContent(content);
        logDO.setStatus(MessageStatusEnum.PENDING.name());
        logDO.setPriority(resolvePriority(request));
        logDO.setSenderId(SystemConstants.SYSTEM_USER_ID);
        logDO.setCanary(canaryFlag);
        logDO.setCanaryKey(canaryKeyForLog);
        logDO.setRecallStatus(RecallStatusEnum.NONE.name());
        logDO.setReceiptStatus("NONE");
        logDO.setRetryCount(0);
        logDO.setTraceId(TraceIdUtil.getOrCreate());
        logDO.setMsgId(StringUtils.hasText(request.getMessageId()) ? request.getMessageId()
                : SnowflakeIdGenerator.nextIdStr());
        logDO.setDedupKey(dedupKey);
        // P2-6: 级联发送时记录父消息 ID,用于追溯级联关系
        logDO.setParentMsgId(request.getParentMsgId());
        // P0-3: 定时发送时间
        logDO.setScheduledAt(request.getScheduledAt());
        if (matchedRule != null) {
            logDO.setRouteRuleId(matchedRule.getId());
        }
        logDO.setTenantId(TenantContext.getTenantId());
        // ⑧-2 P0-3: 定时消息 —— scheduledAt 非空且在未来时,落库 SCHEDULED 不立即发送
        if (request.getScheduledAt() != null
                && request.getScheduledAt().isAfter(LocalDateTime.now())) {
            logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
            msgLogMapper.insert(logDO);
            log.info("[Message] 定时消息已入库: msgId={} scheduledAt={} channel={}",
                    logDO.getMsgId(), logDO.getScheduledAt(), channel);
            return MessageResult.ok(channel, logDO.getMsgId());
        }

        // P1-1: 智能推送时间优化 —— 非紧急且未设置定时时间的消息，使用用户活跃度画像推荐最佳推送时间
        if (request.getScheduledAt() == null && StringUtils.hasText(receiver)
                && !"URGENT".equals(resolvePriority(request))) {
            try {
                LocalDateTime optimalTime = deliveryTimeOptimizer.getOptimalDeliveryTime(receiver, channel);
                if (optimalTime != null && optimalTime.isAfter(LocalDateTime.now().plusMinutes(5))) {
                    request.setScheduledAt(optimalTime);
                    logDO.setScheduledAt(optimalTime);
                    logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
                    msgLogMapper.insert(logDO);
                    messageTraceService.recordTrace(logDO.getMsgId(),
                            MsgTraceDO.Node.SCHEDULED,
                            "SUCCESS", channel, "智能定时: optimalAt=" + optimalTime);
                    log.info("[Message] 智能定时推送: msgId={} receiver={} optimalAt={}",
                            logDO.getMsgId(), receiver, optimalTime);
                    return MessageResult.ok(channel, logDO.getMsgId());
                }
            } catch (Exception e) {
                log.debug("[Message] 智能推送时间优化失败,降级立即发送: receiver={} err={}",
                        receiver, e.getMessage());
            }
        }

        msgLogMapper.insert(logDO);

        // ⑨ 聚合判断（P0-6）：digestEnabled=1 时追加到聚合批次,不立即发送
        if (pref != null && Integer.valueOf(1).equals(pref.getDigestEnabled())
                && StringUtils.hasText(bizType) && StringUtils.hasText(receiver)) {
            aggregateService.appendOrStart(bizType, receiver, channel, logDO.getTenantId());
            logDO.setStatus(MessageStatusEnum.PENDING.name());
            logDO.setErrorMessage("AGGREGATED");
            msgLogMapper.updateById(logDO);
            log.info("[Message] 已加入聚合批次: msgId={} group={} receiver={}",
                    logDO.getMsgId(), bizType, receiver);
            return MessageResult.ok(channel, logDO.getMsgId());
        }

        // P0-2: 记录落库轨迹
        messageTraceService.recordTrace(logDO.getMsgId(),
                MsgTraceDO.Node.PERSISTED, "SUCCESS", channel,
                "消息已落库: status=" + logDO.getStatus());

        // ⑩ 通道分发
        MessageResult result = doDispatch(logDO, matchedRule, receiver);
        // P2-6: 父消息发送成功后触发级联发送(聚合消息不触发级联,由聚合 flush 时自行处理)
        if (result != null && result.isSuccess()) {
            triggerCascade(request, logDO, depth);
        }
        return result;
    }

    /**
     * P2-6: 触发级联发送。
     *
     * <p>遍历 {@code request.getCascadeTo()},为每个子消息设置 {@code parentMsgId = 父 msgId},
     * 递归调用 {@link #sendInternal}。单条级联失败不影响其他级联(try-catch 吞异常记 WARN)。
     * 深度超限时整体跳过并记 WARN。
     *
     * @param request  父消息请求(含 cascadeTo 列表)
     * @param parentLog 父消息落库记录(提供 msgId 作为子消息的 parentMsgId)
     * @param depth    父消息的级联深度
     */
    private void triggerCascade(MessageRequest request, MsgLogDO parentLog, int depth) {
        List<MessageRequest> cascadeTo = request.getCascadeTo();
        if (cascadeTo == null || cascadeTo.isEmpty()) {
            return;
        }
        if (depth + 1 > MessageConstants.MAX_CASCADE_DEPTH) {
            log.warn("[Message] 级联深度超限,跳过全部级联: parentMsgId={} depth={} max={}",
                    parentLog.getMsgId(), depth, MessageConstants.MAX_CASCADE_DEPTH);
            return;
        }
        for (MessageRequest child : cascadeTo) {
            if (child == null) {
                continue;
            }
            child.setParentMsgId(parentLog.getMsgId());
            try {
                sendInternal(child, depth + 1);
            } catch (Exception e) {
                log.warn("[Message] 级联消息发送失败,不影响其他级联: parentMsgId={} err={}",
                        parentLog.getMsgId(), e.getMessage());
            }
        }
    }

    /**
     * 执行通道分发,包含 P0-3 重试落库 与 P0-4 通道降级 / P1-8 多级降级链。
     */
    private MessageResult doDispatch(MsgLogDO logDO, MsgRouteRuleDO matchedRule, String receiver) {
        String channel = logDO.getChannel();
        long start = System.currentTimeMillis();
        try {
            // P2-16: 邮件退信黑名单检查 —— EMAIL 通道发送前校验收信人是否在退信黑名单中
            if ("EMAIL".equalsIgnoreCase(channel) && StringUtils.hasText(receiver)
                    && emailBounceHandler.isBounced(receiver)) {
                String bounceReason = emailBounceHandler.getBounceReason(receiver);
                log.info("[Message] 邮件退信黑名单拦截: msgId={} receiver={} reason={}",
                        logDO.getMsgId(), receiver, bounceReason);
                logDO.setStatus(MessageStatusEnum.SKIPPED.name());
                logDO.setErrorMessage("EMAIL_BOUNCED: " + bounceReason);
                msgLogMapper.updateById(logDO);
                messageMetrics.recordSend(channel, "BOUNCED", 0);
                messageServiceMetrics.recordSendFailure(channel, logDO.getTemplateCode(),
                        logDO.getTenantId(), "EMAIL_BOUNCED");
                return MessageResult.fail(channel, "邮件地址在退信黑名单中: " + receiver);
            }

            logDO.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(logDO);
            // P0-2: 记录分发开始轨迹
            messageTraceService.recordTrace(logDO.getMsgId(),
                    MsgTraceDO.Node.DISPATCH_START,
                    "SUCCESS", channel, "通道分发开始");
            String providerTraceId = channelRouter.dispatch(logDO);
            long cost = System.currentTimeMillis() - start;
            logDO.setStatus(MessageStatusEnum.SUCCESS.name());
            logDO.setProviderTraceId(providerTraceId);
            logDO.setCostMs(cost);
            logDO.setCost(calculateCost(channel));
            msgLogMapper.updateById(logDO);
            if (StringUtils.hasText(receiver)) {
                rateLimitService.recordFrequency(receiver, channel, logDO.getBizType());
            }
            // P2-20: 发送成功后记录配额计数
            senderQuotaService.recordSend(logDO.getSenderId(), channel);
            messageMetrics.recordSend(channel, "SUCCESS", cost);
            // P3-22: 记录发送耗时（Micrometer Timer P50/P90/P99）
            messageServiceMetrics.recordSendDuration(channel, Duration.ofMillis(cost));
            // P3-24: 记录发送成功（Counter）
            messageServiceMetrics.recordSendSuccess(channel, logDO.getTemplateCode(), logDO.getTenantId());
            // P0-2: 记录分发成功轨迹
            messageTraceService.recordTrace(logDO.getMsgId(),
                    MsgTraceDO.Node.DISPATCH_SUCCESS,
                    "SUCCESS", channel, "发送成功: cost=" + cost + "ms");
            log.info("[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms",
                    logDO.getMsgId(), channel, receiver, cost);
            return MessageResult.ok(channel, providerTraceId);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            logDO.setCostMs(cost);
            logDO.setErrorMessage(e.getMessage());
            // P3-24/P3-25: 记录发送失败 + 异常分类
            messageServiceMetrics.recordSendFailure(channel, logDO.getTemplateCode(),
                    logDO.getTenantId(), e.getClass().getSimpleName());
            messageServiceMetrics.recordException(channel, e.getClass().getSimpleName());
            // P0-4 + P1-8: 多级降级链（优先）→ 单通道降级
            List<String> fallbackChannels = resolveFallbackChannels(matchedRule, channel);
            if (!fallbackChannels.isEmpty()) {
                MessageResult fallback = tryFallbackChain(logDO, fallbackChannels, cost);
                if (fallback != null) {
                    return fallback;
                }
            }
            // P0-3 重试落库：retryCount < MAX → RETRY + nextRetryAt,否则 FAILED
            return handleFailure(logDO, e, cost);
        }
    }

    /**
     * P1-8: 解析有序降级通道列表。
     *
     * <p>优先使用 {@link MsgRouteRuleDO#getFallbackChain()}（逗号分隔多通道），
     * 为空时回退到 {@link MsgRouteRuleDO#getFallbackChannel()}（单通道）。
     * 自动过滤空白项与当前通道(避免循环降级)。
     *
     * @param matchedRule    命中的路由规则
     * @param currentChannel 当前发送通道(排除自身)
     * @return 有序降级通道列表（大写），可能为空
     */
    private List<String> resolveFallbackChannels(MsgRouteRuleDO matchedRule, String currentChannel) {
        if (matchedRule == null) {
            return Collections.emptyList();
        }
        String chain = matchedRule.getFallbackChain();
        List<String> result = new ArrayList<>();
        if (StringUtils.hasText(chain)) {
            for (String ch : chain.split(",")) {
                String trimmed = ch == null ? "" : ch.trim();
                if (trimmed.isEmpty()) {
                    continue;
                }
                String upper = trimmed.toUpperCase();
                if (!upper.equalsIgnoreCase(currentChannel) && !result.contains(upper)) {
                    result.add(upper);
                }
            }
        }
        if (result.isEmpty()) {
            String single = matchedRule.getFallbackChannel();
            if (StringUtils.hasText(single)
                    && !single.equalsIgnoreCase(currentChannel)) {
                result.add(single.trim().toUpperCase());
            }
        }
        return result;
    }

    /**
     * P0-4 + P1-8: 按降级链顺序逐个尝试,任一成功即返回。
     *
     * @param logDO            消息日志(会被修改 channel)
     * @param fallbackChannels 有序降级通道列表
     * @param prevCost         前序累计耗时
     * @return 降级成功返回 MessageResult.ok;全部失败返回 null(继续走重试逻辑)
     */
    private MessageResult tryFallbackChain(MsgLogDO logDO, List<String> fallbackChannels, long prevCost) {
        String origChannel = logDO.getChannel();
        long accumulatedCost = prevCost;
        List<String> tried = new ArrayList<>();
        tried.add(origChannel);
        for (String fallbackChannel : fallbackChannels) {
            long start = System.currentTimeMillis();
            try {
                logDO.setStatus(MessageStatusEnum.SENDING.name());
                logDO.setChannel(fallbackChannel);
                msgLogMapper.updateById(logDO);
                String providerTraceId = channelRouter.dispatch(logDO);
                long cost = System.currentTimeMillis() - start;
                logDO.setStatus(MessageStatusEnum.SUCCESS.name());
                logDO.setProviderTraceId(providerTraceId);
                logDO.setCostMs(accumulatedCost + cost);
                logDO.setCost(calculateCost(fallbackChannel));
                msgLogMapper.updateById(logDO);
                messageMetrics.recordSend(fallbackChannel, "SUCCESS", cost);
                log.info("[Message] 降级发送成功: msgId={} chain={} final={} cost={}ms",
                        logDO.getMsgId(), tried, fallbackChannel, cost);
                return MessageResult.ok(fallbackChannel, providerTraceId);
            } catch (Exception fe) {
                long cost = System.currentTimeMillis() - start;
                accumulatedCost += cost;
                tried.add(fallbackChannel);
                log.warn("[Message] 降级发送失败: msgId={} fallback={} err={} 继续尝试下一通道",
                        logDO.getMsgId(), fallbackChannel, fe.getMessage());
            }
        }
        // 全部降级失败,恢复原 channel,继续走重试逻辑
        logDO.setChannel(origChannel);
        logDO.setErrorMessage(String.join("→", tried) + " 均失败");
        return null;
    }

    /**
     * P0-3 失败处理：retryCount < MAX → RETRY + nextRetryAt(指数退避),否则 FAILED。
     *
     * <p>P1-7: 重试次数与退避由 {@link RetryStrategyResolver} 按通道解析,替代硬编码常量。
     */
    private MessageResult handleFailure(MsgLogDO logDO, Exception e, long cost) {
        int retryCount = logDO.getRetryCount() == null ? 0 : logDO.getRetryCount();
        if (!retryStrategyResolver.isMaxRetriesReached(retryCount, logDO.getChannel())) {
            logDO.setStatus(MessageStatusEnum.RETRY.name());
            logDO.setNextRetryAt(retryStrategyResolver.calcNextRetryAt(retryCount, logDO.getChannel()));
            msgLogMapper.updateById(logDO);
            messageMetrics.recordRetry(logDO.getChannel());
            log.warn("[Message] 发送失败转重试: msgId={} channel={} retryCount={} nextRetryAt={} err={}",
                    logDO.getMsgId(), logDO.getChannel(), retryCount, logDO.getNextRetryAt(), e.getMessage());
            return MessageResult.fail(logDO.getChannel(), "发送失败,已加入重试队列: " + e.getMessage());
        }
        logDO.setStatus(MessageStatusEnum.FAILED.name());
        msgLogMapper.updateById(logDO);
        messageMetrics.recordSend(logDO.getChannel(), "FAILED", cost);
        log.error("[Message] 发送失败(重试耗尽): msgId={} channel={} retryCount={} err={}",
                logDO.getMsgId(), logDO.getChannel(), retryCount, e.getMessage());
        return MessageResult.fail(logDO.getChannel(), e.getMessage());
    }

    @Override
    public MessageResult sendDirect(MessageSendDTO dto) {
        if (dto == null) {
            return MessageResult.fail(null, "发送参数为空");
        }
        MessageRequest request = new MessageRequest();
        request.setChannel(dto.getChannel());
        request.setTemplateCode(dto.getTemplateCode());
        request.setReceiver(dto.getReceiver());
        request.setParams(dto.getParams());
        request.setContent(dto.getContent());
        request.setSubject(dto.getSubject());
        request.setBizType(dto.getBizType());
        request.setBizId(dto.getBizId());
        request.setMessageId(dto.getMessageId());
        return send(request);
    }

    @Override
    public BatchSendResult batchSend(List<MessageRequest> requests, String batchId) {
        BatchSendResult result = new BatchSendResult(batchId, 0, 0, 0, 0);
        if (requests == null || requests.isEmpty() || !StringUtils.hasText(batchId)) {
            return result;
        }
        // 限制单批最大 100 条,防止阻塞过久
        int limit = Math.min(requests.size(), MessageConstants.BATCH_SEND_MAX_SIZE);
        result.setTotal(limit);
        for (int i = 0; i < limit; i++) {
            MessageRequest req = requests.get(i);
            if (req == null) {
                result.incSkipped();
                continue;
            }
            // 统一设置 bizId = batchId 便于进度查询
            req.setBizId(batchId);
            try {
                MessageResult r = send(req);
                if (r != null && r.isSuccess()) {
                    result.incSuccess();
                } else {
                    result.incFailed();
                }
            } catch (Exception e) {
                log.warn("[Message] 批量发送单条失败: batchId={} idx={} err={}",
                        batchId, i, e.getMessage());
                result.incFailed();
            }
        }
        log.info("[Message] 批量发送完成: batchId={} total={} success={} failed={} skipped={}",
                batchId, result.getTotal(), result.getSuccess(), result.getFailed(), result.getSkipped());
        return result;
    }

    @Override
    public Page<MsgLogDO> pageLog(MessageLogQueryDTO query) {
        Page<MsgLogDO> page = new Page<>(
                query == null ? 1 : query.getPageNum(),
                Math.min(query == null ? 10 : query.getPageSize(), PageConstants.MAX_PAGE_SIZE));
        LambdaQueryWrapper<MsgLogDO> w = new LambdaQueryWrapper<>();
        if (query != null) {
            w.eq(StringUtils.hasText(query.getChannel()), MsgLogDO::getChannel, query.getChannel());
            w.eq(StringUtils.hasText(query.getBizType()), MsgLogDO::getBizType, query.getBizType());
            w.eq(StringUtils.hasText(query.getBizId()), MsgLogDO::getBizId, query.getBizId());
            w.eq(StringUtils.hasText(query.getStatus()), MsgLogDO::getStatus, query.getStatus());
            w.eq(StringUtils.hasText(query.getReceiver()), MsgLogDO::getReceiver, query.getReceiver());
            w.eq(StringUtils.hasText(query.getPriority()), MsgLogDO::getPriority, query.getPriority());
            w.eq(StringUtils.hasText(query.getRecallStatus()), MsgLogDO::getRecallStatus, query.getRecallStatus());
            w.eq(StringUtils.hasText(query.getTenantId()), MsgLogDO::getTenantId, query.getTenantId());
            // P2-13: 全文搜索（模糊匹配 content / receiver / templateCode）
            if (StringUtils.hasText(query.getKeyword())) {
                String kw = query.getKeyword().trim();
                w.and(wrapper -> wrapper
                        .like(MsgLogDO::getContent, kw)
                        .or().like(MsgLogDO::getReceiver, kw)
                        .or().like(MsgLogDO::getTemplateCode, kw)
                        .or().like(MsgLogDO::getMsgId, kw)
                        .or().like(MsgLogDO::getBizId, kw));
            }
            // P2-13: 时间范围
            if (StringUtils.hasText(query.getStartTime())) {
                w.ge(MsgLogDO::getCreatedAt, LocalDateTime.parse(query.getStartTime()));
            }
            if (StringUtils.hasText(query.getEndTime())) {
                w.le(MsgLogDO::getCreatedAt, LocalDateTime.parse(query.getEndTime()));
            }
        }
        w.orderByDesc(MsgLogDO::getCreatedAt);
        return msgLogMapper.selectPage(page, w);
    }

    /**
     * 判断通道是否启用：优先 ChannelRouter，回退 MessageProperties.channelEnabled。
     */
    private boolean isChannelEnabled(String channel) {
        try {
            if (channelRouter != null && !channelRouter.isChannelEnabled(channel)) {
                return false;
            }
        } catch (Exception e) {
            log.debug("[Message] ChannelRouter 判断异常,回退配置: {}", e.getMessage());
        }
        try {
            Map<String, Boolean> enabled = messageProperties.getChannelEnabled();
            if (enabled != null && enabled.containsKey(channel)) {
                return Boolean.TRUE.equals(enabled.get(channel));
            }
        } catch (Exception e) {
            log.debug("[Message] channelEnabled 配置读取异常: {}", e.getMessage());
        }
        return true;
    }

    /**
     * 判断当前是否在 DND 免打扰时段（P0-6）。
     * 支持跨天时段(如 22:00-08:00)。
     */
    private boolean isInDndPeriod(MsgPreferenceDO pref) {
        if (pref == null || !Integer.valueOf(1).equals(pref.getDndEnabled())) {
            return false;
        }
        String start = pref.getDndStart();
        String end = pref.getDndEnd();
        if (!StringUtils.hasText(start) || !StringUtils.hasText(end)) {
            return false;
        }
        try {
            LocalTime now = LocalTime.now();
            LocalTime s = LocalTime.parse(start);
            LocalTime e = LocalTime.parse(end);
            if (s.isBefore(e)) {
                // 同日时段(如 09:00-18:00)
                return !now.isBefore(s) && now.isBefore(e);
            } else {
                // 跨天时段(如 22:00-08:00)
                return !now.isBefore(s) || now.isBefore(e);
            }
        } catch (Exception ex) {
            log.warn("[Message] DND 时段解析失败: start={} end={} err={}",
                    start, end, ex.getMessage());
            return false;
        }
    }

    /**
     * P2-5: 计算免打扰时段的结束时间（即下次可发送时间，不含 buffer）。
     *
     * <p>支持跨天时段（如 22:00-08:00）：
     * <ul>
     *   <li>同日 DND（09:00-18:00）：结束时间为当天 end</li>
     *   <li>跨天 DND（22:00-08:00），当前在 start 之后：结束时间为次日 end</li>
     *   <li>跨天 DND（22:00-08:00），当前在 end 之前：结束时间为当天 end</li>
     * </ul>
     *
     * @param pref 偏好配置（须已确认在 DND 时段内）
     * @return DND 结束时间 + buffer，解析失败返回 null
     */
    private LocalDateTime calculateDndEndTime(MsgPreferenceDO pref) {
        if (pref == null) {
            return null;
        }
        String startStr = pref.getDndStart();
        String endStr = pref.getDndEnd();
        if (!StringUtils.hasText(startStr) || !StringUtils.hasText(endStr)) {
            return null;
        }
        try {
            LocalTime now = LocalTime.now();
            LocalTime start = LocalTime.parse(startStr);
            LocalTime end = LocalTime.parse(endStr);
            LocalDateTime todayEnd = LocalDateTime.now().toLocalDate().atTime(end);
            LocalDateTime nextEnd;
            if (start.isBefore(end)) {
                // 同日 DND（如 09:00-18:00）：结束时间为当天 end
                nextEnd = todayEnd;
            } else {
                // 跨天 DND（如 22:00-08:00）
                if (now.isBefore(end)) {
                    // 当前在 end 之前（凌晨段）：结束时间为当天 end
                    nextEnd = todayEnd;
                } else {
                    // 当前在 start 之后（夜晚段）：结束时间为次日 end
                    nextEnd = todayEnd.plusDays(1);
                }
            }
            // 附加 buffer 避免卡在 DND 结束瞬间的高峰
            MessageProperties.SmartTimingConfig stc = messageProperties.getSmartTiming();
            long buffer = (stc != null) ? stc.getDndBufferSeconds() : 0L;
            return nextEnd.plusSeconds(buffer);
        } catch (Exception e) {
            log.warn("[Message] DND 结束时间计算失败: start={} end={} err={}",
                    startStr, endStr, e.getMessage());
            return null;
        }
    }

    private String resolvePriority() {
        try {
            String p = messageProperties.getDefaultPriority();
            return StringUtils.hasText(p) ? p : "NORMAL";
        } catch (Exception e) {
            return "NORMAL";
        }
    }

    /**
     * P0-3: 解析发送优先级,优先使用请求中的 priority,回退全局配置。
     */
    private String resolvePriority(MessageRequest request) {
        if (request != null && StringUtils.hasText(request.getPriority())) {
            return request.getPriority().trim().toUpperCase();
        }
        return resolvePriority();
    }

    private String buildRateLimitKey(String channel, String bizType) {
        return (channel == null ? "unknown" : channel) + ":" + (bizType == null ? "default" : bizType);
    }

    /**
     * P2-4: 按通道计算单条消息成本。
     *
     * @param channel 通道
     * @return 单条成本（元），未配置或关闭时返回 ZERO
     */
    private BigDecimal calculateCost(String channel) {
        MessageProperties.CostConfig cfg = messageProperties.getCost();
        if (cfg == null || !cfg.isEnabled() || cfg.getUnitPrices() == null) {
            return BigDecimal.ZERO;
        }
        return cfg.getUnitPrices().getOrDefault(channel, BigDecimal.ZERO);
    }

    private String buildDedupKey(MessageRequest request) {
        if (StringUtils.hasText(request.getMessageId())) {
            return request.getMessageId();
        }
        if (StringUtils.hasText(request.getBizType()) && StringUtils.hasText(request.getBizId())
                && StringUtils.hasText(request.getTemplateCode()) && StringUtils.hasText(request.getReceiver())) {
            return request.getBizType() + ":" + request.getBizId() + ":"
                    + request.getTemplateCode() + ":" + request.getReceiver();
        }
        return null;
    }

    /**
     * P2-3: 事务消息发送。
     *
     * <p>通过 RocketMQ 半消息机制,确保通知请求仅在本地事务校验（通道/模板有效性）通过后才投递。
     * 半消息发送后由 {@link com.njydsz.pmis.message.server.producer.MessageTransactionListener}
     * 执行校验,COMMIT 后消费端异步调用 {@link #send} 完成实际发送。
     *
     * <p>降级策略：未配置 RocketMQ 时直接走同步 {@link #send}。
     *
     * @param request 消息发送请求
     * @return 发送结果
     */
    @Override
    public MessageResult sendTransactionally(MessageRequest request) {
        if (request == null) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "消息请求不能为空");
        }
        MessageQueueOperations mqProducer = mqProducerProvider.getIfAvailable();
        if (mqProducer == null) {
            log.warn("[Message] MQ 未配置,事务消息降级为同步发送: channel={}", request.getChannel());
            return send(request);
        }
        try {
            String msgId = mqProducer.sendTransactionMessage(request);
            log.info("[Message] 事务消息半消息已提交: messageId={} msgId={} channel={}",
                    request.getMessageId(), msgId, request.getChannel());
            return MessageResult.ok(request.getChannel(), msgId);
        } catch (Exception e) {
            log.error("[Message] 事务消息发送失败,降级同步发送: channel={} err={}",
                    request.getChannel(), e.getMessage());
            return send(request);
        }
    }

    /**
     * P0-3: 异步发送消息（先落库 PENDING → 再投递 MQ）。
     *
     * <p>可靠性保证流程：
     * <ol>
     *   <li>生成 messageId（雪花 ID）</li>
     *   <li>落库 PENDING 记录（DB 是 Source of Truth）</li>
     *   <li>投递到 MQ，消费端处理后更新状态</li>
     *   <li>MQ 投递失败 → 落库 PENDING 记录仍存在，由恢复扫描器补偿</li>
     * </ol>
     *
     * @param request 消息发送请求
     * @return 发送结果
     */
    @Override
    public MessageResult sendAsync(MessageRequest request) {
        if (request == null) {
            return MessageResult.fail(null, "消息请求为空");
        }
        // 确保有 messageId
        if (!StringUtils.hasText(request.getMessageId())) {
            request.setMessageId(SnowflakeIdGenerator.nextIdStr());
        }
        // ① 先落库 PENDING（DB 是 Source of Truth）
        MsgLogDO logDO = new MsgLogDO();
        logDO.setMsgId(request.getMessageId());
        logDO.setChannel(request.getChannel());
        logDO.setBizType(request.getBizType());
        logDO.setBizId(request.getBizId());
        logDO.setReceiver(request.getReceiver());
        logDO.setTemplateCode(request.getTemplateCode());
        logDO.setContent(request.getContent());
        logDO.setStatus(MessageStatusEnum.PENDING.name());
        logDO.setPriority(resolvePriority(request));
        logDO.setRetryCount(0);
        logDO.setReceiptStatus("NONE");
        logDO.setRecallStatus(RecallStatusEnum.NONE.name());
        logDO.setTraceId(TraceIdUtil.getOrCreate());
        logDO.setSenderId(SystemConstants.SYSTEM_USER_ID);
        logDO.setTenantId(TenantContext.getTenantId());
        logDO.setTopic(PmisMessageTopics.TOPIC_MESSAGE);
        try {
            msgLogMapper.insert(logDO);
            log.info("[Message] 异步消息已落库 PENDING: msgId={} channel={}", logDO.getMsgId(), request.getChannel());
        } catch (Exception e) {
            log.error("[Message] 异步消息落库失败,降级直接投递 MQ: msgId={} err={}",
                    request.getMessageId(), e.getMessage());
        }
        // ② 投递到 MQ
        MessageQueueOperations mqOps = mqProducerProvider.getIfAvailable();
        if (mqOps == null) {
            // MQ 未配置，降级为同步发送
            log.warn("[Message] MQ 未配置,异步消息降级同步发送: msgId={}", request.getMessageId());
            return send(request);
        }
        try {
            mqOps.asyncSend(request);
            log.info("[Message] 异步消息已投递 MQ: msgId={} channel={}", request.getMessageId(), request.getChannel());
            return MessageResult.ok(request.getChannel(), request.getMessageId());
        } catch (Exception e) {
            log.error("[Message] 异步投递 MQ 失败,降级同步发送: msgId={} err={}",
                    request.getMessageId(), e.getMessage());
            return send(request);
        }
    }
}
