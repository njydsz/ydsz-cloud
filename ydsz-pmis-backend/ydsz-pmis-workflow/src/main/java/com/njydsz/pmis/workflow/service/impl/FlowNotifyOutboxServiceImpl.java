package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.security.TenantContext;
import com.alibaba.fastjson2.JSON;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.common.feign.NotificationClient;
import com.njydsz.pmis.common.feign.dto.NotificationFeignDTO;
import com.njydsz.pmis.workflow.entity.FlowNotifyOutboxDO;
import com.njydsz.pmis.workflow.mapper.FlowNotifyOutboxMapper;
import com.njydsz.pmis.workflow.service.FlowNotifyOutboxService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 工作流通知外发箱服务实现（P2-1 阶段一）
 *
 * <p>本地消息表 + 定时扫描重投，保证业务事务与通知投递的最终一致性。
 * 阶段一不接 MQ，直接同步调 NotificationClient Feign 重投；
 * 阶段二将切换为 RocketMQ 投递（消费端 MessageConsumer 已就绪）。
 *
 * @author ydsz-pmis-team
 * @since 1.2.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowNotifyOutboxServiceImpl implements FlowNotifyOutboxService {

    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRIES = 5;

    /** 默认单次扫描条数 */
    private static final int DEFAULT_BATCH_SIZE = 50;

    /** 指数退避基数（秒）：30s, 60s, 120s, 240s, 480s */
    private static final long[] BACKOFF_SECONDS = {30L, 60L, 120L, 240L, 480L, 900L, 1800L};

    /** 通知中心 Feign 客户端（注入失败时由 fallback 返回错误码） */
    private final NotificationClient notificationClient;
    private final FlowNotifyOutboxMapper flowNotifyOutboxMapper;

    /**
     * 写入 outbox（在主事务内同步执行，事务回滚则 outbox 也回滚）
     *
     * <p>不使用 @Transactional，继承调用方事务上下文。
     */
    @Override
    public Long saveOutbox(FlowNotifyOutboxDO event) {
        if (event == null) {
            return null;
        }
        if (event.getStatus() == null) {
            event.setStatus("PENDING");
        }
        if (event.getRetryCount() == null) {
            event.setRetryCount(0);
        }
        if (event.getMaxRetries() == null) {
            event.setMaxRetries(DEFAULT_MAX_RETRIES);
        }
        if (event.getNextRetryAt() == null) {
            event.setNextRetryAt(LocalDateTime.now());
        }
        if (event.getTenantId() == null) {
            event.setTenantId(TenantContext.getTenantId());
        }
        flowNotifyOutboxMapper.insert(event);
        log.debug("[NotifyOutbox] 事件入箱: id={} type={} bizType={} bizId={}",
                event.getId(), event.getEventType(), event.getBizType(), event.getBizId());
        return event.getId();
    }

    /**
     * 扫描待投递事件并投递
     *
     * <p>使用 REQUIRES_NEW 开启新事务，FOR UPDATE SKIP LOCKED 锁定行避免多实例并发抢占。
     * 单条投递失败不影响其他事件，最终 retry_count 超阈值标 DEAD。
     */
    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public int scanAndDeliver(int batchSize) {
        int effectiveBatch = batchSize <= 0 ? DEFAULT_BATCH_SIZE : batchSize;
        LocalDateTime now = LocalDateTime.now();
        List<FlowNotifyOutboxDO> pending = flowNotifyOutboxMapper.selectPendingForSend(now, effectiveBatch);
        if (pending == null || pending.isEmpty()) {
            return 0;
        }
        log.info("[NotifyOutbox] 扫描到 {} 条待投递事件", pending.size());
        int successCount = 0;
        for (FlowNotifyOutboxDO event : pending) {
            try {
                boolean ok = deliverOne(event);
                if (ok) {
                    flowNotifyOutboxMapper.markSent(event.getId(), LocalDateTime.now());
                    successCount++;
                } else {
                    handleFailure(event, "投递返回失败码");
                }
            } catch (Exception e) {
                handleFailure(event, truncate(e.getMessage(), 1000));
            }
        }
        log.info("[NotifyOutbox] 本轮投递完成: 成功 {} / 总 {} 条", successCount, pending.size());
        return successCount;
    }

    @Override
    @Transactional(readOnly = true)
    public List<FlowNotifyOutboxDO> listDeadEvents(int limit) {
        int effectiveLimit = limit <= 0 ? 50 : limit;
        return flowNotifyOutboxMapper.selectList(
                new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<FlowNotifyOutboxDO>()
                        .eq(FlowNotifyOutboxDO::getStatus, "DEAD")
                        .orderByDesc(FlowNotifyOutboxDO::getCreatedAt)
                        .last("LIMIT " + effectiveLimit));
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW, rollbackFor = Exception.class)
    public boolean retryDeadEvent(String id) {
        if (id == null) {
            return false;
        }
        FlowNotifyOutboxDO event = flowNotifyOutboxMapper.selectById(id);
        if (event == null || !"DEAD".equals(event.getStatus())) {
            return false;
        }
        event.setStatus("PENDING");
        event.setRetryCount(0);
        event.setErrorMsg(null);
        event.setNextRetryAt(LocalDateTime.now());
        flowNotifyOutboxMapper.updateById(event);
        log.info("[NotifyOutbox] 死信事件人工重投: id={} type={}", id, event.getEventType());
        return true;
    }

    // ============================== 内部辅助 ==============================

    /**
     * 投递单条事件到通知中心
     *
     * <p>阶段一：直接调 NotificationClient.send Feign。
     * 阶段二将切换为 RocketMQTemplate.syncSend。
     *
     * @param event 事件
     * @return true 表示投递成功
     */
    private boolean deliverOne(FlowNotifyOutboxDO event) {
        try {
            NotificationFeignDTO dto = buildNotificationDTO(event);
            Result<Integer> resp = notificationClient.send(dto);
            if (resp == null) {
                log.warn("[NotifyOutbox] 投递响应为空: id={}", event.getId());
                return false;
            }
            if (resp.getCode() != Result.CODE_SUCCESS) {
                log.warn("[NotifyOutbox] 投递返回失败码: id={} code={} msg={}",
                        event.getId(), resp.getCode(), resp.getMessage());
                return false;
            }
            log.debug("[NotifyOutbox] 投递成功: id={} type={}", event.getId(), event.getEventType());
            return true;
        } catch (Exception e) {
            log.warn("[NotifyOutbox] 投递异常: id={} err={}", event.getId(), e.getMessage());
            throw new RuntimeException("投递异常: " + e.getMessage(), e);
        }
    }

    /**
     * 处理投递失败：增加重试计数，超阈值标 DEAD
     */
    private void handleFailure(FlowNotifyOutboxDO event, String errorMsg) {
        int newRetryCount = (event.getRetryCount() == null ? 0 : event.getRetryCount()) + 1;
        int maxRetries = event.getMaxRetries() == null ? DEFAULT_MAX_RETRIES : event.getMaxRetries();
        if (newRetryCount >= maxRetries) {
            flowNotifyOutboxMapper.markDead(event.getId(), truncate(errorMsg, 1000));
            log.warn("[NotifyOutbox] 事件转入死信: id={} type={} retries={}",
                    event.getId(), event.getEventType(), newRetryCount);
        } else {
            LocalDateTime nextRetry = LocalDateTime.now().plusSeconds(calcBackoff(newRetryCount));
            flowNotifyOutboxMapper.markRetry(event.getId(), truncate(errorMsg, 1000), nextRetry);
            log.debug("[NotifyOutbox] 事件投递失败，等待下次重试: id={} retries={} nextRetry={}",
                    event.getId(), newRetryCount, nextRetry);
        }
    }

    /**
     * 构造通知中心 send 接口的 DTO
     */
    private NotificationFeignDTO buildNotificationDTO(FlowNotifyOutboxDO event) {
        NotificationFeignDTO dto = new NotificationFeignDTO();
        dto.setTitle(event.getEventType());
        dto.setContent(event.getPayload());
        dto.setLevel("INFO");
        dto.setCategory("WORKFLOW");
        dto.setBizType(event.getBizType());
        if (event.getBizId() != null) {
            dto.setBizId(String.valueOf(event.getBizId()));
        }
        // 解析 payload JSON 提取额外字段
        if (event.getPayload() != null && !event.getPayload().isBlank()) {
            try {
                Map<String, Object> extra = JSON.parseObject(event.getPayload());
                if (extra != null) {
                    if (extra.containsKey("title")) dto.setTitle((String) extra.get("title"));
                    if (extra.containsKey("content")) dto.setContent((String) extra.get("content"));
                    if (extra.containsKey("level")) dto.setLevel((String) extra.get("level"));
                    if (extra.containsKey("receiverId")) {
                        Object rid = extra.get("receiverId");
                        if (rid instanceof Number n) dto.setReceiverId(n.longValue());
                    }
                }
            } catch (Exception ignore) {
                // payload 非 JSON，保留原始内容
            }
        }
        // 批量接收人
        if (event.getTargetUserIds() != null && !event.getTargetUserIds().isBlank()) {
            try {
                List<Long> ids = new ArrayList<>();
                for (String s : event.getTargetUserIds().split(",")) {
                    ids.add(Long.parseLong(s.trim()));
                }
                dto.setReceiverIds(ids);
            } catch (Exception ignore) {
                // 解析失败忽略
            }
        }
        return dto;
    }

    /**
     * 指数退避：根据重试次数计算下次退避秒数
     */
    private long calcBackoff(int retryCount) {
        if (retryCount <= 0) {
            return BACKOFF_SECONDS[0];
        }
        int idx = Math.min(retryCount - 1, BACKOFF_SECONDS.length - 1);
        return BACKOFF_SECONDS[idx];
    }

    /**
     * 截断字符串到指定长度
     */
    private String truncate(String s, int maxLen) {
        if (s == null) {
            return null;
        }
        return s.length() <= maxLen ? s : s.substring(0, maxLen);
    }
}
