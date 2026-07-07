package com.njydsz.pmis.message.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.constant.SystemConstants;
import com.njydsz.pmis.common.entity.PageQuery;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.JsonUtils;
import com.njydsz.pmis.common.util.SnowflakeIdGenerator;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.message.channel.ChannelRouter;
import com.njydsz.pmis.message.config.MessageProperties;
import com.njydsz.pmis.message.constant.MessageConstants;
import com.njydsz.pmis.message.dto.BatchSendResult;
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgCanaryDO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgPreferenceDO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.AggregateService;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalTime;
import java.util.List;
import java.util.Map;

/**
 * 消息发送核心编排服务实现。
 *
 * <p>发送流程：通道校验 → 路由 → 灰度(P0-7 差异化) → 订阅校验(P0-5) → 偏好(DND/locale/digest, P0-6) →
 * 限流 → 模板加载(偏好 locale) → 渲染 → 落库 PENDING → 通道分发 →
 * 成功 SUCCESS / 失败降级 fallback(P0-4) / 失败重试 RETRY(P0-3) → 频率计数。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final ChannelRouter channelRouter;
    private final TemplateEngine templateEngine;
    private final TemplateService templateService;
    private final MsgLogMapper msgLogMapper;
    private final RouteRuleService routeRuleService;
    private final RateLimitService rateLimitService;
    private final CanaryService canaryService;
    private final MessageProperties messageProperties;
    private final MessageMetrics messageMetrics;
    private final SubscriptionService subscriptionService;
    private final PreferenceService preferenceService;
    private final AggregateService aggregateService;

    @Override
    public MessageResult send(MessageRequest request) {
        if (request == null) {
            return MessageResult.fail(null, "消息请求为空");
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
        // ② 路由（命中则覆盖 channel）
        MsgRouteRuleDO matchedRule = routeRuleService.match(request);
        if (matchedRule != null && StringUtils.hasText(matchedRule.getTargetChannel())) {
            channel = matchedRule.getTargetChannel();
            request.setChannel(channel);
        }
        String receiver = request.getReceiver();
        String bizType = request.getBizType();
        String templateCode = request.getTemplateCode();

        // ③ 灰度命中差异化处理（P0-7）：命中后切换实验模板/通道
        int canaryFlag = 0;
        if (StringUtils.hasText(templateCode) && StringUtils.hasText(receiver)) {
            MsgCanaryDO canary = canaryService.matchConfig(templateCode, receiver);
            if (canary != null) {
                canaryFlag = 1;
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
            log.info("[Message] 命中免打扰时段,跳过发送: receiver={} dnd={}~{}",
                    receiver, pref.getDndStart(), pref.getDndEnd());
            messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
            return MessageResult.fail(channel, "当前为免打扰时段");
        }
        String prefLocale = pref != null ? pref.getLocale() : null;

        // ⑥ 限流 + 频率
        if (!rateLimitService.tryAcquire(buildRateLimitKey(channel, bizType), 1)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new BizException(BizErrorCode.RATE_LIMIT, "发送限流，请稍后重试");
        }
        if (StringUtils.hasText(receiver)
                && !rateLimitService.checkFrequency(receiver, channel, bizType)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new BizException(BizErrorCode.RATE_LIMIT, "发送频率超限");
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
            if (StringUtils.hasText(template.getContent())) {
                content = templateEngine.render(template.getContent(), request.getParams());
            }
            if (!StringUtils.hasText(subject) && StringUtils.hasText(template.getSubject())) {
                subject = templateEngine.render(template.getSubject(), request.getParams());
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
        logDO.setPriority(resolvePriority());
        logDO.setSenderId(SystemConstants.SYSTEM_USER_ID);
        logDO.setCanary(canaryFlag);
        logDO.setRecallStatus(RecallStatusEnum.NONE.name());
        logDO.setReceiptStatus("NONE");
        logDO.setRetryCount(0);
        logDO.setTraceId(TraceIdUtil.get());
        logDO.setMsgId(StringUtils.hasText(request.getMessageId()) ? request.getMessageId()
                : SnowflakeIdGenerator.nextIdStr());
        logDO.setDedupKey(buildDedupKey(request));
        if (matchedRule != null) {
            logDO.setRouteRuleId(matchedRule.getId());
        }
        logDO.setTenantId(TenantContext.getTenantId());
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

        // ⑩ 通道分发
        return doDispatch(logDO, matchedRule, receiver);
    }

    /**
     * 执行通道分发,包含 P0-3 重试落库 与 P0-4 通道降级。
     */
    private MessageResult doDispatch(MsgLogDO logDO, MsgRouteRuleDO matchedRule, String receiver) {
        String channel = logDO.getChannel();
        long start = System.currentTimeMillis();
        try {
            logDO.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(logDO);
            String providerTraceId = channelRouter.dispatch(logDO);
            long cost = System.currentTimeMillis() - start;
            logDO.setStatus(MessageStatusEnum.SUCCESS.name());
            logDO.setProviderTraceId(providerTraceId);
            logDO.setCostMs(cost);
            msgLogMapper.updateById(logDO);
            if (StringUtils.hasText(receiver)) {
                rateLimitService.recordFrequency(receiver, channel, logDO.getBizType());
            }
            messageMetrics.recordSend(channel, "SUCCESS", cost);
            log.info("[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms",
                    logDO.getMsgId(), channel, receiver, cost);
            return MessageResult.ok(channel, providerTraceId);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            logDO.setCostMs(cost);
            logDO.setErrorMessage(e.getMessage());
            // P0-4 通道降级：matchedRule 有 fallbackChannel 时尝试降级发送
            if (matchedRule != null && StringUtils.hasText(matchedRule.getFallbackChannel())
                    && !matchedRule.getFallbackChannel().equalsIgnoreCase(channel)) {
                MessageResult fallback = tryFallback(logDO, matchedRule.getFallbackChannel(), cost);
                if (fallback != null) {
                    return fallback;
                }
            }
            // P0-3 重试落库：retryCount < MAX → RETRY + nextRetryAt,否则 FAILED
            return handleFailure(logDO, e, cost);
        }
    }

    /**
     * P0-4 通道降级：用 fallbackChannel 重新分发。
     *
     * @return 降级成功返回 MessageResult.ok;降级失败返回 null(继续走重试逻辑)
     */
    private MessageResult tryFallback(MsgLogDO logDO, String fallbackChannel, long prevCost) {
        String origChannel = logDO.getChannel();
        long start = System.currentTimeMillis();
        try {
            logDO.setStatus(MessageStatusEnum.SENDING.name());
            logDO.setChannel(fallbackChannel);
            msgLogMapper.updateById(logDO);
            String providerTraceId = channelRouter.dispatch(logDO);
            long cost = System.currentTimeMillis() - start;
            logDO.setStatus(MessageStatusEnum.SUCCESS.name());
            logDO.setProviderTraceId(providerTraceId);
            logDO.setCostMs(prevCost + cost);
            msgLogMapper.updateById(logDO);
            messageMetrics.recordSend(fallbackChannel, "SUCCESS", cost);
            log.info("[Message] 降级发送成功: msgId={} orig={} fallback={} cost={}ms",
                    logDO.getMsgId(), origChannel, fallbackChannel, cost);
            return MessageResult.ok(fallbackChannel, providerTraceId);
        } catch (Exception fe) {
            log.warn("[Message] 降级发送失败: msgId={} fallback={} err={}",
                    logDO.getMsgId(), fallbackChannel, fe.getMessage());
            // 恢复原 channel,继续走重试逻辑
            logDO.setChannel(origChannel);
            logDO.setErrorMessage(origChannel + "→" + fallbackChannel + " 均失败: " + fe.getMessage());
            return null;
        }
    }

    /**
     * P0-3 失败处理：retryCount < MAX → RETRY + nextRetryAt(指数退避),否则 FAILED。
     */
    private MessageResult handleFailure(MsgLogDO logDO, Exception e, long cost) {
        int retryCount = logDO.getRetryCount() == null ? 0 : logDO.getRetryCount();
        if (retryCount < MessageConstants.MAX_RETRY_COUNT) {
            long backoff = MessageConstants.RETRY_BASE_BACKOFF_MS * (1L << retryCount);
            logDO.setStatus(MessageStatusEnum.RETRY.name());
            logDO.setNextRetryAt(java.time.LocalDateTime.now().plusNanos(backoff * 1_000_000L));
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
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
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

    private String resolvePriority() {
        try {
            String p = messageProperties.getDefaultPriority();
            return StringUtils.hasText(p) ? p : "NORMAL";
        } catch (Exception e) {
            return "NORMAL";
        }
    }

    private String buildRateLimitKey(String channel, String bizType) {
        return (channel == null ? "unknown" : channel) + ":" + (bizType == null ? "default" : bizType);
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
}
