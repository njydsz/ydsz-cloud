package com.njydsz.pmis.message.server.realtime;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.socket.constant.WebSocketConstants;
import com.njydsz.pmis.common.socket.offline.OfflineMessageStore;
import com.njydsz.pmis.common.json.YdszJson;
import com.njydsz.pmis.message.domain.entity.config.MsgOfflineDO;
import com.njydsz.pmis.message.infra.mapper.config.MsgOfflineMapper;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 离线消息补偿服务（Redis + DB 双层存储）。
 *
 * <p>实现 {@link OfflineMessageStore} 接口，由 common-socket 的
 * {@code WebSocketSessionEventListener} 自动调用。
 *
 * <p>用户离线时，将待推送消息缓存到 Redis List（{@code pmis:ws:offline:{userId}}），
 * FIFO 顺序保留最近 100 条。
 *
 * <p>增强特性：
 * <ul>
 *   <li>Redis 缓存 TTL 30 天</li>
 *   <li>当 Redis 缓存数量超过阈值时，将溢出消息持久化到数据库表 {@code pmis_msg_offline}，防止 Redis 内存膨胀</li>
 *   <li>用户上线时合并 Redis + DB 两层消息一并推送</li>
 *   <li>数据库消息保留 30 天后自动过期</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMessageService implements OfflineMessageStore {

    /** P3-6: 批量 insert 单批最大条数（pmis_msg_offline 14 列，500 条 ≈ 7000 参数，远低于 PG 65535 上限） */
    private static final int INSERT_BATCH_SIZE = 500;

    private final StringRedisTemplate redisTemplate;
    private final MsgOfflineMapper msgOfflineMapper;

    @Override
    public void cacheOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
            Map<String, Object> envelope = Map.of(
                    "type", type == null ? "UNKNOWN" : type,
                    "payload", payload,
                    "timestamp", System.currentTimeMillis());
            String json = YdszJson.toJson(envelope);
            redisTemplate.opsForList().leftPush(key, json);
            redisTemplate.opsForList().trim(key, 0, WebSocketConstants.WS_OFFLINE_MAX_CACHE - 1);
            redisTemplate.expire(key, Duration.ofSeconds(WebSocketConstants.WS_OFFLINE_TTL_SECONDS));

            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > WebSocketConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD) {
                persistOverflowToDb(userId, key, size);
            }

            log.debug("[WS-Offline] 缓存离线消息: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.warn("[WS-Offline] 缓存离线消息失败，降级忽略: userId={}, err={}", userId, e.getMessage(), e);
        }
    }

    @Override
    public List<String> drainOffline(String userId) {
        if (userId == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();

        // 先从数据库拉取持久化的离线消息
        try {
            List<MsgOfflineDO> dbMessages = msgOfflineMapper.selectList(
                    new LambdaQueryWrapper<MsgOfflineDO>()
                            .eq(MsgOfflineDO::getUserId, userId)
                            .eq(MsgOfflineDO::getStatus, "PENDING")
                            .le(MsgOfflineDO::getExpiredAt, LocalDateTime.now().plusDays(30))
                            .ge(MsgOfflineDO::getExpiredAt, LocalDateTime.now())
                            .orderByAsc(MsgOfflineDO::getMsgTimestamp));
            for (MsgOfflineDO msg : dbMessages) {
                result.add(msg.getPayload());
            }
            if (!dbMessages.isEmpty()) {
                msgOfflineMapper.markPushedByUser(userId);
                log.info("[WS-Offline] 从数据库拉取离线消息: userId={}, count={}", userId, dbMessages.size());
            }
        } catch (Exception e) {
            log.warn("[WS-Offline] 数据库离线消息拉取失败: userId={}, err={}", userId, e.getMessage(), e);
        }

        // 再从 Redis 拉取缓存消息
        String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw != null && !raw.isEmpty()) {
            redisTemplate.delete(key);
            List<String> redisResult = new ArrayList<>(raw);
            Collections.reverse(redisResult);
            result.addAll(redisResult);
        }

        log.info("[WS-Offline] 拉取离线消息: userId={}, total={}", userId, result.size());
        return result;
    }

    @Override
    public long countOffline(String userId) {
        if (userId == null) {
            return 0L;
        }
        long redisCount = 0;
        long dbCount = 0;
        try {
            String key = WebSocketConstants.WS_OFFLINE_KEY_PREFIX + userId;
            Long size = redisTemplate.opsForList().size(key);
            redisCount = size == null ? 0L : size;
        } catch (Exception e) {
            log.debug("[WS-Offline] Redis 计数失败: {}", e.getMessage());
        }
        try {
            Long dbSize = msgOfflineMapper.selectCount(
                    new LambdaQueryWrapper<MsgOfflineDO>()
                            .eq(MsgOfflineDO::getUserId, userId)
                            .eq(MsgOfflineDO::getStatus, "PENDING"));
            dbCount = dbSize == null ? 0L : dbSize;
        } catch (Exception e) {
            log.debug("[WS-Offline] DB 计数失败: {}", e.getMessage());
        }
        return redisCount + dbCount;
    }

    /**
     * 异步将 Redis 溢出的离线消息持久化到数据库。
     *
     * <p>当 Redis List 超过阈值时，将尾部（较旧）的消息写入数据库，
     * 然后从 Redis 中移除已持久化的部分，保持 Redis 缓存新鲜度。
     *
     * <p>P3-6: 改为批量 INSERT，避免逐条 insert 的数据库往返开销。
     *
     * @param userId      用户 ID
     * @param redisKey    Redis key
     * @param currentSize 当前 Redis List 大小
     */
    @Async
    public void persistOverflowToDb(String userId, String redisKey, long currentSize) {
        try {
            long overflowCount = currentSize - WebSocketConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD;
            if (overflowCount <= 0) {
                return;
            }
            List<String> overflowMessages = redisTemplate.opsForList()
                    .range(redisKey, WebSocketConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD, -1);
            if (overflowMessages == null || overflowMessages.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            String tenantId = TenantContext.getTenantId();
            // P3-6: 先构建全部实体（预生成 ID），再批量 insert
            List<MsgOfflineDO> entities = new ArrayList<>(overflowMessages.size());
            for (String json : overflowMessages) {
                MsgOfflineDO offline = new MsgOfflineDO();
                offline.setId(IdWorker.getIdStr());
                offline.setUserId(userId);
                offline.setMsgType("OFFLINE_OVERFLOW");
                offline.setPayload(json);
                offline.setMsgTimestamp(System.currentTimeMillis());
                offline.setStatus("PENDING");
                offline.setExpiredAt(now.plusDays(30));
                offline.setTenantId(tenantId);
                entities.add(offline);
            }
            // 分批批量 insert（防止单条 SQL 参数超过 PG 65535 上限）
            for (int i = 0; i < entities.size(); i += INSERT_BATCH_SIZE) {
                int to = Math.min(i + INSERT_BATCH_SIZE, entities.size());
                msgOfflineMapper.insertBatch(entities.subList(i, to));
            }
            redisTemplate.opsForList().trim(redisKey, 0, WebSocketConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD - 1);
            log.info("[WS-Offline] 溢出消息持久化到数据库: userId={}, count={}", userId, overflowMessages.size());
        } catch (Exception e) {
            log.warn("[WS-Offline] 溢出消息持久化失败: userId={}, err={}", userId, e.getMessage(), e);
        }
    }
}
