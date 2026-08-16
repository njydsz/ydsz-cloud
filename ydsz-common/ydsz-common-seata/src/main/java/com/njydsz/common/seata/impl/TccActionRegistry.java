package com.njydsz.common.seata.impl;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import com.njydsz.common.seata.api.TccAction;

/**
 * TCC Action 注册表
 *
 * <p>管理 Spring 容器中所有 {@link TccAction} Bean 的注册表，
 * 支持通过 Bean 名称查找 TCC Action 实例，用于跨实例的事务恢复。
 *
 * <p><b>设计目的</b>：替代原 {@code registeredActions} 内存 Map，
 * 解决实例重启后 TCC Action 丢失的问题。通过 Spring Bean 名称持久化
 * 在事务日志中，恢复时从注册表查找对应 Bean。
 *
 * @author ydsz-team
 * @since 1.4.0
 */
public class TccActionRegistry implements ApplicationContextAware, InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(TccActionRegistry.class);

    private ApplicationContext applicationContext;

    /** Bean 名称 → TccAction 实例的映射 */
    private final Map<String, TccAction<?>> actionMap = new ConcurrentHashMap<>();

    /**
     * 注册 TCC Action Bean
     *
     * @param beanName Spring Bean 名称
     * @param action   TCC Action 实例
     */
    public void register(String beanName, TccAction<?> action) {
        actionMap.put(beanName, action);
        log.debug("TccAction registered: beanName={}", beanName);
    }

    /**
     * 通过 Bean 名称查找 TCC Action
     *
     * @param beanName Spring Bean 名称
     * @return TccAction 实例，未找到时返回 null
     */
    @SuppressWarnings("unchecked")
    public <T> TccAction<T> findByName(String beanName) {
        return (TccAction<T>) actionMap.get(beanName);
    }

    /**
     * 获取注册表大小（用于监控）
     *
     * @return 已注册的 TCC Action 数量
     */
    public int size() {
        return actionMap.size();
    }

    @Override
    public void setApplicationContext(ApplicationContext applicationContext) {
        this.applicationContext = applicationContext;
    }

    /**
     * 初始化时自动扫描并注册所有 TccAction Bean
     */
    @Override
    public void afterPropertiesSet() {
        Map<String, TccAction> beans = applicationContext.getBeansOfType(TccAction.class);
        beans.forEach(this::register);
        log.info("TccActionRegistry initialized: {} actions registered", actionMap.size());
    }
}
