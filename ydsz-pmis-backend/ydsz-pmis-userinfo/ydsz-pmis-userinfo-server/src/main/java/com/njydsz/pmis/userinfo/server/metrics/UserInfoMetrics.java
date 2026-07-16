package com.njydsz.pmis.userinfo.server.metrics;

import java.util.concurrent.atomic.AtomicLong;

import jakarta.annotation.PostConstruct;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;

/**
 * 用户中心核心业务指标采集（P1-2）
 *
 * <p>覆盖认证、用户、会话、权限等核心业务指标：
 * <ul>
 *   <li>Counter: 登录成功/失败次数、Token 刷新次数、登出次数、密码修改次数</li>
 *   <li>Gauge: 在线用户数、活跃会话数、锁定账号数、停用账号数</li>
 *   <li>Timer: 登录耗时、Token 验证耗时</li>
 * </ul>
 *
 * <p>所有指标前缀 {@code pmis_user_}，便于在 Grafana 看板中筛选。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class UserInfoMetrics extends AbstractModuleMetrics {

    private final JdbcTemplate jdbcTemplate;

    // ============================== Gauge 指标 ==============================

    /** 在线用户数（活跃会话去重） */
    private final AtomicLong onlineUsers = new AtomicLong(0);
    /** 活跃会话数 */
    private final AtomicLong activeSessions = new AtomicLong(0);
    /** 锁定账号数 */
    private final AtomicLong lockedAccounts = new AtomicLong(0);
    /** 停用账号数 */
    private final AtomicLong disabledAccounts = new AtomicLong(0);
    /** 总用户数 */
    private final AtomicLong totalUsers = new AtomicLong(0);

    public UserInfoMetrics(MeterRegistry meterRegistry, JdbcTemplate jdbcTemplate) {
        super(meterRegistry, "pmis_user_");
        this.jdbcTemplate = jdbcTemplate;
    }

    @PostConstruct
    public void init() {
        Gauge.builder("pmis_user_online_count", onlineUsers, AtomicLong::doubleValue)
                .description("在线用户数（活跃会话去重）")
                .register(registry);

        Gauge.builder("pmis_user_active_sessions", activeSessions, AtomicLong::doubleValue)
                .description("活跃会话数")
                .register(registry);

        Gauge.builder("pmis_user_locked_accounts", lockedAccounts, AtomicLong::doubleValue)
                .description("锁定账号数")
                .register(registry);

        Gauge.builder("pmis_user_disabled_accounts", disabledAccounts, AtomicLong::doubleValue)
                .description("停用账号数")
                .register(registry);

        Gauge.builder("pmis_user_total", totalUsers, AtomicLong::doubleValue)
                .description("总用户数")
                .register(registry);

        log.info("[UserInfoMetrics] 指标注册完成: pmis_user_online_count, pmis_user_active_sessions, pmis_user_locked_accounts, pmis_user_disabled_accounts, pmis_user_total");
    }

    /**
     * 每分钟刷新 Gauge 指标
     */
    @Scheduled(fixedDelay = 60_000)
    public void refreshGauges() {
        try {
            // 活跃会话数
            activeSessions.set(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pmis_user_session WHERE status = 'ACTIVE' AND expire_at > NOW()",
                            Long.class));
            // 在线用户数（去重）
            onlineUsers.set(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(DISTINCT user_id) FROM pmis_user_session WHERE status = 'ACTIVE' AND expire_at > NOW()",
                            Long.class));
            // 锁定账号数
            lockedAccounts.set(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pmis_user_account WHERE locked_until IS NOT NULL AND locked_until > NOW()",
                            Long.class));
            // 停用账号数
            disabledAccounts.set(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pmis_user_account WHERE status = 'DISABLED'",
                            Long.class));
            // 总用户数
            totalUsers.set(
                    jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM pmis_user_account WHERE deleted = 0",
                            Long.class));
        } catch (Exception e) {
            log.warn("[UserInfoMetrics] 指标刷新失败: {}", e.getMessage());
        }
    }

    // ============================== Counter 方法 ==============================

    /**
     * 记录登录成功
     *
     * @param clientType 客户端类型（PC/APP/H5）
     */
    public void recordLoginSuccess(String clientType) {
        incrementCounter("login_success_total", "client_type", safe(clientType));
    }

    /**
     * 记录登录失败
     *
     * @param reason 失败原因（USER_NOT_FOUND / PASSWORD_INCORRECT / USER_LOCKED / USER_DISABLED）
     */
    public void recordLoginFailure(String reason) {
        incrementCounter("login_failure_total", "reason", safe(reason));
    }

    /**
     * 记录 Token 刷新
     */
    public void recordTokenRefresh() {
        incrementCounter("token_refresh_total");
    }

    /**
     * 记录登出
     */
    public void recordLogout() {
        incrementCounter("logout_total");
    }

    /**
     * 记录密码修改
     *
     * @param trigger 触发方式（SELF / ADMIN / EXPIRED）
     */
    public void recordPasswordChange(String trigger) {
        incrementCounter("password_change_total", "trigger", safe(trigger));
    }

    /**
     * 记录账号锁定
     */
    public void recordAccountLocked() {
        incrementCounter("account_locked_total");
    }

    /**
     * 记录会话踢出
     *
     * @param reason 踢出原因（CONCURRENT_LIMIT / KICK_OTHERS / EXPIRED）
     */
    public void recordSessionKicked(String reason) {
        incrementCounter("session_kicked_total", "reason", safe(reason));
    }

    // ============================== Timer 方法 ==============================

    /**
     * 记录登录耗时
     *
     * @param elapsedMs 耗时（毫秒）
     */
    public void recordLoginDuration(long elapsedMs) {
        recordTimer("login_duration_seconds", elapsedMs);
    }

    /**
     * 记录 Token 验证耗时
     *
     * @param elapsedMs 耗时（毫秒）
     */
    public void recordTokenValidationDuration(long elapsedMs) {
        recordTimer("token_validation_duration_seconds", elapsedMs);
    }

}
