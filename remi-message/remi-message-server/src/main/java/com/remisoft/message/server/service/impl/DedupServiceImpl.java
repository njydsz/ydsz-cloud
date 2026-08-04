package com.remisoft.message.server.service.impl.core;

import org.springframework.stereotype.Service;

import com.remisoft.common.lock.idempotent.IdempotentStrategy;
import com.remisoft.message.domain.constant.MessageConstants;
import com.remisoft.message.server.config.MessageProperties;
import com.remisoft.message.server.service.core.DedupService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 智能去重服务实现（P2-1）。
 *
 * <p>使用 {@link IdempotentStrategy#acquire} 实现原子去重：
 * <ul>
 *   <li>首次写入成功 → 返回 true（允许发送）</li>
 *   <li>窗口内重复写入失败 → 返回 false（跳过发送）</li>
 *   <li>TTL 到期后自动释放，允许补发</li>
 * </ul>
 *
 * <p>降级策略：Redis 异常时 fail-open（返回 true），仅记 WARN 日志，不阻断业务。
 *
 * @author remi-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DedupServiceImpl implements DedupService {

    /** 幂等策略（SET NX EX 原子去重） */
    private final IdempotentStrategy idempotentStrategy;
    /** 消息模块配置属性 */
    private final MessageProperties messageProperties;

    @Override
    public boolean tryAcquire(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return true;
        }
        MessageProperties.DedupConfig cfg = messageProperties.getDedup();
        if (cfg == null || !cfg.isEnabled()) {
            return true;
        }
        int ttl = cfg.getTtlSeconds() <= 0 ? 60 : cfg.getTtlSeconds();
        String redisKey = MessageConstants.DEDUP_KEY_PREFIX + dedupKey;
        String token = idempotentStrategy.acquire(redisKey, ttl * 1000L);
        if (token != null) {
            log.debug("[Dedup] 首次到达,放行: key={} ttl={}s", dedupKey, ttl);
            return true;
        }
        log.info("[Dedup] 检测到重复消息,跳过发送: key={} ttl={}s", dedupKey, ttl);
        return false;
    }
}
