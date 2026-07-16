package com.njydsz.common.safe.sensitive;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * 敏感数据脱敏配置属性
 *
 * <p>基于 {@link SensitiveDataProcessor} 的脱敏序列化实现。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     sensitive:
 *       enabled: true
 *       max-depth: 10
 *       log-level: INFO
 *       # 全局脱敏规则（可选）
 *       global-rules:
 *         - field-name: phone
 *           type: PHONE
 *         - field-name: idCard
 *           type: ID_CARD
 * }</pre>
 *
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.sensitive")
public class SensitiveDataProperties {

    /**
     * 是否启用敏感数据脱敏
     */
    private boolean enabled = true;

    /**
     * 最大递归深度
     */
    private int maxDepth = 10;

    /**
     * 脱敏日志级别
     */
    private String logLevel = "DEBUG";

    /**
     * 是否启用脱敏统计
     */
    private boolean statisticsEnabled = false;

    /**
     * 全局脱敏规则列表
     */
    private List<GlobalDesensitizeRule> globalRules = new ArrayList<>();

    /**
     * 全局脱敏规则
     */
    @Data
    public static class GlobalDesensitizeRule {
        private String fieldName;
        private SensitiveType type;
        private char replaceChar = '*';
        private boolean enabled = true;
    }
}
