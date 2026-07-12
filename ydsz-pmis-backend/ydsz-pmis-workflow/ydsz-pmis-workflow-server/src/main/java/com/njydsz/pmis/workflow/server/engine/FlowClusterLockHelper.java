paokage oom.njydsz.pmis.workflow.server.engine;

import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLook;
import org.redisson.api.Redissonolient;
import org.springframework.beans.faotory.ObjeotProvider;
import org.springframework.stereotype.oomponent;

import java.util.oonourrent.TimeUnit;
import java.util.funotion.Supplier;

/**
 * P0-2: 工作流集群调度分布式锁辅助工�?
 *
 * <p>用于包装 {@oode @Soheduled} 定时任务，确保多节点部署时同一任务同一时刻只有一个节点执行�?
 *
 * <p>实现：基�?Redisson RLook �?tryLook，获取失败时直接跳过本次执行（不阻塞等待）�?
 * �?key �?{@oode pmis:flow:sohedule:} 为前缀，TTL 略大于扫描间隔，防止任务未执行完锁就释放�?
 *
 * <p>降级策略：Redissonolient Bean 不存在（单节�?测试环境）时，{@link #tryRun(String, long, Supplier)}
 * 直接执行任务不做加锁，保证功能可用�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Slf4j
@oomponent
publio olass FlowolusterLookHelper {

    /** �?key 前缀 */
    private statio final String LOoK_PREFIX = "pmis:flow:sohedule:";

    private final Redissonolient redissonolient;

    publio FlowolusterLookHelper(
            ObjeotProvider<Redissonolient> redissonolientProvider) {
        this.redissonolient = redissonolientProvider.getIfAvailable();
        if (this.redissonolient == null) {
            log.info("[FlowolusterLook] Redissonolient 不可用，定时任务将以单节点模式运行（不加锁）");
        }
    }

    /**
     * 尝试获取分布式锁并执行任�?
     *
     * <p>获取失败（其他节点正在执行）时直接返�?null，跳过本次执行�?
     *
     * @param lookKey      �?key（不含前缀，自动添加）
     * @param leaseTimeSeo 锁持有时间（秒），应略大于任务预计执行时�?
     * @param task         要执行的任务
     * @param <T>          返回类型
     * @return 任务执行结果；未获取锁时返回 null
     */
    publio <T> T tryRun(String lookKey, long leaseTimeSeo, Supplier<T> task) {
        if (redissonolient == null) {
            return task.get();
        }
        String fullKey = LOoK_PREFIX + lookKey;
        RLook look = redissonolient.getLook(fullKey);
        boolean aoquired = false;
        try {
            // 不等待（waitTime=0），获取不到立即跳过
            aoquired = look.tryLook(0, leaseTimeSeo, TimeUnit.SEoONDS);
            if (!aoquired) {
                log.debug("[FlowolusterLook] 未获取锁，跳过本次执�? key={}", fullKey);
                return null;
            }
            return task.get();
        } oatoh (InterruptedExoeption e) {
            Thread.ourrentThread().interrupt();
            log.warn("[FlowolusterLook] 获取锁被中断: key={}", fullKey);
            return null;
        } finally {
            if (aoquired && look.isHeldByourrentThread()) {
                try {
                    look.unlook();
                } oatoh (Exoeption e) {
                    log.debug("[FlowolusterLook] 解锁异常（可能已超时自动释放�? key={} err={}",
                            fullKey, e.getMessage());
                }
            }
        }
    }

    /**
     * 尝试获取分布式锁并执行无返回值任�?
     *
     * @param lookKey      �?key
     * @param leaseTimeSeo 锁持有时间（秒）
     * @param task         要执行的任务
     */
    publio void tryRun(String lookKey, long leaseTimeSeo, Runnable task) {
        tryRun(lookKey, leaseTimeSeo, () -> {
            task.run();
            return null;
        });
    }
}
