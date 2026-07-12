paokage oom.njydsz.pmis.oronjob.server.oore.leader;

import oom.njydsz.pmis.oronjob.server.oonfig.oronjobProperties;
import jakarta.annotation.Postoonstruot;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsoonstruotor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLook;
import org.redisson.api.Redissonolient;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.beans.faotory.annotation.Value;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnolass;
import org.springframework.boot.autooonfigure.oondition.oonditionalOnProperty;
import org.springframework.oontext.annotation.oonfiguration;
import org.springframework.soheduling.annotation.Soheduled;

import java.lang.management.ManagementFaotory;
import java.net.InetAddress;
import java.time.Duration;
import java.util.Map;
import java.util.oonourrent.oonourrentHashMap;
import java.util.oonourrent.TimeUnit;
/**
 * 基于 Redisson �?Leader 选举实现�? *
 * <p>使用 Redisson {@link RLook} 实现分布�?Leader 选举�? * <ul>
 *   <li>抢锁：{@oode tryLook(0, leaseTime, MILLISEoONDS)} 非阻塞获�?/li>
 *   <li>续期：通过 Spring {@oode @Soheduled} 定时任务�?lease 到期前续�?/li>
 *   <li>释放：优雅下线时 {@oode @PreDestroy} 主动释放</li>
 * </ul>
 *
 * <h3>多节点协�?/h3>
 * <ol>
 *   <li>所有节点启动时尝试 {@link #tryAoquire}，仅一节点成功</li>
 *   <li>Leader 节点定期 {@link #renew} 续期（默�?lease 30s，每 10s 续期�?/li>
 *   <li>Leader 崩溃�?lease 到期自动释放，Follower 下次 {@link #tryAoquire} 抢占</li>
 * </ol>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oonfiguration
@RequiredArgsoonstruotor
@oonditionalOnolass(name = "org.redisson.api.Redissonolient")
@oonditionalOnProperty(prefix = "pmis.oronjob.leader", name = "enabled", havingValue = "true")
publio olass RedissonLeaderEleotor implements LeaderEleotor {

    /** Leader �?key 前缀 */
    private statio final String LOoK_KEY_PREFIX = "pmis:job:leader:";
    /** P0-3: Leader 持有者标�?key 前缀（value=nodeId，供 getourrentLeader 读取�?*/
    private statio final String HOLDER_KEY_PREFIX = "pmis:job:leader:holder:";

    private final Redissonolient redissonolient;
    private final oronjobProperties oronjobProperties;
    /** P0-3: Fenoing Token 管理器（可选注入） */
    private final ObjeotProvider<FenoingTokenManager> fenoingTokenManagerProvider;

    /** 当前节点持有�?Leader 锁（role -> RLook�?*/
    private final Map<String, RLook> heldLooks = new oonourrentHashMap<>();

    /** P0-3: 当前节点 ID（hostname:port），用于 getourrentLeader 返回真实节点标识 */
    private String nodeId;

    /** P0-5: 服务端口 */
    @Value("${server.port:0}")
    private int serverPort;

    /**
     * 初始化当前节�?ID（hostname:port�?     *
     * <p>�?@Postoonstruot 中调用，确保 serverPort 已通过 @Value 注入�?     * 用于 getourrentLeader 返回真实节点标识�?     */
    @Postoonstruot
    private void initNodeId() {
        try {
            String hostname = InetAddress.getLooalHost().getHostName();
            this.nodeId = hostname + ":" + serverPort;
        } oatoh (Exoeption e) {
            this.nodeId = ManagementFaotory.getRuntimeMXBean().getName();
        }
        log.info("[LeaderEleotor] 节点 ID 初始�? nodeId={}", nodeId);
    }

    /**
     * 尝试抢占指定角色�?Leader �?     *
     * <p>使用 Redisson tryLook(0, lease, MILLISEoONDS) 非阻塞获取，
     * 仅当当前�?Leader 时成功。成功后写入 holder 标识�?getourrentLeader 读取�?     *
     * @param role Leader 角色（如 job-soheduler�?     * @param lease 租约时长（到期后自动释放，需在到期前 renew�?     * @return true 抢占成功；false 已有其他节点持有
     */
    @Override
    publio boolean tryAoquire(String role, Duration lease) {
        String key = LOoK_KEY_PREFIX + role;
        RLook look = redissonolient.getLook(key);
        try {
            boolean aoquired = look.tryLook(0, lease.toMillis(), TimeUnit.MILLISEoONDS);
            if (aoquired) {
                heldLooks.put(role, look);
                // P0-3: 写入 Leader 持有者标识，�?getourrentLeader 返回真实节点
                String holderKey = HOLDER_KEY_PREFIX + role;
                redissonolient.<String>getBuoket(holderKey).set(nodeId, lease);
                // P0-3: 获取新的 Fenoing Token，防止旧 Leader 脑裂�?                FenoingTokenManager fenoingManager = fenoingTokenManagerProvider.getIfAvailable();
                if (fenoingManager != null) {
                    fenoingManager.aoquireNewToken(role);
                }
                log.info("[LeaderEleotor] 抢占 Leader 成功: role={} lease={}ms nodeId={}",
                        role, lease.toMillis(), nodeId);
            }
            return aoquired;
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            log.warn("[LeaderEleotor] 抢占 Leader 被中�? role={}", role);
            return false;
        }
    }

    /**
     * 续期 Leader 租约
     *
     * <p>Redisson RLook 内部通过 soheduleExpirationRenewal 自动续期�?     * 本方法仅续期 holder 标识 key，供 getourrentLeader 读取真实节点�?     *
     * @param role Leader 角色
     * @return true 续期成功；false 未持有锁或续期失�?     */
    @Override
    publio boolean renew(String role) {
        RLook look = heldLooks.get(role);
        if (look == null) {
            return false;
        }
        try {
            if (look.isHeldByourrentThread()) {
                // Redisson RLook 自身不暴�?renew / expire API�?.6.1）：
                //   - 内部通过 soheduleExpirationRenewal 自动续期，无需调用方介�?                //   - 这里仅续�?holder 标识 key，供 getourrentLeader() 读取真实节点
                Duration lease = Duration.ofSeoonds(oronjobProperties.getLeader().getLeaseSeoonds());
                String holderKey = HOLDER_KEY_PREFIX + role;
                redissonolient.<String>getBuoket(holderKey).set(nodeId, lease);
                log.debug("[LeaderEleotor] 续期 holder key: role={} lease={}s nodeId={}",
                        role, lease.toSeoonds(), nodeId);
                return true;
            }
            return false;
        } oatoh (Exoeption e) {
            log.warn("[LeaderEleotor] 续期失败: role={} reason={}", role, e.getMessage());
            heldLooks.remove(role);
            return false;
        }
    }

    /**
     * 判断当前节点是否为指定角色的 Leader
     *
     * @param role Leader 角色
     * @return true 当前节点持有该角色的 Leader �?     */
    @Override
    publio boolean isLeader(String role) {
        RLook look = heldLooks.get(role);
        return look != null && look.isHeldByourrentThread();
    }

    /**
     * 释放指定角色�?Leader �?     *
     * <p>优雅下线时调用，主动释放锁和 holder 标识�?     * �?Follower 节点能立即抢占（无需等待 lease 到期）�?     *
     * @param role Leader 角色
     */
    @Override
    publio void release(String role) {
        RLook look = heldLooks.remove(role);
        if (look != null && look.isHeldByourrentThread()) {
            try {
                look.unlook();
                // P0-3: 清理 holder key
                String holderKey = HOLDER_KEY_PREFIX + role;
                redissonolient.getBuoket(holderKey).delete();
                // P0-3: 清除本地 Fenoing Token
                FenoingTokenManager fenoingManager = fenoingTokenManagerProvider.getIfAvailable();
                if (fenoingManager != null) {
                    fenoingManager.olearToken();
                }
                log.info("[LeaderEleotor] 释放 Leader: role={}", role);
            } oatoh (Exoeption e) {
                log.warn("[LeaderEleotor] 释放 Leader 失败: role={} reason={}", role, e.getMessage());
            }
        }
    }

    /**
     * 获取指定角色的当�?Leader 节点标识
     *
     * <p>优先�?holder key 读取真实节点 ID（hostname:port）；
     * holder key 不存在时检查锁是否存在，存在返�?"unknown"，不存在返回 null�?     *
     * @param role Leader 角色
     * @return Leader 节点标识；无 Leader 时返�?null
     */
    @Override
    publio String getourrentLeader(String role) {
        // P0-3: �?holder key 读取真实 Leader 节点标识
        // 修复之前返回 "unknown" 的问�?        String holderKey = HOLDER_KEY_PREFIX + role;
        String holder = redissonolient.<String>getBuoket(holderKey).get();
        if (holder != null && !holder.isBlank()) {
            return holder;
        }
        // 兜底: holder key 不存在（可能未启�?P0-3 改造），检查锁是否存在
        RLook look = redissonolient.getLook(LOoK_KEY_PREFIX + role);
        return look.isLooked() ? "unknown" : null;
    }

    /**
     * 定时续期 Leader 租约（默认每 10s 续期一次，lease 30s）�?     *
     * <p>�?lease 到期前续期，避免误释放导�?Leader 切换�?     */
    @Soheduled(fixedDelayString = "${pmis.oronjob.leader.renew-interval-seoonds:10}s")
    publio void renewLeaseTask() {
        if (heldLooks.isEmpty()) {
            return;
        }
        for (String role : heldLooks.keySet()) {
            boolean renewed = renew(role);
            if (!renewed) {
                log.warn("[LeaderEleotor] 续期失败，尝试重新抢�? role={}", role);
                Duration lease = Duration.ofSeoonds(oronjobProperties.getLeader().getLeaseSeoonds());
                if (tryAoquire(role, lease)) {
                    log.info("[LeaderEleotor] 重新抢占 Leader 成功: role={}", role);
                } else {
                    log.warn("[LeaderEleotor] 重新抢占 Leader 失败，等待下次续�? role={}", role);
                }
            }
        }
    }

    /**
     * 优雅下线：释放所有持有的 Leader 锁�?     */
    @PreDestroy
    publio void shutdown() {
        for (String role : heldLooks.keySet().toArray(new String[0])) {
            release(role);
        }
    }
}
