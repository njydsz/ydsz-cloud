package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.workflow.service.FlowJoinTokenService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * GAP-P2: 并行网关 join 令牌服务实现（Redis）
 *
 * <p>使用 Redis 原子 INCR 维护 join 节点的已到达分支计数，
 * 配合独立的分支总数 key 实现精确聚合判断。
 *
 * <p>Key 设计：
 * <ul>
 *   <li>到达计数：{@code flow:join:{instanceId}:{joinNodeCode}} —— INCR 原子自增</li>
 *   <li>分支总数：{@code flow:join:{instanceId}:{joinNodeCode}:total} —— 初始化时写入</li>
 * </ul>
 * 两个 key 均设置 7 天 TTL，防止异常流程导致计数器永久残留。
 *
 * <p>容错策略：所有方法对 Redis 异常做降级处理。
 * <ul>
 *   <li>{@link #allArrived} 在 Redis 不可用或未初始化时返回 false（fail-safe：不提前聚合）</li>
 *   <li>{@link #arriveToken} 在 Redis 不可用时返回 false，调用方可重试或走兜底逻辑</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.1.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowJoinTokenServiceImpl implements FlowJoinTokenService {

    /** 到达计数 key 前缀：flow:join:{instanceId}:{joinNodeCode} */
    private static final String KEY_PREFIX = "flow:join:";
    /** 分支总数 key 后缀 */
    private static final String TOTAL_SUFFIX = ":total";
    /** 默认 TTL：7 天 */
    private static final Duration TTL = Duration.ofDays(7);

    private final StringRedisTemplate redisTemplate;

    // ============================== 接口实现 ==============================

    /**
     * 初始化 join 令牌：写入分支总数并重置到达计数
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @param branchCount  并行分支数（&lt;=0 时按 1 处理）
     */
    @Override
    public void initTokens(Long instanceId, String joinNodeCode, int branchCount) {
        if (!isValidParam(instanceId, joinNodeCode)) {
            return;
        }
        int total = Math.max(1, branchCount);
        String totalKey = buildTotalKey(instanceId, joinNodeCode);
        String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
        try {
            redisTemplate.opsForValue().set(totalKey, String.valueOf(total), TTL);
            redisTemplate.opsForValue().set(arrivedKey, "0", TTL);
            log.info("[FlowJoinToken] 初始化 join 令牌 instanceId={} node={} branchCount={}",
                    instanceId, joinNodeCode, total);
        } catch (Exception e) {
            log.warn("[FlowJoinToken] 初始化令牌失败 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
        }
    }

    /**
     * 标记一个分支已到达：INCR 到达计数并判断是否全部到达
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @return true=本次到达后全部分支已到达（可聚合）；false=仍有分支未到达或 Redis 异常
     */
    @Override
    public boolean arriveToken(Long instanceId, String joinNodeCode) {
        if (!isValidParam(instanceId, joinNodeCode)) {
            return false;
        }
        String arrivedKey = buildArrivedKey(instanceId, joinNodeCode);
        try {
            Long arrived = redisTemplate.opsForValue().increment(arrivedKey);
            // 新建 key 时 INCR 不会带 TTL，防御性补设
            Long ttl = redisTemplate.getExpire(arrivedKey);
            if (ttl == null || ttl < 0) {
                redisTemplate.expire(arrivedKey, TTL);
            }
            int total = readTotal(instanceId, joinNodeCode);
            boolean allArrived = arrived != null && arrived >= total;
            log.debug("[FlowJoinToken] 分支到达 instanceId={} node={} arrived={}/{} allArrived={}",
                    instanceId, joinNodeCode, arrived, total, allArrived);
            return allArrived;
        } catch (Exception e) {
            log.warn("[FlowJoinToken] 标记到达失败 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
            return false;
        }
    }

    /**
     * 检查是否所有分支都已到达
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     * @return true=全部到达可聚合；false=未全部到达 / 未初始化 / Redis 异常
     */
    @Override
    public boolean allArrived(Long instanceId, String joinNodeCode) {
        if (!isValidParam(instanceId, joinNodeCode)) {
            return false;
        }
        try {
            int total = readTotal(instanceId, joinNodeCode);
            String arrivedStr = redisTemplate.opsForValue().get(buildArrivedKey(instanceId, joinNodeCode));
            if (arrivedStr == null) {
                return false;
            }
            long arrived;
            try {
                arrived = Long.parseLong(arrivedStr);
            } catch (NumberFormatException e) {
                log.warn("[FlowJoinToken] 到达计数非数字 instanceId={} node={} raw={}",
                        instanceId, joinNodeCode, arrivedStr);
                return false;
            }
            return arrived >= total;
        } catch (Exception e) {
            log.warn("[FlowJoinToken] 检查全部到达失败 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
            return false;
        }
    }

    /**
     * 清除 join 令牌：删除到达计数与分支总数 key
     *
     * @param instanceId   流程实例 ID
     * @param joinNodeCode join 节点编码
     */
    @Override
    public void clearTokens(Long instanceId, String joinNodeCode) {
        if (!isValidParam(instanceId, joinNodeCode)) {
            return;
        }
        try {
            redisTemplate.delete(buildArrivedKey(instanceId, joinNodeCode));
            redisTemplate.delete(buildTotalKey(instanceId, joinNodeCode));
            log.info("[FlowJoinToken] 清除 join 令牌 instanceId={} node={}",
                    instanceId, joinNodeCode);
        } catch (Exception e) {
            log.warn("[FlowJoinToken] 清除令牌失败 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
        }
    }

    /**
     * 检查 join 令牌是否已初始化（total key 是否存在）
     */
    @Override
    public boolean isInitialized(Long instanceId, String joinNodeCode) {
        if (!isValidParam(instanceId, joinNodeCode)) {
            return false;
        }
        try {
            Boolean exists = redisTemplate.hasKey(buildTotalKey(instanceId, joinNodeCode));
            return Boolean.TRUE.equals(exists);
        } catch (Exception e) {
            log.warn("[FlowJoinToken] 检查初始化状态失败 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
            return false;
        }
    }

    // ============================== 私有辅助 ==============================

    /** 读取分支总数，未初始化时返回 Integer.MAX_VALUE（避免误判为已全部到达） */
    private int readTotal(Long instanceId, String joinNodeCode) {
        try {
            String totalStr = redisTemplate.opsForValue().get(buildTotalKey(instanceId, joinNodeCode));
            if (totalStr == null) {
                // 未初始化：返回最大值，确保 allArrived 返回 false（fail-safe）
                log.warn("[FlowJoinToken] 分支总数未初始化 instanceId={} node={}",
                        instanceId, joinNodeCode);
                return Integer.MAX_VALUE;
            }
            return Integer.parseInt(totalStr);
        } catch (NumberFormatException e) {
            log.warn("[FlowJoinToken] 分支总数非数字 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
            return Integer.MAX_VALUE;
        } catch (Exception e) {
            log.warn("[FlowJoinToken] 读取分支总数失败 instanceId={} node={} err={}",
                    instanceId, joinNodeCode, e.getMessage());
            return Integer.MAX_VALUE;
        }
    }

    /** 参数合法性校验 */
    private boolean isValidParam(Long instanceId, String joinNodeCode) {
        if (instanceId == null) {
            log.warn("[FlowJoinToken] instanceId 为空，跳过");
            return false;
        }
        if (joinNodeCode == null || joinNodeCode.isBlank()) {
            log.warn("[FlowJoinToken] joinNodeCode 为空，跳过 instanceId={}", instanceId);
            return false;
        }
        return true;
    }

    /** 构建到达计数 key：flow:join:{instanceId}:{joinNodeCode} */
    private String buildArrivedKey(Long instanceId, String joinNodeCode) {
        return KEY_PREFIX + instanceId + ":" + joinNodeCode;
    }

    /** 构建分支总数 key：flow:join:{instanceId}:{joinNodeCode}:total */
    private String buildTotalKey(Long instanceId, String joinNodeCode) {
        return buildArrivedKey(instanceId, joinNodeCode) + TOTAL_SUFFIX;
    }
}
