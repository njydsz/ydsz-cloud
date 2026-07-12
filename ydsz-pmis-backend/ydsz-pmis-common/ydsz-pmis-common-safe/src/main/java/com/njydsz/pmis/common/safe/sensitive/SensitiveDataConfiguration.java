package com.njydsz.pmis.common.safe.sensitive;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * 敏感数据脱敏配置
 *
 * <p>基于 {@link SensitiveDataProcessor} 的脱敏序列化实现。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * remi:
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
 * <p><b>使用方式：</b>
 * <pre>{@code
 * // 方式一：通过 AOP 自动脱敏（推荐）
 * // Controller 返回值中带有 @SensitiveData 注解的字段会自动脱敏
 *
 * // 方式二：手动序列化
 * String json = SensitiveDataSerializer.serialize(user);
 *
 * // 方式三：手动处理对象
 * UserVO desensitized = SensitiveDataProcessor.process(user);
 *
 * // 方式四：全局规则脱敏（无需注解）
 * // 配置 global-rules 后，匹配字段名的字段会自动脱敏
 * }</pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@Data
@ConfigurationProperties(prefix = "remi.safe.sensitive")
public class SensitiveDataConfiguration {

    /**
     * 是否启用敏感数据脱敏
     *
     * <p>默认值为 true。
     * 设置为 false 时，AOP 拦截器不会生效，但工具类仍可使用。
     */
    private boolean enabled = true;

    /**
     * 最大递归深度
     *
     * <p>处理嵌套对象时的最大递归深度，防止栈溢出。
     * 默认值为 10，可根据业务对象复杂度调整。
     */
    private int maxDepth = 10;

    /**
     * 脱敏日志级别
     *
     * <p>控制脱敏操作的日志输出级别。
     * 可选值：TRACE, DEBUG, INFO, WARN, ERROR
     * 默认值为 DEBUG。
     */
    private String logLevel = "DEBUG";

    /**
     * 是否启用脱敏统计
     *
     * <p>启用后，会在日志中输出脱敏处理的字段数量和耗时统计。
     * 默认值为 false。
     */
    private boolean statisticsEnabled = false;

    /**
     * 全局脱敏规则列表
     *
     * <p>无需在字段上添加 @SensitiveData 注解，通过字段名匹配自动脱敏。
     * 适用于无法修改源码的第三方类或统一脱敏场景。
     *
     * <p>示例：
     * <pre>{@code
     * global-rules:
     *   - field-name: phone
     *     type: PHONE
     *   - field-name: idCard
     *     type: ID_CARD
     *   - field-name: email
     *     type: EMAIL
     * }</pre>
     */
    private List<GlobalDesensitizeRule> globalRules = new ArrayList<>();

    /**
     * 全局脱敏规则
     */
    @Data
    public static class GlobalDesensitizeRule {
        /**
         * 字段名（支持精确匹配和通配符）
         *
         * <p>示例：
         * <ul>
         *   <li>phone：精确匹配 phone 字段</li>
         *   <li>*phone*：通配符匹配包含 phone 的字段</li>
         *   <li>user*Phone：通配符匹配 user 开头 Phone 结尾的字段</li>
         * </ul>
         */
        private String fieldName;

        /**
         * 脱敏类型
         *
         * @see SensitiveType
         */
        private SensitiveType type;

        /**
         * 替换字符
         *
         * <p>默认值为 '*'
         */
        private char replaceChar = '*';

        /**
         * 是否启用
         *
         * <p>默认值为 true
         */
        private boolean enabled = true;
    }
}
