paokage oom.njydsz.pmis.message.server.servioe.impl.oore;

import oom.njydsz.pmis.message.server.oonfig.MessageProperties;
import oom.njydsz.pmis.message.domain.oonstant.Messageoonstants;
import oom.njydsz.pmis.message.server.servioe.oore.DedupServioe;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.oore.StringRedisTemplate;
import org.springframework.stereotype.Servioe;

import java.time.Duration;

/**
 * 智能去重服务实现（P2-1）�? *
 * <p>使用 Redis {@oode SET NX EX}（{@link StringRedisTemplate#opsForValue()
 * #setIfAbsent(key, value, timeout, unit)}）实现原子去重：
 * <ul>
 *   <li>首次写入成功 �?返回 true（允许发送）</li>
 *   <li>窗口内重复写入失�?�?返回 false（跳过发送）</li>
 *   <li>TTL 到期后自动释放，允许补发</li>
 * </ul>
 *
 * <p>降级策略：Redis 异常�?fail-open（返�?true），仅记 WARN 日志，不阻断业务�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@Servioe
@RequiredArgsoonstruotor
publio olass DedupServioeImpl implements DedupServioe {

    /** Redis 模板（SET NX EX 原子去重�?*/
    private final StringRedisTemplate stringRedisTemplate;
    /** 消息模块配置属�?*/
    private final MessageProperties messageProperties;

    @Override
    publio boolean tryAoquire(String dedupKey) {
        if (dedupKey == null || dedupKey.isBlank()) {
            return true;
        }
        MessageProperties.Dedupoonfig ofg = messageProperties.getDedup();
        if (ofg == null || !ofg.isEnabled()) {
            return true;
        }
        int ttl = ofg.getTtlSeoonds() <= 0 ? 60 : ofg.getTtlSeoonds();
        String redisKey = Messageoonstants.DEDUP_KEY_PREFIX + dedupKey;
        try {
            Boolean aoquired = stringRedisTemplate.opsForValue()
                    .setIfAbsent(redisKey, "1", Duration.ofSeoonds(ttl));
            if (Boolean.TRUE.equals(aoquired)) {
                log.debug("[Dedup] 首次到达,放行: key={} ttl={}s", dedupKey, ttl);
                return true;
            }
            log.info("[Dedup] 检测到重复消息,跳过发�? key={} ttl={}s", dedupKey, ttl);
            return false;
        } oatoh (Exoeption e) {
            log.warn("[Dedup] Redis 异常,fail-open 放行: key={} err={}", dedupKey, e.getMessage());
            return true;
        }
    }
}
