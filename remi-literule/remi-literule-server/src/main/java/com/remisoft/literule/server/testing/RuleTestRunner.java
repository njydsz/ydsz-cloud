package com.remisoft.literule.server.testing;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import com.remisoft.literule.api.RuleResult;
import com.remisoft.literule.server.sdk.LiteRuleSdk;

import lombok.extern.slf4j.Slf4j;

/**
 * 规则测试执行器
 *
 * <p>提供规则回归测试能力：执行测试用例，对比实际触发与预期触发，
 * 产出结构化的测试报告。支持 CI/CD 集成和 SDK 嵌入式测试。
 *
 * <h3>基本用法</h3>
 * <pre>{@code
 * LiteRuleSdk sdk = LiteRuleSdk.builder().build();
 * // ... 注册规则 ...
 *
 * RuleTestRunner runner = new RuleTestRunner(client);
 *
 * // 执行单个测试用例
 * RuleTestResult result = runner.run(testCase);
 *
 * // 执行测试套件
 * RuleTestReport report = runner.runSuite("回归测试", testCases);
 *
 * // CI 门禁
 * if (!report.allPassed()) {
 *     System.err.println("回归测试未通过: " + report.getFailedResults());
 *     System.exit(1);
 * }
 * }</pre>
 *
 * <h3>链式 DSL</h3>
 * <pre>{@code
 * RuleTestReport report = RuleTestRunner.create(client)
 *     .testCase("TC001", "高额预警触发")
 *         .facts(Map.of("amount", 15000))
 *         .expect("R001")
 *         .end()
 *     .testCase("TC002", "小额不触发")
 *         .facts(Map.of("amount", 500))
 *         .expect()  // 预期无触发
 *         .end()
 *     .run();
 * }</pre>
 *
 * @since 1.0.0
 * @author remi-team
 */
@Slf4j
public class RuleTestRunner {

    private final LiteRuleSdk client;

    public RuleTestRunner(LiteRuleSdk client) {
        this.client = Objects.requireNonNull(client, "LiteRuleSdk");
    }

    /**
     * 创建测试执行器
     */
    public static RuleTestRunner create(LiteRuleSdk client) {
        return new RuleTestRunner(client);
    }

    /**
     * 执行单个测试用例
     *
     * @param testCase 测试用例
     * @return 测试结果
     */
    public RuleTestResult run(RuleTestCase testCase) {
        Objects.requireNonNull(testCase, "testCase");
        Objects.requireNonNull(testCase.getFacts(), "testCase.facts");

        long start = System.currentTimeMillis();
        String caseId = testCase.getId() != null ? testCase.getId() : "TC-" + System.nanoTime();
        String caseName = testCase.getName() != null ? testCase.getName() : "unnamed";

        try {
            // 执行 dry-run
            List<RuleResult> results = client.dryRun(testCase.getFacts());
            long elapsed = System.currentTimeMillis() - start;

            // 获取实际触发的规则编码集合
            Set<String> actualTriggered = results.stream()
                    .filter(RuleResult::isTriggered)
                    .map(RuleResult::getRuleCode)
                    .collect(Collectors.toSet());

            // 获取预期触发的规则编码集合
            Set<String> expectedTriggered = new HashSet<>();
            if (testCase.getExpectedTriggered() != null) {
                expectedTriggered.addAll(testCase.getExpectedTriggered());
            }

            // 计算差异
            Set<String> falsePositives = new HashSet<>(actualTriggered);
            falsePositives.removeAll(expectedTriggered);

            Set<String> falseNegatives = new HashSet<>(expectedTriggered);
            falseNegatives.removeAll(actualTriggered);

            boolean passed = falsePositives.isEmpty() && falseNegatives.isEmpty();

            String failureReason = null;
            if (!passed) {
                StringBuilder sb = new StringBuilder();
                if (!falsePositives.isEmpty()) {
                    sb.append("误触发: ").append(falsePositives);
                }
                if (!falseNegatives.isEmpty()) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append("漏触发: ").append(falseNegatives);
                }
                failureReason = sb.toString();
            }

            return RuleTestResult.builder()
                    .testCaseId(caseId)
                    .testCaseName(caseName)
                    .ruleCode(testCase.getRuleCode())
                    .passed(passed)
                    .actualTriggered(actualTriggered)
                    .expectedTriggered(expectedTriggered)
                    .falsePositives(falsePositives)
                    .falseNegatives(falseNegatives)
                    .ruleResults(results)
                    .elapsedMs(elapsed)
                    .failureReason(failureReason)
                    .build();

        } catch (Exception e) {
            long elapsed = System.currentTimeMillis() - start;
            log.error("[LiteRule-Test] 测试用例执行异常: id={}, name={}", caseId, caseName, e);
            return RuleTestResult.failed(caseId, caseName, "执行异常: " + e.getMessage(), elapsed);
        }
    }

    /**
     * 批量执行测试套件
     *
     * @param suiteName 套件名称
     * @param testCases 测试用例列表
     * @return 测试报告
     */
    public RuleTestReport runSuite(String suiteName, List<RuleTestCase> testCases) {
        Objects.requireNonNull(testCases, "testCases");

        List<RuleTestResult> results = new ArrayList<>();
        long suiteStart = System.currentTimeMillis();

        for (RuleTestCase tc : testCases) {
            RuleTestResult result = run(tc);
            results.add(result);
        }

        long totalElapsed = System.currentTimeMillis() - suiteStart;
        int passed = (int) results.stream().filter(RuleTestResult::isPassed).count();
        int failed = results.size() - passed;

        List<RuleTestResult> failedResults = results.stream()
                .filter(r -> !r.isPassed())
                .collect(Collectors.toList());

        return RuleTestReport.builder()
                .suiteName(suiteName != null ? suiteName : "default")
                .total(results.size())
                .passed(passed)
                .failed(failed)
                .skipped(0)
                .passRate(RuleTestReport.calculatePassRate(passed, results.size()))
                .totalElapsedMs(totalElapsed)
                .results(results)
                .failedResults(failedResults)
                .build();
    }

    /**
     * 批量执行测试套件（默认名称）
     */
    public RuleTestReport runSuite(List<RuleTestCase> testCases) {
        return runSuite("default", testCases);
    }

    // ==================== 链式 DSL ====================

    /**
     * 开始链式构建测试用例
     */
    public TestSuiteBuilder suite(String name) {
        return new TestSuiteBuilder(this, name);
    }

    /**
     * 链式测试套件构建器
     */
    public static class TestSuiteBuilder {
        private final RuleTestRunner runner;
        private final String suiteName;
        private final List<RuleTestCase> testCases = new ArrayList<>();
        private TestCaseBuilder currentBuilder;

        TestSuiteBuilder(RuleTestRunner runner, String suiteName) {
            this.runner = runner;
            this.suiteName = suiteName;
        }

        /**
         * 定义一个测试用例
         */
        public TestCaseBuilder testCase(String id, String name) {
            if (currentBuilder != null) {
                currentBuilder.end();
            }
            currentBuilder = new TestCaseBuilder(this, id, name);
            return currentBuilder;
        }

        /**
         * 执行测试套件
         */
        public RuleTestReport run() {
            if (currentBuilder != null) {
                currentBuilder.end();
                currentBuilder = null;
            }
            return runner.runSuite(suiteName, testCases);
        }

        void addTestCase(RuleTestCase tc) {
            testCases.add(tc);
        }
    }

    /**
     * 链式测试用例构建器
     */
    public static class TestCaseBuilder {
        private final TestSuiteBuilder suiteBuilder;
        private final RuleTestCase.RuleTestCaseBuilder builder;

        TestCaseBuilder(TestSuiteBuilder suiteBuilder, String id, String name) {
            this.suiteBuilder = suiteBuilder;
            this.builder = RuleTestCase.builder().id(id).name(name);
        }

        /**
         * 设置测试用例的事实数据（规则评估的输入 facts）。
         *
         * @param facts 事实键值对，不可为 null（执行时由 {@link #run} 校验）
         * @return 当前用例构建器（链式调用）
         */
        public TestCaseBuilder facts(Map<String, Object> facts) {
            builder.facts(facts);
            return this;
        }

        /**
         * 设置关联的规则编码（可选，用于聚焦单规则或以 {@code ruleCode} 维度聚合结果）。
         *
         * @param ruleCode 规则编码，可空
         * @return 当前用例构建器（链式调用）
         */
        public TestCaseBuilder ruleCode(String ruleCode) {
            builder.ruleCode(ruleCode);
            return this;
        }

        /**
         * 设置预期触发的规则编码集合。
         *
         * <p>不传参（{@code .expect()}）表示预期本次评估<strong>无规则触发</strong>，
         * 实际有触发时将判为误触发（false positive）。
         *
         * @param ruleCodes 预期触发的规则编码（可变参数，可空）
         * @return 当前用例构建器（链式调用）
         */
        public TestCaseBuilder expect(String... ruleCodes) {
            builder.expectedTriggered(List.of(ruleCodes));
            return this;
        }

        /**
         * 设置测试用例描述（仅展示/报告用，可空）。
         *
         * @param desc 描述文本
         * @return 当前用例构建器（链式调用）
         */
        public TestCaseBuilder description(String desc) {
            builder.description(desc);
            return this;
        }

        /**
         * 设置用例标签（用于分组、筛选或在报告中归类，可空）。
         *
         * @param tags 标签数组，可空
         * @return 当前用例构建器（链式调用）
         */
        public TestCaseBuilder tags(String... tags) {
            builder.tags(List.of(tags));
            return this;
        }

        // 幂等标记：防止用例链中途重复调用 end() 导致 RuleTestCase 被重复提交
        private boolean added = false;

        /**
         * 完成当前用例定义并交回套件构建器。
         *
         * <p>幂等：内部通过 {@code added} 标记保证即使用例链中途重复调用
         * {@code end()}，{@link RuleTestCase} 也只提交一次，避免重复用例。
         *
         * @return 所属套件构建器（继续追加下一个用例或调用 {@link TestSuiteBuilder#run()}）
         */
        public TestSuiteBuilder end() {
            if (!added) {
                suiteBuilder.addTestCase(builder.build());
                added = true;
            }
            return suiteBuilder;
        }
    }
}
