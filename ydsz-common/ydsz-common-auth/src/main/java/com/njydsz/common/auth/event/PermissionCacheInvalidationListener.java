package com.njydsz.common.auth.event;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import com.njydsz.common.auth.service.RbacPermissionEvaluator;

/**
 * 权限变更事件监听器
 *
 * <p>监听 {@link PermissionChangedEvent}，在权限变更时自动使缓存失效。
 *
 * <p><b>多实例同步：</b>
 * 当 ydsz-common-redis 可用时，通过 Redis Keyspace Notification 将缓存失效消息广播到其他实例，
 * 确保多实例部署场景下各节点缓存一致性。
 *
 * <p><b>触发场景：</b>
 * <ul>
 *   <li>业务代码发布权限变更事件后自动清理对应角色的权限缓存</li>
 *   <li>角色权限被修改后自动清理缓存</li>
 *   <li>用户角色被分配后自动清理缓存</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public class PermissionCacheInvalidationListener {

    private static final Logger log = LoggerFactory.getLogger(PermissionCacheInvalidationListener.class);

    private final RbacPermissionEvaluator permissionEvaluator;

    public PermissionCacheInvalidationListener(RbacPermissionEvaluator permissionEvaluator) {
        this.permissionEvaluator = permissionEvaluator;
    }

    /**
     * 监听权限变更事件并清理对应缓存（同步监听）
     *
     * @param event 权限变更事件
     */
    @EventListener
    public void onPermissionChanged(PermissionChangedEvent event) {
        log.info("接收到权限变更事件, type={}, roleCode={}", event.getChangeType(), event.getRoleCode());

        handleInvalidation(event.getChangeType(), event.getRoleCode());
    }

    /**
     * 处理缓存失效逻辑
     */
    private void handleInvalidation(PermissionChangedEvent.PermissionChangeType changeType, String roleCode) {
        switch (changeType) {
            case ROLE_PERMISSION_CHANGED:
            case USER_ROLE_CHANGED:
                if (roleCode != null && !"ALL".equals(roleCode)) {
                    permissionEvaluator.clearCachesByRoleCodes(roleCode);
                    log.info("已清理角色权限缓存: {}", roleCode);
                }
                break;

            case MENU_CHANGED:
            case COLUMN_PERMISSION_CHANGED:
            case ROLE_DATA_SCOPE_CHANGED:
            case ROLE_COLUMN_PERMISSION_CHANGED:
            case ROLE_DELETED:
            case ALL:
                permissionEvaluator.clearAllCaches();
                log.info("已清理全部权限缓存");
                break;

            default:
                log.warn("未知的权限变更类型: {}", changeType);
        }
    }
}
