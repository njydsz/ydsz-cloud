package com.njydsz.common.exception.alert;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.njydsz.common.exception.custom.AbstractYdszException;
import com.njydsz.common.exception.enums.ExceptionLevel;
import com.njydsz.common.exception.observability.TraceContext;

import java.util.concurrent.ConcurrentHashMap;
/**
 * 异常告警发布器
 *
 * <p>当异常级别为 FATAL 或 ERROR 时，自动发布告警事件给所有注册的
 * {@link ExceptionAlertListener}，支持多渠道告警（钉钉、邮件、短信等）。
 *
 * <p><b>告警收敛策略：</b>
 * <ul>
 *   <li>同一 errorCode 在去重时间窗口内只告警一次</li>
 *   <li>FATAL 级别忽略收敛策略，每次都告警</li>
 *   <li>支持运行时静默期：通过 {@link #enterSilencePeriod(int)} 动态设置</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <pre>{@code
 * @Autowired
 * private ExceptionAlertPublisher alertPublisher;
 *
 * try {
 *     // business logic
 * } catch (SysException e) {
 *     alertPublisher.publishAlert(e);
 *     throw e;
 * }
 * }</pre>
 *
 * <p>注册告警监听器：
 * <pre>{@code
 * @Bean
 * public ExceptionAlertListener dingTalkAlertListener() {
 *     return new DingTalkAlertListener();
 * }
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ExceptionAlertListener
 * @see AbstractYdszException
 */
public class ExceptionAlertPublisher {

    private static final Logger log = LoggerFactory.getLogger(ExceptionAlertPublisher.class);

    private final List<ExceptionAlertListener> listeners = new CopyOnWriteArrayList<>();
    private final long dedupWindowMillis;

    /** 上次告警时间（按 errorCode 分组），有界缓存防止无界增长 */
    private final ConcurrentHashMap<String, Long> lastAlertTime =
            new ConcurrentHashMap<>();

    /** 去重记录最大容量 */
    private static final int MAX_DEDUP_ENTRIES = 10000;

    /** 静默期结束时间（全局） */
    private volatile long silenceUntil = 0;

    /**
     * 创建异常告警发布器
     *
     * @param dedupWindowSeconds 去重时间窗口（秒），同一 errorCode 在此窗口内只告警一次
     */
    public ExceptionAlertPublisher(int dedupWindowSeconds) {
        this.dedupWindowMillis = dedupWindowSeconds * 1000L;
    }

    /**
     * 注册告警监听器
     *
     * @param listener 告警监听器
     */
    public void addListener(ExceptionAlertListener listener) {
        if (listener != null) {
            listeners.add(listener);
        }
    }

    /**
     * 发布异常告警
     *
     * <p>根据异常级别和去重策略决定是否发送告警：
     * <ul>
     *   <li>FATAL 级别：忽略去重和静默期，立即告警</li>
     *   <li>ERROR 级别：应用去重和静默期策略</li>
     *   <li>WARN/INFO 级别：不告警</li>
     * </ul>
     *
     * @param throwable 异常对象
     */
    public void publishAlert(Throwable throwable) {
        if (!shouldAlert(throwable)) {
            return;
        }

        AbstractYdszException ex = (AbstractYdszException) throwable;
        ExceptionLevel level = ex.getLevel();

        // FATAL 级别忽略所有策略
        if (level != ExceptionLevel.FATAL) {
            // 检查静默期
            if (System.currentTimeMillis() < silenceUntil) {
                log.debug("异常告警被静默期抑制 | code={} | level={}", ex.getCode(), level);
                return;
            }

            // 检查去重窗口
            String dedupKey = ex.getCode();
            Long lastTime = lastAlertTime.get(dedupKey);
            long now = System.currentTimeMillis();
            if (lastTime != null && (now - lastTime) < dedupWindowMillis) {
                log.debug("异常告警被去重策略抑制 | code={} | 距上次告警 {}ms", ex.getCode(), now - lastTime);
                return;
            }
            lastAlertTime.put(dedupKey, now);
            // 防止无界增长：超过容量时清理过期记录
            if (lastAlertTime.size() > MAX_DEDUP_ENTRIES) {
                cleanupExpiredDedupEntries();
            }
        }

        ExceptionAlertEvent event = new ExceptionAlertEvent(
                ex.getCode(),
                ex.getKey(),
                ex.getMessage(),
                level,
                ex.getCategory(),
                ex.getHttpStatus(),
                System.currentTimeMillis(),
                TraceContext.getTraceId()
        );

        for (ExceptionAlertListener listener : listeners) {
            try {
                listener.onAlert(event);
            } catch (Exception e) {
                log.error("异常告警监听器执行失败 | listener={} | event={}",
                        listener.getClass().getSimpleName(), event, e);
            }
        }
    }

    /**
     * 判断异常是否需要告警
     *
     * <p>仅 FATAL 和 ERROR 级别的 {@link AbstractYdszException} 才会触发告警。
     *
     * @param throwable 异常对象
     * @return true-需要告警（FATAL/ERROR 级别的 AbstractYdszException）
     */
    public boolean shouldAlert(Throwable throwable) {
        if (!(throwable instanceof AbstractYdszException)) {
            return false;
        }
        ExceptionLevel level = ((AbstractYdszException) throwable).getLevel();
        return level == ExceptionLevel.FATAL || level == ExceptionLevel.ERROR;
    }

    /**
     * 设置全局静默期
     *
     * @param seconds 静默时长（秒）
     */
    public void enterSilencePeriod(int seconds) {
        this.silenceUntil = System.currentTimeMillis() + seconds * 1000L;
        log.warn("异常告警进入静默期 | 持续 {} 秒", seconds);
    }

    /**
     * 退出静默期
     */
    public void exitSilencePeriod() {
        this.silenceUntil = 0;
        log.info("异常告警静默期已结束");
    }

    /**
     * 清理过期的去重记录
     *
     * <p>当去重记录超过最大容量时，移除所有已过期的记录以防止内存泄漏。
     */
    public void cleanupExpiredDedupEntries() {
        long threshold = System.currentTimeMillis() - dedupWindowMillis;
        lastAlertTime.entrySet().removeIf(entry -> entry.getValue() < threshold);
    }
}
