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
import com.njydsz.pmis.message.config.RetryStrategyResolver;
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
import com.njydsz.pmis.message.filter.SensitiveWordFilter;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.AggregateService;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.DedupService;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.SubscriptionService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.TemplateEngine;
import com.njydsz.pmis.message.producer.RocketMQMessageProducer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

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
    private final SensitiveWordFilter sensitiveWordFilter;
    private final RetryStrategyResolver retryStrategyResolver;
    private final DedupService dedupService;

    /** P2-3: RocketMQ 事务消息生产者（可选,未配置 RocketMQ 时为 null） */
    @Autowired(required = false)
    private RocketMQMessageProducer mqProducer;

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
            log.info("[Message] 命中免打扰时段,跳过发送: receiver={} dnd={}~{}",
                    receiver, pref.getDndStart(), pref.getDndEnd());
            messageMetrics.recordSend(channel, "DND_SKIPPED", 0);
            return MessageResult.fail(channel, "当前为免打扰时段");
        }
        String prefLocale = pref != null ? pref.getLocale() : null;

        // ⑤-2 P2-1: 智能去重（SET NX EX）—— 相同 dedupKey 在 TTL 窗口内仅允许一次
        String dedupKey = buildDedupKey(request);
        if (StringUtils.hasText(dedupKey) && !dedupService.tryAcquire(dedupKey)) {
            log.info("[Message] 检测到重复消息,跳过发送: dedupKey={} receiver={}", dedupKey, receiver);
            messageMetrics.recordSend(channel, "DEDUPED", 0);
            return MessageResult.fail(channel, "消息重复,已忽略");
        }

        // ⑥ 限流 + 频率
        // ⑥-1 通道+bizType 维度令牌桶（全局配额）
        if (!rateLimitService.tryAcquire(buildRateLimitKey(channel, bizType), 1)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new BizException(BizErrorCode.RATE_LIMIT, "发送限流，请稍后重试");
        }
        // ⑥-2 P2-5/P0-5: 多维度令牌桶（receiver/templateCode/tenant），优先级感知
        if (!rateLimitService.checkSendLimit(channel, receiver, templateCode,
                TenantContext.getTenantId(), request.getPriority())) {
            messageMetrics.recordSend(channel, "RATE_LIMITED", 0);
            throw new BizException(BizErrorCode.RATE_LIMIT, "多维度限流：receiver/template/tenant 超限");
        }
        // ⑥-3 用户偏好频率（每日/每小时上限）
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

        // ⑦-2 敏感词过滤（P2-1）：对最终 content 做敏感词替换,无论模板渲染还是直传内容
        if (StringUtils.hasText(content)) {
            content = sensitiveWordFilter.filter(content);
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
                && request.getScheduledAt().isAfter(java.time.LocalDateTime.now())) {
            logDO.setStatus(MessageStatusEnum.SCHEDULED.name());
            msgLogMapper.insert(logDO);
            log.info("[Message] 定时消息已入库: msgId={} scheduledAt={} channel={}",
                    logDO.getMsgId(), logDO.getScheduledAt(), channel);
            return MessageResult.ok(channel, logDO.getMsgId());
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
            logDO.setStatus(MessageStatusEnum.SENDING.name());
            msgLogMapper.updateById(logDO);
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
            messageMetrics.recordSend(channel, "SUCCESS", cost);
            log.info("[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms",
                    logDO.getMsgId(), channel, receiver, cost);
            return MessageResult.ok(channel, providerTraceId);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            logDO.setCostMs(cost);
            logDO.setErrorMessage(e.getMessage());
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
            return java.util.Collections.emptyList();
        }
        String chain = matchedRule.getFallbackChain();
        java.util.List<String> result = new java.util.ArrayList<>();
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
        java.util.List<String> tried = new java.util.ArrayList<>();
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
    private java.math.BigDecimal calculateCost(String channel) {
        MessageProperties.CostConfig cfg = messageProperties.getCost();
        if (cfg == null || !cfg.isEnabled() || cfg.getUnitPrices() == null) {
            return java.math.BigDecimal.ZERO;
        }
        return cfg.getUnitPrices().getOrDefault(channel, java.math.BigDecimal.ZERO);
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
     * 半消息发送后由 {@link com.njydsz.pmis.message.producer.MessageTransactionListener}
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "消息请求不能为空");
        }
        if (mqProducer == null) {
            log.warn("[Message] RocketMQ 未配置,事务消息降级为同步发送: channel={}", request.getChannel());
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
}
