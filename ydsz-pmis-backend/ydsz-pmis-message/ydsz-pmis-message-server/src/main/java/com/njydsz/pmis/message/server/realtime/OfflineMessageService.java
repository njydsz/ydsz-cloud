package com.njydsz.pmis.message.server.realtime;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.pmis.common.security.TenantContext;
import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.message.domain.constant.MessageConstants;
import com.njydsz.pmis.message.domain.entity.config.MsgOfflineDO;
import com.njydsz.pmis.message.infra.mapper.config.MsgOfflineMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * P0-4: 离线消息补偿服务（Redis + DB 双层存储）�? *
 * <p>用户离线时，将待推送消息缓存到 Redis List（{@code pmis:ws:offline:{userId}}），
 * FIFO 顺序保留最�?{@link MessageConstants#WS_OFFLINE_MAX_CACHE} 条�? *
 * <p>P0-3 增强�? * <ul>
 *   <li>Redis 缓存 TTL �?7 天升级到 30 �?/li>
 *   <li>�?Redis 缓存数量超过 {@link MessageConstants#WS_OFFLINE_DB_PERSIST_THRESHOLD} 时，
 *       将溢出消息持久化到数据库�?{@code pmis_msg_offline}，防�?Redis 内存膨胀</li>
 *   <li>用户上线时合�?Redis + DB 两层消息一并推�?/li>
 *   <li>数据库消息保�?30 天后自动过期</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OfflineMessageService {

    private final StringRedisTemplate redisTemplate;
    private final MsgOfflineMapper msgOfflineMapper;

    /**
     * 缓存一条离线消息�?     *
     * <p>优先写入 Redis List；当 Redis 缓存数量超过阈值时�?     * 异步将溢出消息持久化到数据库�?     *
     * @param userId  用户 ID
     * @param type    消息类型标签（如 NOTIFICATION / ALERT�?     * @param payload 消息内容（任意可序列化对象）
     */
    public void cacheOffline(String userId, String type, Object payload) {
        if (userId == null) {
            return;
        }
        try {
            String key = MessageConstants.WS_OFFLINE_KEY_PREFIX + userId;
            Map<String, Object> envelope = Map.of(
                    "type", type == null ? "UNKNOWN" : type,
                    "payload", payload,
                    "timestamp", System.currentTimeMillis());
            String json = JsonUtils.toJson(envelope);
            redisTemplate.opsForList().leftPush(key, json);
            // 保留最�?maxCache 条（FIFO 淘汰�?            redisTemplate.opsForList().trim(key, 0, MessageConstants.WS_OFFLINE_MAX_CACHE - 1);
            // P0-3: TTL 升级�?30 �?            redisTemplate.expire(key, Duration.ofSeconds(MessageConstants.WS_OFFLINE_TTL_SECONDS));

            // P0-3: 检�?Redis 缓存数量，超过阈值时溢出到数据库
            Long size = redisTemplate.opsForList().size(key);
            if (size != null && size > MessageConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD) {
                persistOverflowToDb(userId, key, size);
            }

            log.debug("[WS-Offline] 缓存离线消息: userId={}, type={}", userId, type);
        } catch (Exception e) {
            log.warn("[WS-Offline] 缓存离线消息失败，降级忽�? userId={}, err={}", userId, e.getMessage());
        }
    }

    /**
     * 拉取并清空用户的所有离线消息（Redis + DB 合并，FIFO 顺序：最旧的消息在前）�?     *
     * <p>先从数据库拉�?PENDING 消息，再�?Redis 拉取缓存消息�?     * 合并后标记数据库消息为已推送并清空 Redis 缓存�?     *
     * @param userId 用户 ID
     * @return 离线消息 JSON 列表（最旧在前），无则返回空列表
     */
    public List<String> drainOffline(String userId) {
        if (userId == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();

        // P0-3: 先从数据库拉取持久化的离线消�?        try {
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
            log.warn("[WS-Offline] 数据库离线消息拉取失�? userId={}, err={}", userId, e.getMessage());
        }

        // 再从 Redis 拉取缓存消息
        String key = MessageConstants.WS_OFFLINE_KEY_PREFIX + userId;
        List<String> raw = redisTemplate.opsForList().range(key, 0, -1);
        if (raw != null && !raw.isEmpty()) {
            redisTemplate.delete(key);
            // LPUSH 入队导致顺序反转，反转为时间正序（最旧在前）
            List<String> redisResult = new ArrayList<>(raw);
            java.util.Collections.reverse(redisResult);
            result.addAll(redisResult);
        }

        log.info("[WS-Offline] 拉取离线消息: userId={}, total={}", userId, result.size());
        return result;
    }

    /**
     * 查询用户离线消息数量（Redis + DB 合并）�?     *
     * @param userId 用户 ID
     * @return 离线消息数量
     */
    public long countOffline(String userId) {
        if (userId == null) {
            return 0L;
        }
        long redisCount = 0;
        long dbCount = 0;
        try {
            String key = MessageConstants.WS_OFFLINE_KEY_PREFIX + userId;
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
     * P0-3: 异步�?Redis 溢出的离线消息持久化到数据库�?     *
     * <p>�?Redis List 超过阈值时，将尾部（较旧）的消息写入数据库�?     * 然后�?Redis 中移除已持久化的部分，保�?Redis 缓存新鲜度�?     */
    @Async
    public void persistOverflowToDb(String userId, String redisKey, long currentSize) {
        try {
            long overflowCount = currentSize - MessageConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD;
            if (overflowCount <= 0) {
                return;
            }
            // �?Redis List 尾部取出较旧的消息（RANGE 从尾部开始）
            List<String> overflowMessages = redisTemplate.opsForList()
                    .range(redisKey, MessageConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD, -1);
            if (overflowMessages == null || overflowMessages.isEmpty()) {
                return;
            }
            LocalDateTime now = LocalDateTime.now();
            for (String json : overflowMessages) {
                MsgOfflineDO offline = new MsgOfflineDO();
                offline.setUserId(userId);
                offline.setMsgType("OFFLINE_OVERFLOW");
                offline.setPayload(json);
                offline.setMsgTimestamp(System.currentTimeMillis());
                offline.setStatus("PENDING");
                offline.setExpiredAt(now.plusDays(30));
                offline.setTenantId(TenantContext.getTenantId());
                msgOfflineMapper.insert(offline);
            }
            // �?Redis 中移除已持久化的消息
            redisTemplate.opsForList().trim(redisKey, 0, MessageConstants.WS_OFFLINE_DB_PERSIST_THRESHOLD - 1);
            log.info("[WS-Offline] 溢出消息持久化到数据�? userId={}, count={}", userId, overflowMessages.size());
        } catch (Exception e) {
            log.warn("[WS-Offline] 溢出消息持久化失�? userId={}, err={}", userId, e.getMessage());
        }
    }
}
