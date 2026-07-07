/**
 * 规则引擎 - 扩展点（SPI）层。
 *
 * <p>提供 literule 模块所有可扩展点，业务方可自定义实现：
 * <ul>
 *   <li>{@code RuleSource}        - 规则来源 SPI（DB / Nacos / Git / 本地文件）</li>
 *   <li>{@code RuleFunction}      - 自定义函数 SPI</li>
 *   <li>{@code RuleOperator}      - 自定义操作符 SPI</li>
 *   <li>{@code RuleDataSource}    - 规则所需数据源 SPI</li>
 *   <li>{@code RuleActionExecutor} - 规则动作执行器 SPI</li>
 *   <li>{@code RuleEventListener} - 规则事件监听器 SPI</li>
 *   <li>{@code RuleContextBuilder} - 规则上下文构建器 SPI</li>
 * </ul>
 *
 * <h3>SPI 注册方式</h3>
 * <ul>
 *   <li>Spring Boot：{@code META-INF/spring/...} 自动装配</li>
 *   <li>JDK SPI：{@code META-INF/services/<接口全限定名>}</li>
 *   <li>Spring Bean：直接 {@code @Component} 注入（推荐）</li>
 * </ul>
 *
 * <h3>实现约束</h3>
 * <ul>
 *   <li>SPI 实现必须是无状态的（线程安全）</li>
 *   <li>SPI 加载顺序通过 {@code @Order} 控制</li>
 *   <li>SPI 实现变更时务必保证向后兼容（不删除方法 / 不改签名）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
package com.njydsz.pmis.literule.spi;
