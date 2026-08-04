package com.remisoft.common.auth.event;

/**
 * 权限变更事件 SPI 接口。
 *
 * <p>用于扩展权限变更回调，实现类可通过 SpringFactoriesLoader 自动注册。
 * 适用于需要在权限变更时执行自定义逻辑的场景，如：
 * <ul>
 *   <li>审计日志记录</li>
 *   <li>第三方系统同步</li>
 *   <li>告警通知</li>
 *   <li>缓存预热</li>
 * </ul>
 *
 * <p><b>使用方式：</b>
 * <ol>
 *   <li>实现此接口</li>
 *   <li>在 {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 *       或 {@code META-INF/spring.factories} 中注册</li>
 *   <li>实现 {@code onPermissionChanged} 方法处理权限变更事件</li>
 * </ol>
 *
 * @author remi-team
 * @since 1.0.0
 * 
 * @see PermissionChangedEvent
 * @see PermissionChangeNotifier
 */
public interface PermissionChangeListener {

    /**
     * 权限变更回调方法。
     *
     * <p>当权限发生变更时，此方法会被调用。实现类应确保该方法的幂等性，
     * 避免重复处理导致副作用。
     *
     * @param event 权限变更事件，包含事件类型、角色编码和时间戳
     */
    void onPermissionChanged(PermissionChangedEvent event);

    /**
     * 获取监听器的执行顺序。
     *
     * <p>数值越小，优先级越高。默认值为 0。
     *
     * @return 执行顺序
     */
    default int getOrder() {
        return 0;
    }
}
