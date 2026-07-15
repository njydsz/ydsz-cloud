package com.njydsz.pmis.common.auth.event;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.core.OrderComparator;

/**
 * 权限变更事件发布器。
 *
 * <p>负责发布权限变更事件到 SPI 扩展点监听器。通过 ServiceLoader 自动发现并排序
 * 所有注册的 {@link PermissionChangeListener} 实现。
 *
 * <p><b>核心功能：</b>
 * <ul>
 *   <li>通过 Spring ApplicationEventPublisher 发布 Spring 事件</li>
 *   <li>通过 ServiceLoader 加载所有 PermissionChangeListener SPI 实现</li>
 *   <li>按 getOrder() 排序后依次通知监听器</li>
 * </ul>
 *
 * @since 1.0.0
 * 
 * @see PermissionChangedEvent
 * @see PermissionChangeListener
 */
public class PermissionChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(PermissionChangeNotifier.class);

    private final ApplicationEventPublisher applicationEventPublisher;

    private final List<PermissionChangeListener> listeners;

    public PermissionChangeNotifier(ApplicationEventPublisher applicationEventPublisher) {
        this.applicationEventPublisher = applicationEventPublisher;
        this.listeners = loadListeners();
        log.info("PermissionChangeNotifier 初始化完成，加载 {} 个 SPI 监听器", listeners.size());
    }

    /**
     * 发布角色权限变更事件。
     *
     * @param roleCode 角色编码
     */
    public void notifyRoleChanged(String roleCode) {
        notify(PermissionChangedEvent.rolePermissionChanged(roleCode));
    }

    /**
     * 发布菜单变更事件。
     */
    public void notifyMenuChanged() {
        notify(PermissionChangedEvent.menuChanged());
    }

    /**
     * 发布接口权限变更事件。
     *
     * @param apiPath API 路径
     */
    public void notifyApiPermissionChanged(String apiPath) {
        notify(new PermissionChangedEvent(apiPath, PermissionChangedEvent.PermissionChangeType.ROLE_PERMISSION_CHANGED, null, null));
    }

    /**
     * 发布权限变更事件。
     *
     * @param event 权限变更事件
     */
    public void notify(PermissionChangedEvent event) {
        if (event == null) {
            log.warn("权限变更事件发布失败：event 不能为空");
            return;
        }

        log.info("发布权限变更事件：{}", event);

        // 发布 Spring 事件
        applicationEventPublisher.publishEvent(event);

        // 通知 SPI 监听器
        for (PermissionChangeListener listener : listeners) {
            try {
                listener.onPermissionChanged(event);
            } catch (Exception e) {
                log.error("权限变更监听器处理异常: listener={}, event={}, error={}",
                        listener.getClass().getName(), event, e.getMessage(), e);
            }
        }
    }

    /**
     * 通过 ServiceLoader 加载所有 PermissionChangeListener SPI 实现，并按 Order 排序。
     */
    private List<PermissionChangeListener> loadListeners() {
        ServiceLoader<PermissionChangeListener> loader = ServiceLoader.load(PermissionChangeListener.class);
        List<PermissionChangeListener> listenerList = new ArrayList<>();
        for (PermissionChangeListener listener : loader) {
            listenerList.add(listener);
        }
        listenerList.sort(OrderComparator.INSTANCE);
        return listenerList;
    }
}
