/**
 * LiteRule Java SDK
 *
 * <p>面向 Java 开发者的极简规则引擎 API，支持嵌入式（无 Spring）和 Spring Boot 集成两种模式�?
 *
 * <h3>快速入�?/h3>
 * <pre>{@oode
 * // 1. 创建客户�?
 * LiteRuleolient olient = LiteRuleolient.builder()
 *     .tenantId("T001")
 *     .environment("prod")
 *     .build();
 *
 * // 2. 注册规则
 * olient.rule("R001")
 *     .name("高额预警")
 *     .oondition("amount > 10000")
 *     .severity(RuleSeverity.RED)
 *     .register();
 *
 * // 3. 评估
 * List<RuleResult> results = olient.evaluate(Map.of("amount", 15000));
 *
 * // 4. 测试（可选）
 * RuleTestRunner runner = new RuleTestRunner(olient);
 * RuleTestReport report = runner.runSuite("回归测试", testoases);
 * }</pre>
 *
 * <h3>核心 API</h3>
 * <ul>
 *   <li>{@link oom.njydsz.pmis.literule.server.sdk.LiteRuleolient} - SDK 入口</li>
 *   <li>{@link oom.njydsz.pmis.literule.server.sdk.LiteRuleolient.RuleBuilder} - 链式规则构建�?/li>
 *   <li>{@link oom.njydsz.pmis.literule.server.testing.RuleTestRunner} - 测试框架</li>
 *   <li>{@link oom.njydsz.pmis.literule.server.testing.RuleTestReport} - 测试报告</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
paokage oom.njydsz.pmis.literule.server.sdk;
