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

    /** 消息日志 Mapper */
    private final MessageLogMapper messageLogMapper;
    /** 消息模板 Mapper */
    private final MessageTemplateMapper messageTemplateMapper;
    /** 模板引擎 */
    private final TemplateEngine templateEngine;
    /** Spring 应用上下文，用于初始化阶段收集通道 Bean */
    private final ApplicationContext applicationContext;

    /** channel -> MessageChannel 缓存 */
    private final Map<String, MessageChannel> channelCache = new ConcurrentHashMap<>();

    /**
     * 初始化阶段收集所有 {@link MessageChannel} 实现并按通道类型注册到缓存。
     */
    @PostConstruct
    public void initChannels() {
        Map<String, MessageChannel> beans = applicationContext.getBeansOfType(MessageChannel.class);
        for (MessageChannel c : beans.values()) {
            channelCache.put(c.channelType().toUpperCase(), c);
        }
        log.info("[Message] 通道初始化完成, 共加载 {} 个: {}", channelCache.size(), channelCache.keySet());
    }

    /**
     * 发送消息：校验入参 → 加载渲染模板 → 选择通道 → 执行发送并记录日志。
     *
     * @param request 消息发送请求
     * @return 消息发送结果
     * @throws BizException 入参非法、模板不存在/停用、通道不支持时抛出
     */
    @Override
    public MessageResult send(MessageRequest request) {
        if (request == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.message.msg_d9712a58");
        }
        if (!StringUtils.hasText(request.getChannel())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.message.msg_fd9fba6f");
        }
        if (!StringUtils.hasText(request.getReceiver())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.message.msg_35f5875c");
        }

        // 加载并渲染模板
        if (StringUtils.hasText(request.getTemplateCode())) {
            MessageTemplateDO template = loadTemplate(
                    request.getTemplateCode(), request.getChannel().toUpperCase(), null);
            if (template == null) {
                throw new BizException(BizErrorCode.NOT_FOUND, "error.message.msg_c16cb047" + request.getTemplateCode());
            }
            if (!"ENABLED".equalsIgnoreCase(template.getStatus())) {
                throw new BizException(BizErrorCode.BAD_REQUEST, "error.message.msg_fe0cc3a8" + request.getTemplateCode());
            }
            Map<String, Object> params = request.getParams() == null ? new HashMap<>() : request.getParams();
            String content = templateEngine.render(template.getContent(), params);
            request.setContent(content);
            if (!StringUtils.hasText(request.getSubject()) && StringUtils.hasText(template.getSubject())) {
                request.setSubject(template.getSubject());
            }
        }

        if (!StringUtils.hasText(request.getContent())) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.message.msg_48e93db8");
        }

        // 选择通道
        MessageChannel channel = channelCache.get(request.getChannel().toUpperCase());
        if (channel == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "error.message.msg_3d19e7f2" + request.getChannel());
        }

        // 执行发送并记录日志
        return executeWithLog(channel, request);
    }

    /**
     * 直接发送消息（忽略模板，仅使用 content）。
     *
     * @param request 消息发送请求
     * @return 消息发送结果
     */
    @Override
    public MessageResult sendDirect(MessageRequest request) {
        if (request != null) {
            request.setTemplateCode(null);
        }
        return send(request);
    }

    /**
     * 分页查询消息发送日志，支持按通道/业务类型/状态过滤。
     *
     * @param page    页码
     * @param size    每页大小
     * @param channel 通道（可选）
     * @param bizType 业务类型（可选）
     * @param status  发送状态（可选）
     * @return 消息日志分页结果
     */
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

    /**
     * 加载消息模板，tenantId 为空时默认 1。
     *
     * @param templateCode 模板编码
     * @param channel      通道（大小写不敏感）
     * @param tenantId     租户 ID（可选）
     * @return 模板对象，不存在时返回 null
     */
    @Override
    public MessageTemplateDO loadTemplate(String templateCode, String channel, Long tenantId) {
        if (!StringUtils.hasText(templateCode) || !StringUtils.hasText(channel)) {
            return null;
        }
        if (tenantId == null) tenantId = 1L;
        return messageTemplateMapper.selectByCodeAndChannel(templateCode, channel.toUpperCase(), tenantId);
    }

    // ==================== 内部 ====================

    /**
     * 执行通道发送并记录发送日志，发送异常被捕获转为失败结果，日志写入异常被吞掉。
     *
     * @param channel 消息通道
     * @param request 消息发送请求
     * @return 消息发送结果
     */
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
