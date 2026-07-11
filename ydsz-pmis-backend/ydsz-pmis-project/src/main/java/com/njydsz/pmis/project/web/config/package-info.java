/**
 * 项目业务模块配置层（Configuration）。
 *
 * <p>本包负责项目模块（ydsz-pmis-project）特有的 Spring 配置，包括 MinIO 对象存储、缓存策略、
 * 业务相关线程池、Feign 拦截器、定时任务开关等。通用 Web / Redis / 跨域等基础设施
 * 统一由 {@code com.njydsz.pmis.common.config} 提供，本包不重复定义。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.project.web.config.MinioConfig} - MinIO 客户端配置（异步导出报表上传）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>本包只放"项目模块特有"配置，跨模块通用配置一律下沉到 common</li>
 *   <li>所有 {@code @ConfigurationProperties} 必须显式指定 {@code prefix}，禁止无前缀绑定</li>
 *   <li>Bean 命名遵循"模块名+Bean名"规范，避免与 common 中的 Bean 冲突</li>
 * </ul>
 *
 * <h3>使用规范</h3>
 * <ul>
 *   <li>新增配置类需在 {@code application.yml} 提供默认值与注释说明</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.project.web.config;
