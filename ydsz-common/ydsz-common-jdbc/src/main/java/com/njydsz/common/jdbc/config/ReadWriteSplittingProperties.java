package com.njydsz.common.jdbc.config;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 自动读写分离配置属性
 *
 * <p>启用后，SELECT 语句自动路由到从库，INSERT/UPDATE/DELETE 路由到主库。
 * 需配合 dynamic-datasource 使用。
 *
 * <p>配置示例：
 * <pre>
 * ydsz:
 *   jdbc:
 *     read-write-splitting:
 *       enabled: true
 *       master-ds: master
 *       slave-ds-list: [slave1, slave2]
 *       load-balance-strategy: round-robin   # round-robin | random | weighted
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.jdbc.read-write-splitting")
public class ReadWriteSplittingProperties {

    /**
     * 是否启用自动读写分离（默认 false）
     */
    private boolean enabled = false;

    /**
     * 主库数据源名称（默认 master）
     */
    private String masterDs = "master";

    /**
     * 从库数据源名称列表
     */
    private List<String> slaveDsList = Arrays.asList("slave");

    /**
     * 负载均衡策略：round-robin | random | weighted（默认 round-robin）
     */
    private String loadBalanceStrategy = "round-robin";

    /**
     * 从库权重映射（仅在 load-balance-strategy=weighted 时生效）。
     * <p>key=数据源名称，value=权重值（正整数）。
     * 未配置的从库默认权重为 1。
     */
    private Map<String, Integer> weights = new LinkedHashMap<>();
}
