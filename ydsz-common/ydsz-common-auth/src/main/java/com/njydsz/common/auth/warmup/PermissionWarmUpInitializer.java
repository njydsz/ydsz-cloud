package com.njydsz.common.auth.warmup;

import java.util.List;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import com.njydsz.common.auth.config.AuthProperties;
import com.njydsz.common.auth.model.RolePermissions;
import com.njydsz.common.auth.service.RolePermissionLoader;
import com.njydsz.common.util.string.StringUtils;

/**
 * 权限预热初始化器。
 *
 * <p>在应用启动完成后，延迟加载指定角色的权限到本地缓存，
 * 减少首次权限校验的请求延迟。
 *
 * <p><b>工作流程：</b>
 * <ol>
 *   <li>监听 {@link ApplicationReadyEvent} 事件</li>
 *   <li>延迟 {@code warmUpDelay} 毫秒执行预热</li>
 *   <li>逐个加载 {@code warmUpRoleIds} 中的角色权限</li>
 *   <li>记录预热结果日志</li>
 * </ol>
 *
 * <p><b>配置项：</b>
 * <ul>
 *   <li>{@code ydsz.auth.warmUpEnabled}：是否启用预热（默认 true）</li>
 *   <li>{@code ydsz.auth.warmUpRoleIds}：需要预热的角色 ID 列表</li>
 *   <li>{@code ydsz.auth.warmUpDelay}：预热延迟时间（毫秒，默认 3000）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 */
public class PermissionWarmUpInitializer {

    private static final Logger log = LoggerFactory.getLogger(PermissionWarmUpInitializer.class);

    private final AuthProperties properties;
    private final RolePermissionLoader rolePermissionLoader;
    private final ThreadPoolTaskExecutor taskExecutor;

    public PermissionWarmUpInitializer(AuthProperties properties,
                                       RolePermissionLoader rolePermissionLoader) {
        this.properties = properties;
        this.rolePermissionLoader = rolePermissionLoader;
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setThreadNamePrefix("auth-warmup-");
        executor.setDaemon(true);
        executor.setWaitForTasksToCompleteOnShutdown(true);
        executor.setAwaitTerminationSeconds(5);
        executor.initialize();
        this.taskExecutor = executor;
    }

    /**
     * 应用关闭时优雅关闭线程池。
     */
    @PreDestroy
    public void shutdown() {
        log.info("权限预热线程池正在关闭...");
        taskExecutor.shutdown();
        log.info("权限预热线程池已关闭");
    }

    /**
     * 监听应用就绪事件，触发权限预热。
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady(ApplicationReadyEvent event) {
        if (!Boolean.TRUE.equals(properties.getWarmUpEnabled())) {
            log.info("权限预热功能已禁用，跳过预热");
            return;
        }

        List<String> roleIds = properties.getWarmUpRoleIds();
        if (roleIds == null || roleIds.isEmpty()) {
            log.info("未配置需要预热的角色 ID，跳过预热");
            return;
        }

        Long delayMs = properties.getWarmUpDelay();
        long delay = (delayMs == null || delayMs < 0) ? 3000L : delayMs;

        log.info("权限预热任务已调度，延迟 {} 毫秒后执行，预热角色数：{}", delay, roleIds.size());

        // 延迟执行，避免与应用启动竞争资源
        taskExecutor.execute(() -> {
            try {
                TimeUnit.MILLISECONDS.sleep(delay);
                executeWarmUp(roleIds);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("权限预热任务被中断");
            } catch (Exception e) {
                log.error("权限预热任务执行异常: {}", e.getMessage(), e);
            }
        });
    }

    /**
     * 执行权限预热。
     *
     * @param roleIds 需要预热的角色 ID 列表
     */
    private void executeWarmUp(List<String> roleIds) {
        log.info("开始执行权限预热，角色列表：{}", roleIds);

        long startTime = System.currentTimeMillis();
        int successCount = 0;
        int failCount = 0;

        for (String roleId : roleIds) {
            if (StringUtils.isBlank(roleId)) {
                log.warn("跳过空白的角色 ID");
                failCount++;
                continue;
            }

            try {
                RolePermissions permissions = rolePermissionLoader.loadByRoleCode(roleId.trim());
                if (permissions != null) {
                    successCount++;
                    log.debug("角色权限预热成功: roleCode={}, menuPerms={}, buttonPerms={}, apiPerms={}",
                            roleId,
                            permissions.getMenuPermissions() != null ? permissions.getMenuPermissions().size() : 0,
                            permissions.getButtonPermissions() != null ? permissions.getButtonPermissions().size() : 0,
                            permissions.getApiPermissions() != null ? permissions.getApiPermissions().size() : 0);
                } else {
                    failCount++;
                    log.warn("角色权限预热返回 null: roleCode={}", roleId);
                }
            } catch (Exception e) {
                failCount++;
                log.error("角色权限预热失败: roleCode={}, error={}", roleId, e.getMessage(), e);
            }
        }

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("权限预热完成，耗时：{} ms，成功：{}，失败：{}", elapsed, successCount, failCount);
    }
}
