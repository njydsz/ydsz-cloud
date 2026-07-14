package com.njydsz.pmis.common.domain.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.njydsz.pmis.common.domain.tree.TreeLazyConfig;

/**
 * Domain 模块自动配置
 *
 * <p>激活领域模型层的配置属性绑定，包括�?
 * <ul>
 *   <li>树形结构懒加载配置（TreeLazyConfig�?/li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.domain", name = "enabled", havingValue = "true", matchIfMissing = true)
@EnableConfigurationProperties(TreeLazyConfig.class)
public class DomainAutoConfiguration {
}