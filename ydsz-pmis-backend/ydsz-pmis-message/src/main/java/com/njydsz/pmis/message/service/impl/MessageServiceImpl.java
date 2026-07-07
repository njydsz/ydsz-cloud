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
import com.njydsz.pmis.message.dto.MessageLogQueryDTO;
import com.njydsz.pmis.message.dto.MessageSendDTO;
import com.njydsz.pmis.message.entity.MsgLogDO;
import com.njydsz.pmis.message.entity.MsgRouteRuleDO;
import com.njydsz.pmis.message.entity.MsgTemplateDO;
import com.njydsz.pmis.message.enums.MessageStatusEnum;
import com.njydsz.pmis.message.enums.RecallStatusEnum;
import com.njydsz.pmis.message.mapper.MsgLogMapper;
import com.njydsz.pmis.message.metric.MessageMetrics;
import com.njydsz.pmis.message.service.CanaryService;
import com.njydsz.pmis.message.service.MessageService;
import com.njydsz.pmis.message.service.PreferenceService;
import com.njydsz.pmis.message.service.RateLimitService;
import com.njydsz.pmis.message.service.RouteRuleService;
import com.njydsz.pmis.message.service.TemplateService;
import com.njydsz.pmis.message.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 消息发送核心编排服务实现。
 *
 * <p>发送流程：通道校验 → 路由 → 灰度 → 限流 → 模板加载 → 渲染 → 落库 PENDING →
 * 通道分发 → 更新 SUCCESS/FAILED → 频率计数。异常捕获并落库 FAILED。
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
    private final PreferenceService preferenceService;
    private final CanaryService canaryService;
    private final MessageProperties messageProperties;
    private final MessageMetrics messageMetrics;

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
        // ③ 灰度命中标记
        int canaryFlag = 0;
        if (StringUtils.hasText(request.getTemplateCode())
                && canaryService.hit(request.getTemplateCode(), receiver)) {
            canaryFlag = 1;
        }
        // ④ 限流 + 频率
        if (!rateLimitService.tryAcquire(buildRateLimitKey(channel, bizType), 1)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new BizException(BizErrorCode.RATE_LIMIT, "发送限流，请稍后重试");
        }
        if (StringUtils.hasText(receiver)
                && !rateLimitService.checkFrequency(receiver, channel, bizType)) {
            messageMetrics.recordSend(channel, "FAILED", 0);
            throw new BizException(BizErrorCode.RATE_LIMIT, "发送频率超限");
        }
        // ⑤ 加载模板（有 templateCode 时）
        String content = request.getContent();
        String subject = request.getSubject();
        MsgTemplateDO template = null;
        if (StringUtils.hasText(request.getTemplateCode())) {
            template = templateService.loadByCodeAndChannel(
                    request.getTemplateCode(), channel, null, TenantContext.getTenantId());
            if (template == null) {
                return MessageResult.fail(channel, "模板不存在: " + request.getTemplateCode());
            }
            if (StringUtils.hasText(template.getContent())) {
                content = templateEngine.render(template.getContent(), request.getParams());
            }
            if (!StringUtils.hasText(subject) && StringUtils.hasText(template.getSubject())) {
                subject = templateEngine.render(template.getSubject(), request.getParams());
            }
        }
        // ⑦ 落库 PENDING
        MsgLogDO logDO = new MsgLogDO();
        logDO.setChannel(channel);
        logDO.setBizType(bizType);
        logDO.setBizId(request.getBizId());
        logDO.setReceiver(receiver);
        logDO.setTemplateCode(request.getTemplateCode());
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

        // ⑧ 通道分发
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
            // ⑩ 频率计数
            if (StringUtils.hasText(receiver)) {
                rateLimitService.recordFrequency(receiver, channel, bizType);
            }
            messageMetrics.recordSend(channel, "SUCCESS", cost);
            log.info("[Message] 发送成功: msgId={} channel={} receiver={} cost={}ms",
                    logDO.getMsgId(), channel, receiver, cost);
            return MessageResult.ok(channel, providerTraceId);
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - start;
            logDO.setStatus(MessageStatusEnum.FAILED.name());
            logDO.setErrorMessage(e.getMessage());
            logDO.setCostMs(cost);
            msgLogMapper.updateById(logDO);
            messageMetrics.recordSend(channel, "FAILED", cost);
            log.error("[Message] 发送失败: msgId={} channel={} receiver={} err={}",
                    logDO.getMsgId(), channel, receiver, e.getMessage());
            return MessageResult.fail(channel, e.getMessage());
        }
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
        // senderId / messageGroup / locale / priority 不在 MessageRequest 中，仅日志侧使用
        return send(request);
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
     *
     * @param channel 通道
     * @return true 表示启用
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
