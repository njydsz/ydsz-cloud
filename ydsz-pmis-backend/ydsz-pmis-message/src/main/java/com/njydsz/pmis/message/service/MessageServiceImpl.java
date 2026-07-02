package com.njydsz.pmis.message.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.common.util.TraceIdUtil;
import com.njydsz.pmis.message.channel.MessageChannel;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.message.entity.MessageLogDO;
import com.njydsz.pmis.message.entity.MessageTemplateDO;
import com.njydsz.pmis.message.mapper.MessageLogMapper;
import com.njydsz.pmis.message.mapper.MessageTemplateMapper;
import com.njydsz.pmis.message.template.TemplateEngine;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationContext;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 消息服务实现
 *
 * <p>核心职责：
 * <ul>
 *   <li>按 channel 选择 MessageChannel 实现</li>
 *   <li>按 templateCode 加载模板并渲染</li>
 *   <li>记录发送日志（pmis_message_log）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class MessageServiceImpl implements MessageService {

    private final MessageLogMapper messageLogMapper;
    private final MessageTemplateMapper messageTemplateMapper;
    private final TemplateEngine templateEngine;
    private final ApplicationContext applicationContext;

    /** channel -> MessageChannel 缓存 */
    private final Map<String, MessageChannel> channelCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void initChannels() {
        Map<String, MessageChannel> beans = applicationContext.getBeansOfType(MessageChannel.class);
        for (MessageChannel c : beans.values()) {
            channelCache.put(c.channelType().toUpperCase(), c);
        }
        log.info("[Message] 通道初始化完成, 共加载 {} 个: {}", channelCache.size(), channelCache.keySet());
    }

    @Override
    public MessageResult send(MessageRequest request) {
        if (request == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "请求不能为空");
        }
        if (!StringUtils.hasText(request.getChannel())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "channel 不能为空");
        }
        if (!StringUtils.hasText(request.getReceiver())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "接收人不能为空");
        }

        // 加载并渲染模板
        if (StringUtils.hasText(request.getTemplateCode())) {
            MessageTemplateDO template = loadTemplate(
                    request.getTemplateCode(), request.getChannel().toUpperCase(), null);
            if (template == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "模板不存在: " + request.getTemplateCode());
            }
            if (!"ENABLED".equalsIgnoreCase(template.getStatus())) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "模板已停用: " + request.getTemplateCode());
            }
            Map<String, Object> params = request.getParams() == null ? new HashMap<>() : request.getParams();
            String content = templateEngine.render(template.getContent(), params);
            request.setContent(content);
            if (!StringUtils.hasText(request.getSubject()) && StringUtils.hasText(template.getSubject())) {
                request.setSubject(template.getSubject());
            }
        }

        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "消息内容不能为空");
        }

        // 选择通道
        MessageChannel channel = channelCache.get(request.getChannel().toUpperCase());
        if (channel == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "不支持的消息通道: " + request.getChannel());
        }

        // 执行发送并记录日志
        return executeWithLog(channel, request);
    }

    @Override
    public MessageResult sendDirect(MessageRequest request) {
        if (request != null) {
            request.setTemplateCode(null);
        }
        return send(request);
    }

    @Override
    public Page<MessageLogDO> pageLog(int page, int size, String channel, String bizType, String status) {
        Page<MessageLogDO> p = new Page<>(page, size);
        LambdaQueryWrapper<MessageLogDO> w = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(channel)) w.eq(MessageLogDO::getChannel, channel);
        if (StringUtils.hasText(bizType)) w.eq(MessageLogDO::getBizType, bizType);
        if (StringUtils.hasText(status)) w.eq(MessageLogDO::getStatus, status);
        w.orderByDesc(MessageLogDO::getCreatedAt);
        return messageLogMapper.selectPage(p, w);
    }

    @Override
    public MessageTemplateDO loadTemplate(String templateCode, String channel, Long tenantId) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
            return null;
        }
        if (tenantId == null) tenantId = 1L;
        return messageTemplateMapper.selectByCodeAndChannel(templateCode, channel.toUpperCase(), tenantId);
    }

    // ==================== 内部 ====================

    private MessageResult executeWithLog(MessageChannel channel, MessageRequest request) {
        MessageLogDO log0 = new MessageLogDO();
        log0.setChannel(channel.channelType());
        log0.setBizType(request.getBizType());
        log0.setBizId(request.getBizId());
        log0.setReceiver(request.getReceiver());
        log0.setTemplateCode(request.getTemplateCode());
        log0.setTemplateParams(request.getParams() == null ? null
                : request.getParams().toString());
        log0.setContent(request.getContent());
        log0.setStatus("PENDING");
        log0.setTraceId(TraceIdUtil.get());
        log0.setTenantId(1L);
        log0.setCreatedAt(LocalDateTime.now());
        log0.setUpdatedAt(LocalDateTime.now());
        log0.setDeleted(0);

        long start = System.currentTimeMillis();
        MessageResult result;
        try {
            result = channel.send(request);
        } catch (Exception e) {
            log.error("[Message] 通道 {} 发送异常: receiver={} reason={}",
                    channel.channelType(), request.getReceiver(), e.getMessage(), e);
            result = MessageResult.fail(channel.channelType(),
                    e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        long cost = System.currentTimeMillis() - start;

        log0.setCostMs(cost);
        log0.setStatus(result.isSuccess() ? "SUCCESS" : "FAILED");
        log0.setProviderTraceId(result.getProviderTraceId());
        log0.setErrorMessage(result.getErrorMessage());

        try {
            messageLogMapper.insert(log0);
        } catch (Exception e) {
            log.error("[Message] 保存日志失败: {}", e.getMessage(), e);
        }
        return result;
    }

    /**
     * 已注册通道列表（供监控/管理端使用）
     *
     * @return 通道类型列表（不可变副本）
     */
    public List<String> listChannelTypes() {
        return List.copyOf(channelCache.keySet());
    }
}
