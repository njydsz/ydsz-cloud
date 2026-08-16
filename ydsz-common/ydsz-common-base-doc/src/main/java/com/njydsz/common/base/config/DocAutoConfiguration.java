package com.njydsz.common.base.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;

import com.njydsz.common.base.exporter.DefaultDocExporter;
import com.njydsz.common.base.exporter.MarkdownDocExporter;

/**
 * 文档模块自动配置类
 *
 * <p>作为 ydsz-common-base-doc 模块的文档入口配置，负责一站式开启文档模块的所有能力：
 * <ul>
 *   <li>启用 {@link DocProperties} 配置属性绑定</li>
 *   <li>按条件激活 {@link OpenApiAutoConfiguration}（OpenAPI 3.0 多分组）</li>
 *   <li>按条件激活 {@link Knife4jAutoConfiguration}（Knife4j 增强 UI）</li>
 *   <li>注册文档导出器 Bean（{@link DefaultDocExporter}、{@link MarkdownDocExporter}）</li>
 * </ul>
 *
 * <p><b>环境控制：</b>
 * 文档功能默认关闭（{@code ydsz.doc.enabled=false}），需显式配置开启。
 * 生产环境通过 {@link DocSecurityConfiguration} 的 {@code ydsz.doc.production-enabled}
 * 和 {@code ydsz.doc.basic-auth} 控制访问安全。
 *
 * <p><b>线程安全性：</b>本类仅包含 Spring 注解与导入语句，无可变状态，线程安全。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "ydsz.doc", name = "enabled", havingValue = "true", matchIfMissing = false)
@EnableConfigurationProperties(DocProperties.class)
@Import({
    OpenApiAutoConfiguration.class,
    Knife4jAutoConfiguration.class,
    DefaultDocExporter.class,
    MarkdownDocExporter.class
})
public class DocAutoConfiguration {
}
