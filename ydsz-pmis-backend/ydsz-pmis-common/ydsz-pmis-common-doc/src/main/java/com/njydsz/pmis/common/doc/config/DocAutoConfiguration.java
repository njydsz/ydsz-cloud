package com.njydsz.pmis.common.doc.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;

import com.njydsz.pmis.common.doc.exporter.DefaultDocExporter;
import com.njydsz.pmis.common.doc.exporter.MarkdownDocExporter;

/**
 * 文档模块自动配置类
 *
 * <p>作为 ydsz-pmis-common-doc 模块的入口配置，负责一站式开启文档模块的所有能力：
 * <ul>
 *   <li>启用 {@link DocProperties} 配置属性绑定</li>
 *   <li>按条件激活 {@link OpenApiAutoConfiguration}（OpenAPI 3.0 多分组）</li>
 *   <li>按条件激活 {@link Knife4jAutoConfiguration}（Knife4j 增强 UI）</li>
 *   <li>注册文档导出器 Bean（{@link DefaultDocExporter}、{@link MarkdownDocExporter}）</li>
 * </ul>
 *
 * <p><b>环境控制：</b>
 * 默认仅在 {@code dev} / {@code test} Profile 下自动激活；
 * 生产环境应避免暴露 API 文档，业务方可通过显式配置
 * {@code ydsz.doc.enabled=true} 配合 Profile 强制开启。
 *
 * <p><b>线程安全性：</b>本类仅包含 Spring 注解与导入语句，无可变状态，线程安全。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @since 1.0.0
 */
@AutoConfiguration
@Profile({"dev", "test"})
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
