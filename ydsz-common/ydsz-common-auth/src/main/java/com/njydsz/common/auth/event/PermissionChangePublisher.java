package com.njydsz.common.auth.event;

import java.net.InetAddress;
import java.util.Set;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.redis.core.RedisTemplate;

import com.njydsz.common.util.string.StringUtils;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 权限变更事件发布器。
 *
 * <p>负责将权限变更事件同步到所有集群节点：
 * <ol>
 *   <li>通过 Spring ApplicationEventPublisher 发布本地事件（触发本地缓存失效）</li>
 *   <li>通过 Redis Pub/Sub 通知其他节点（触发其他节点缓存失效）</li>
 * </ol>
 *
 * <p><b>废弃原因：</b>Redis Pub/Sub 不保证送达，且需要业务代码显式调用发布方法。
 * Redis Keyspace Notification 能够在 Redis 数据变更时精确触发缓存失效，更加可靠。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated 自 3.0.0 起标记废弃，计划 4.0.0 移除。
 *             多节点缓存同步目标：Redis Keyspace Notification。
 *
 * @see PermissionChangedEvent
 * @see PermissionChangeListener
 */
@Deprecated(forRemoval = true, since = "3.0.0")
@Slf4j
@RequiredArgsConstructor
public class PermissionChangePublisher {

    private static final String PERMISSION_CHANGE_CHANNEL = "ydsz-auth:permission:changed";

    private final ApplicationEventPublisher applicationEventPublisher;
    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 发布角色菜单/按钮/接口权限变更事件。
     *
     * @param roleCode 变更的角色编码
     */
    public void publishRolePermissionChanged(String roleCode) {
        publish(roleCode, PermissionChangedEvent.PermissionChangeType.ROLE_PERMISSION_CHANGED, null);
    }

    /**
     * 发布角色数据权限变更事件。
     *
     * @param roleCode 变更的角色编码
     */
    public void publishRoleDataScopeChanged(String roleCode) {
        publish(roleCode, PermissionChangedEvent.PermissionChangeType.ROLE_DATA_SCOPE_CHANGED, null);
    }

    /**
     * 发布角色列权限变更事件。
     *
     * @param roleCode 变更的角色编码
     */
    public void publishRoleColumnPermissionChanged(String roleCode) {
        publish(roleCode, PermissionChangedEvent.PermissionChangeType.ROLE_COLUMN_PERMISSION_CHANGED, null);
    }

    /**
     * 发布角色删除事件（所有相关权限缓存都应清除）。
     *
     * @param roleCode 被删除的角色编码
     */
    public void publishRoleDeleted(String roleCode) {
        publish(roleCode, PermissionChangedEvent.PermissionChangeType.ROLE_DELETED, null);
    }

    /**
     * 发布自定义权限变更事件。
     *
     * @param roleCode 变更的角色编码
     * @param changeType 变更类型
     * @param affectedPermissionTypes 受影响的权限类型（可为 null，表示全部）
     */
    public void publish(String roleCode, PermissionChangedEvent.PermissionChangeType changeType,
                       Set<String> affectedPermissionTypes) {
        if (StringUtils.isBlank(roleCode)) {
            log.warn("权限变更事件发布失败：roleCode 不能为空");
            return;
        }
        if (changeType == null) {
            log.warn("权限变更事件发布失败：changeType 不能为空");
            return;
        }

        String sourceNode = resolveLocalNode();
        PermissionChangedEvent event = new PermissionChangedEvent(
                roleCode, changeType, affectedPermissionTypes, sourceNode);

        log.info("发布权限变更事件：{}", event);

        applicationEventPublisher.publishEvent(event);

        try {
            String message = buildMessage(event);
            redisTemplate.convertAndSend(PERMISSION_CHANGE_CHANNEL, message);
            log.debug("权限变更事件已发布到 Redis Pub/Sub：channel={}, message={}", PERMISSION_CHANGE_CHANNEL, message);
        } catch (Exception e) {
            log.error("权限变更事件发布到 Redis Pub/Sub 失败：{}", e.getMessage(), e);
        }
    }

    private String resolveLocalNode() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String buildMessage(PermissionChangedEvent event) {
        String affectedTypes = event.getAffectedPermissionTypes() != null
                ? String.join(",", event.getAffectedPermissionTypes()) : "";
        return String.format("%s|%s|%s|%d|%s",
                event.getRoleCode(),
                event.getChangeType().name(),
                affectedTypes,
                event.getEventTimestamp(),
                event.getSourceNode());
    }
}
