package com.njydsz.pmis.message.server.service.impl.batch;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.core.response.StandardResultCode;
import com.njydsz.pmis.common.domain.query.PageQuery;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.common.feign.MessageRequest;
import com.njydsz.pmis.common.feign.MessageResult;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.entity.batch.MsgAggregateDO;
import com.njydsz.pmis.message.domain.entity.template.MsgTemplateDO;
import com.njydsz.pmis.message.domain.enums.batch.AggregateBatchStatusEnum;
import com.njydsz.pmis.message.infra.mapper.batch.MsgAggregateMapper;
import com.njydsz.pmis.message.server.service.batch.AggregateService;
import com.njydsz.pmis.message.server.service.core.MessageService;
import com.njydsz.pmis.message.server.service.template.TemplateService;
import com.njydsz.pmis.message.server.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * 聚合批次服务实现�? *
 * <p>appendOrStart 在分布式锁内执行:存在 PENDING 批次则追�?否则新建 PENDING 批次并设定计划发送时�?
 * flushDue 发送到期的 READY 批次;flushByGroup 强制刷新指定�?接收人�? *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AggregateServiceImpl implements AggregateService {

    /** 默认聚合频率窗口(分钟) */
    private static final long DEFAULT_FREQUENCY_MINUTES = 30L;

    /** 摘要模板编码前缀,完整编码 = 前缀 + aggregateGroup(bizType) */
    private static final String DIGEST_TEMPLATE_PREFIX = "DIGEST_";

    /** 默认摘要模板内容(未配置摘要模板时回退) */
    private static final String DEFAULT_DIGEST_TEMPLATE = "您有 ${count} �?${group} 相关消息,请及时查�?;

    /** 聚合批次 Mapper */
    private final MsgAggregateMapper msgAggregateMapper;
    /** 消息发送服务（flush 时回调发送） */
    private final MessageService messageService;
    /** 模板引擎（摘要渲染） */
    private final TemplateEngine templateEngine;
    /** 模板管理服务（加载摘要模板） */
    private final TemplateService templateService;
    /** Redisson 客户端（分布式锁�?*/
    private final RedissonClient redissonClient;

    @Override
    public MsgAggregateDO appendOrStart(String group, String receiver, String channel, String tenantId) {
        if (!StringUtils.hasText(group) || !StringUtils.hasText(receiver)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "聚合组与接收人不能为�?);
        }
        String tid = StringUtils.hasText(tenantId) ? tenantId : TenantContext.getTenantId();
        String lockKey = MessageConstants.AGGREGATE_LOCK_PREFIX + group + ":" + receiver;
        RLock lock = redissonClient.getLock(lockKey);
        boolean locked = false;
        try {
            locked = lock.tryLock(3, 10, TimeUnit.SECONDS);
            if (!locked) {
                throw new SysException(StandardResultCode.RESOURCE_LOCKED, "获取聚合锁失�? " + group);
            }
            // �?PENDING 批次
            MsgAggregateDO batch = msgAggregateMapper.selectOne(new LambdaQueryWrapper<MsgAggregateDO>()
                    .eq(MsgAggregateDO::getAggregateGroup, group)
                    .eq(MsgAggregateDO::getReceiver, receiver)
                    .eq(MsgAggregateDO::getBatchStatus, AggregateBatchStatusEnum.PENDING.name())
                    .last("LIMIT 1"));
            LocalDateTime now = LocalDateTime.now();
            if (batch != null) {
                batch.setMessageCount((batch.getMessageCount() == null ? 0 : batch.getMessageCount()) + 1);
                batch.setLastMessageAt(now);
                msgAggregateMapper.updateById(batch);
                return batch;
            }
            // 新建 PENDING 批次
            MsgAggregateDO entity = new MsgAggregateDO();
            entity.setAggregateGroup(group);
            entity.setReceiver(receiver);
            entity.setChannel(channel);
            entity.setBatchStatus(AggregateBatchStatusEnum.PENDING.name());
            entity.setMessageCount(1);
            entity.setFirstMessageAt(now);
            entity.setLastMessageAt(now);
            entity.setScheduledSendAt(now.plusMinutes(DEFAULT_FREQUENCY_MINUTES));
            entity.setTenantId(tid);
            msgAggregateMapper.insert(entity);
            log.info("[Aggregate] 新建批次: group={} receiver={} scheduledAt={}", group, receiver, entity.getScheduledSendAt());
            return entity;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new SysException(StandardResultCode.RESOURCE_LOCKED, "聚合锁等待中�?);
        } finally {
            if (locked && lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }

    @Override
    public int flushDue() {
        LocalDateTime now = LocalDateTime.now();
        List<MsgAggregateDO> due = msgAggregateMapper.selectList(new LambdaQueryWrapper<MsgAggregateDO>()
                .eq(MsgAggregateDO::getBatchStatus, AggregateBatchStatusEnum.READY.name())
                .le(MsgAggregateDO::getScheduledSendAt, now));
        int sent = 0;
        for (MsgAggregateDO batch : due) {
            if (sendBatch(batch)) {
                sent++;
            }
        }
        if (sent > 0) {
            log.info("[Aggregate] flushDue 发�?{} 个到期批�?, sent);
        }
        return sent;
    }

    @Override
    public int flushByGroup(String group, String receiver) {
        if (!StringUtils.hasText(group) || !StringUtils.hasText(receiver)) {
            throw new SysException(StandardResultCode.BAD_REQUEST, "聚合组与接收人不能为�?);
        }
        List<MsgAggregateDO> batches = msgAggregateMapper.selectList(new LambdaQueryWrapper<MsgAggregateDO>()
                .eq(MsgAggregateDO::getAggregateGroup, group)
                .eq(MsgAggregateDO::getReceiver, receiver)
                .in(MsgAggregateDO::getBatchStatus,
                        AggregateBatchStatusEnum.PENDING.name(),
                        AggregateBatchStatusEnum.READY.name()));
        int sent = 0;
        for (MsgAggregateDO batch : batches) {
            if (sendBatch(batch)) {
                sent++;
            }
        }
        log.info("[Aggregate] flushByGroup 发�?{} 个批�? group={} receiver={}", sent, group, receiver);
        return sent;
    }

    @Override
    public Page<MsgAggregateDO> page(PageQuery query) {
        Page<MsgAggregateDO> page = new Page<>(
                query == null ? 1 : query.getPage(),
                Math.min(query == null ? 10 : query.getSize(), PageQuery.MAX_SIZE));
        return msgAggregateMapper.selectPage(page, new LambdaQueryWrapper<MsgAggregateDO>()
                .orderByDesc(MsgAggregateDO::getCreatedAt));
    }

    /**
     * 发送单个聚合批�?渲染摘要 �?�?MessageService 发�?�?更新 SENT�?     *
     * @param batch 聚合批次
     * @return true 表示发送成�?     */
    private boolean sendBatch(MsgAggregateDO batch) {
        try {
            // 渲染摘要内容：优先按 bizType 查找摘要模板 DIGEST_{group},回退默认模板
            Map<String, Object> params = new HashMap<>();
            params.put("count", batch.getMessageCount());
            params.put("group", batch.getAggregateGroup());
            String digestTemplate = loadDigestTemplate(batch);
            String digest = templateEngine.render(digestTemplate, params);
            batch.setDigestContent(digest);
            MessageRequest request = new MessageRequest();
            request.setChannel(batch.getChannel());
            request.setReceiver(batch.getReceiver());
            request.setContent(digest);
            request.setBizType("AGGREGATE");
            request.setBizId(batch.getId());
            MessageResult result = messageService.send(request);
            boolean ok = result != null && result.isSuccess();
            if (ok) {
                batch.setBatchStatus(AggregateBatchStatusEnum.SENT.name());
                batch.setSentAt(LocalDateTime.now());
                msgAggregateMapper.updateById(batch);
                return true;
            }
            log.warn("[Aggregate] 批次发送失�? id={} err={}", batch.getId(),
                    result == null ? "无响�? : result.getErrorMessage());
            return false;
        } catch (Exception e) {
            log.error("[Aggregate] 批次发送异�? id={} err={}", batch.getId(), e.getMessage());
            return false;
        }
    }

    /**
     * 加载摘要模板：按约定编码 DIGEST_{aggregateGroup} 查找,
     * 找到则用模板 content,否则回退默认摘要文案�?     */
    private String loadDigestTemplate(MsgAggregateDO batch) {
        String group = batch.getAggregateGroup();
        if (!StringUtils.hasText(group)) {
            return DEFAULT_DIGEST_TEMPLATE;
        }
        try {
            MsgTemplateDO tpl = templateService.loadByCodeAndChannel(
                    DIGEST_TEMPLATE_PREFIX + group, batch.getChannel(),
                    null, batch.getTenantId());
            if (tpl != null && StringUtils.hasText(tpl.getContent())) {
                return tpl.getContent();
            }
        } catch (Exception e) {
            log.debug("[Aggregate] 摘要模板加载失败,回退默认: group={} err={}",
                    group, e.getMessage());
        }
        return DEFAULT_DIGEST_TEMPLATE;
    }
}
