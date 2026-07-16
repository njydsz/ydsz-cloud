package com.njydsz.pmis.cronjob.server.core.connector;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import lombok.extern.slf4j.Slf4j;

/**
 * 连接器注册管理器（P2-3）。
 *
 * <p>管理所有已注册的 {@link JobConnector} 实例，提供按类型查找的能力。
 * 连接器通过 Spring 自动注入注册，业务侧通过 {@link #getConnector(String)} 获取。
 *
 * <h3>注册流程</h3>
 * <ol>
 *   <li>实现 {@link JobConnector} 接口</li>
 *   <li>标注 {@code @Component} 注解</li>
 *   <li>Spring 容器启动时自动注入到 {@link #connectors} Map</li>
 * </ol>
 *
 * <h3>使用示例</h3>
 * <pre>{@code
 * JobConnector connector = connectorManager.getConnector("XXL_JOB");
 * if (connector != null) {
 *     List<ConnectorTaskInfo> tasks = connector.importTasks(config);
 * }
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class ConnectorManager {

    /** 已注册的连接器: type → connector */
    private final Map<String, JobConnector> connectors = new ConcurrentHashMap<>();

    /**
     * Spring 自动注入所有 JobConnector 实现。
     *
     * @param connectorList 所有已注册的连接器列表
     */
    public ConnectorManager(List<JobConnector> connectorList) {
        if (connectorList != null) {
            for (JobConnector connector : connectorList) {
                String type = connector.getType();
                connectors.put(type, connector);
                log.info("[ConnectorManager] 注册连接器: type={} class={}", type, connector.getClass().getSimpleName());
            }
        }
        log.info("[ConnectorManager] 初始化完成, 已注册 {} 个连接器: {}", connectors.size(), connectors.keySet());
    }

    /**
     * 获取指定类型的连接器。
     *
     * @param type 连接器类型（如 "XXL_JOB"、"POWER_JOB"）
     * @return 连接器实例；不存在时返回 null
     */
    public JobConnector getConnector(String type) {
        return connectors.get(type);
    }

    /**
     * 获取所有已注册的连接器类型。
     *
     * @return 类型列表
     */
    public List<String> getRegisteredTypes() {
        return connectors.keySet().stream().sorted().collect(Collectors.toList());
    }

    /**
     * 注册连接器（运行时动态注册）。
     *
     * @param connector 连接器实例
     */
    public void register(JobConnector connector) {
        String type = connector.getType();
        connectors.put(type, connector);
        log.info("[ConnectorManager] 动态注册连接器: type={}", type);
    }

    /**
     * 注销连接器。
     *
     * @param type 连接器类型
     */
    public void unregister(String type) {
        connectors.remove(type);
        log.info("[ConnectorManager] 注销连接器: type={}", type);
    }
}
