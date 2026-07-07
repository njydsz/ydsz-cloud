/**
 * 工作流配置层。
 *
 * <p>负责将流程引擎运行期所需的各类参数从 {@code application.yml} / Nacos 中加载为强类型
 * {@code @ConfigurationProperties} Bean，集中管理流程行为开关、阈值与连接信息等。
 *
 * <h3>核心组件</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.workflow.config.FlowHistoryProperties} - 流程历史数据归档配置
 *   （归档开关、保留天数、批次大小、单次最大耗时、Cron、清理开关、清理周期）</li>
 * </ul>
 *
 * <h3>设计原则</h3>
 * <ul>
 *   <li>所有可变参数外置为配置项，避免硬编码在业务逻辑中。</li>
 *   <li>配置前缀统一以 {@code pmis.flow.} 开头，便于在 Nacos 中按模块管理。</li>
 *   <li>支持运行期动态覆盖（DB / JobHandler paramsJson）而不影响主配置。</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.workflow.config;
