package com.njydsz.common.safe.config;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

import lombok.Data;

/**
 * SQL 注入防护配置属性
 *
 * <p>配置前缀 {@code ydsz.safe.sql-injection}，支持外部化检测规则和运行时热更新。
 *
 * <p><b>配置示例：</b>
 * <pre>{@code
 * ydsz:
 *   safe:
 *     sql-injection:
 *       enabled: true
 *       block-on-detect: true
 *       custom-pattern: ""
 *       whitelist-paths:
 *         - /api/public/**
 *       whitelist-params:
 *         - orderBy
 *         - sortField
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@ConfigurationProperties(prefix = "ydsz.safe.sql-injection")
public class SqlInjectionProperties {

    /**
     * 是否启用 SQL 注入防护
     */
    private boolean enabled = true;

    /**
     * 检测到攻击时是否阻断请求（false=仅记录日志不阻断）
     */
    private boolean blockOnDetect = true;

    /**
     * 自定义检测正则表达式（为空时使用内置默认规则）
     *
     * <p>设置后会覆盖内置的 SQL 注入检测规则，支持运行时热更新。
     */
    private String customPattern = "";

    /**
     * 白名单路径列表（Ant 风格，这些路径不执行 SQL 注入检测）
     */
    private List<String> whitelistPaths = new ArrayList<>();

    /**
     * 白名单参数名列表（这些参数的值不执行 SQL 注入检测）
     */
    private List<String> whitelistParams = new ArrayList<>();
}
