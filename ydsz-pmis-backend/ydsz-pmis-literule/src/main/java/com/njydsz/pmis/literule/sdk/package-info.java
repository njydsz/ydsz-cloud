/**
 * LiteRule Java SDK
 *
 * <p>面向 Java 开发者的极简规则引擎 API，支持嵌入式（无 Spring）和 Spring Boot 集成两种模式。
 *
 * <h3>快速入门</h3>
 * <pre>{@code
 * // 1. 创建客户端
 * LiteRuleClient client = LiteRuleClient.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 *
 * // 2. 注册规则
 * client.rule("R001")
 *     .name("高额预警")
 *     .condition("amount > 10000")
 *     .severity(RuleSeverity.RED)
 *     .register();
 *
 * // 3. 评估
 * List<RuleResult> results = client.evaluate(Map.of("amount", 15000));
 *
 * // 4. 测试（可选）
 * RuleTestRunner runner = new RuleTestRunner(client);
 * RuleTestReport report = runner.runSuite("回归测试", testCases);
 * }</pre>
 *
 * <h3>核心 API</h3>
 * <ul>
 *   <li>{@link com.njydsz.pmis.literule.sdk.LiteRuleClient} - SDK 入口</li>
 *   <li>{@link com.njydsz.pmis.literule.sdk.LiteRuleClient.RuleBuilder} - 链式规则构建器</li>
 *   <li>{@link com.njydsz.pmis.literule.testing.RuleTestRunner} - 测试框架</li>
 *   <li>{@link com.njydsz.pmis.literule.testing.RuleTestReport} - 测试报告</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
package com.njydsz.pmis.literule.sdk;
