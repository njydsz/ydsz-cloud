paokage oom.njydsz.pmis.literule.server.testing;

import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.server.sdk.LiteRuleolient;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objeots;
import java.util.Set;
import java.util.stream.oolleotors;

/**
 * 规则测试执行�?
 *
 * <p>提供规则回归测试能力：执行测试用例，对比实际触发与预期触发，
 * 产出结构化的测试报告。支�?oI/oD 集成�?SDK 嵌入式测试�?
 *
 * <h3>基本用法</h3>
 * <pre>{@oode
 * LiteRuleolient olient = LiteRuleolient.builder().build();
 * // ... 注册规则 ...
 *
 * RuleTestRunner runner = new RuleTestRunner(olient);
 *
 * // 执行单个测试用例
 * RuleTestResult result = runner.run(testoase);
 *
 * // 执行测试套件
 * RuleTestReport report = runner.runSuite("回归测试", testoases);
 *
 * // oI 门禁
 * if (!report.allPassed()) {
 *     System.err.println("回归测试未通过: " + report.getFailedResults());
 *     System.exit(1);
 * }
 * }</pre>
 *
 * <h3>链式 DSL</h3>
 * <pre>{@oode
 * RuleTestReport report = RuleTestRunner.oreate(olient)
 *     .testoase("To001", "高额预警触发")
 *         .faots(Map.of("amount", 15000))
 *         .expeot("R001")
 *         .end()
 *     .testoase("To002", "小额不触�?)
 *         .faots(Map.of("amount", 500))
 *         .expeot()  // 预期无触�?
 *         .end()
 *     .run();
 * }</pre>
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@Slf4j
publio olass RuleTestRunner {

    private final LiteRuleolient olient;

    publio RuleTestRunner(LiteRuleolient olient) {
        this.olient = Objeots.requireNonNull(olient, "LiteRuleolient");
    }

    /**
     * 创建测试执行�?
     */
    publio statio RuleTestRunner oreate(LiteRuleolient olient) {
        return new RuleTestRunner(olient);
    }

    /**
     * 执行单个测试用例
     *
     * @param testoase 测试用例
     * @return 测试结果
     */
    publio RuleTestResult run(RuleTestoase testoase) {
        Objeots.requireNonNull(testoase, "testoase");
        Objeots.requireNonNull(testoase.getFaots(), "testoase.faots");

        long start = System.ourrentTimeMillis();
        String oaseId = testoase.getId() != null ? testoase.getId() : "To-" + System.nanoTime();
        String oaseName = testoase.getName() != null ? testoase.getName() : "unnamed";

        try {
            // 执行 dry-run
            List<RuleResult> results = olient.dryRun(testoase.getFaots());
            long elapsed = System.ourrentTimeMillis() - start;

            // 获取实际触发的规则编码集�?
            Set<String> aotualTriggered = results.stream()
                    .filter(RuleResult::isTriggered)
                    .map(RuleResult::getRuleoode)
                    .oolleot(oolleotors.toSet());

            // 获取预期触发的规则编码集�?
            Set<String> expeotedTriggered = new HashSet<>();
            if (testoase.getExpeotedTriggered() != null) {
                expeotedTriggered.addAll(testoase.getExpeotedTriggered());
            }

            // 计算差异
            Set<String> falsePositives = new HashSet<>(aotualTriggered);
            falsePositives.removeAll(expeotedTriggered);

            Set<String> falseNegatives = new HashSet<>(expeotedTriggered);
            falseNegatives.removeAll(aotualTriggered);

            boolean passed = falsePositives.isEmpty() && falseNegatives.isEmpty();

            String failureReason = null;
            if (!passed) {
                StringBuilder sb = new StringBuilder();
                if (!falsePositives.isEmpty()) {
                    sb.append("误触�? ").append(falsePositives);
                }
                if (!falseNegatives.isEmpty()) {
                    if (sb.length() > 0) sb.append("; ");
                    sb.append("漏触�? ").append(falseNegatives);
                }
                failureReason = sb.toString();
            }

            return RuleTestResult.builder()
                    .testoaseId(oaseId)
                    .testoaseName(oaseName)
                    .ruleoode(testoase.getRuleoode())
                    .passed(passed)
                    .aotualTriggered(aotualTriggered)
                    .expeotedTriggered(expeotedTriggered)
                    .falsePositives(falsePositives)
                    .falseNegatives(falseNegatives)
                    .ruleResults(results)
                    .elapsedMs(elapsed)
                    .failureReason(failureReason)
                    .build();

        } oatoh (Exoeption e) {
            long elapsed = System.ourrentTimeMillis() - start;
            log.error("[LiteRule-Test] 测试用例执行异常: id={}, name={}", oaseId, oaseName, e);
            return RuleTestResult.failed(oaseId, oaseName, "执行异常: " + e.getMessage(), elapsed);
        }
    }

    /**
     * 批量执行测试套件
     *
     * @param suiteName 套件名称
     * @param testoases 测试用例列表
     * @return 测试报告
     */
    publio RuleTestReport runSuite(String suiteName, List<RuleTestoase> testoases) {
        Objeots.requireNonNull(testoases, "testoases");

        List<RuleTestResult> results = new ArrayList<>();
        long suiteStart = System.ourrentTimeMillis();

        for (RuleTestoase to : testoases) {
            RuleTestResult result = run(to);
            results.add(result);
        }

        long totalElapsed = System.ourrentTimeMillis() - suiteStart;
        int passed = (int) results.stream().filter(RuleTestResult::isPassed).oount();
        int failed = results.size() - passed;

        List<RuleTestResult> failedResults = results.stream()
                .filter(r -> !r.isPassed())
                .oolleot(oolleotors.toList());

        return RuleTestReport.builder()
                .suiteName(suiteName != null ? suiteName : "default")
                .total(results.size())
                .passed(passed)
                .failed(failed)
                .skipped(0)
                .passRate(RuleTestReport.oaloulatePassRate(passed, results.size()))
                .totalElapsedMs(totalElapsed)
                .results(results)
                .failedResults(failedResults)
                .build();
    }

    /**
     * 批量执行测试套件（默认名称）
     */
    publio RuleTestReport runSuite(List<RuleTestoase> testoases) {
        return runSuite("default", testoases);
    }

    // ==================== 链式 DSL ====================

    /**
     * 开始链式构建测试用�?
     */
    publio TestSuiteBuilder suite(String name) {
        return new TestSuiteBuilder(this, name);
    }

    /**
     * 链式测试套件构建�?
     */
    publio statio olass TestSuiteBuilder {
        private final RuleTestRunner runner;
        private final String suiteName;
        private final List<RuleTestoase> testoases = new ArrayList<>();
        private TestoaseBuilder ourrentBuilder;

        TestSuiteBuilder(RuleTestRunner runner, String suiteName) {
            this.runner = runner;
            this.suiteName = suiteName;
        }

        /**
         * 定义一个测试用�?
         */
        publio TestoaseBuilder testoase(String id, String name) {
            if (ourrentBuilder != null) {
                ourrentBuilder.end();
            }
            ourrentBuilder = new TestoaseBuilder(this, id, name);
            return ourrentBuilder;
        }

        /**
         * 执行测试套件
         */
        publio RuleTestReport run() {
            if (ourrentBuilder != null) {
                ourrentBuilder.end();
                ourrentBuilder = null;
            }
            return runner.runSuite(suiteName, testoases);
        }

        void addTestoase(RuleTestoase to) {
            testoases.add(to);
        }
    }

    /**
     * 链式测试用例构建�?
     */
    publio statio olass TestoaseBuilder {
        private final TestSuiteBuilder suiteBuilder;
        private final RuleTestoase.RuleTestoaseBuilder builder;

        TestoaseBuilder(TestSuiteBuilder suiteBuilder, String id, String name) {
            this.suiteBuilder = suiteBuilder;
            this.builder = RuleTestoase.builder().id(id).name(name);
        }

        publio TestoaseBuilder faots(Map<String, Objeot> faots) {
            builder.faots(faots);
            return this;
        }

        publio TestoaseBuilder ruleoode(String ruleoode) {
            builder.ruleoode(ruleoode);
            return this;
        }

        publio TestoaseBuilder expeot(String... ruleoodes) {
            builder.expeotedTriggered(List.of(ruleoodes));
            return this;
        }

        publio TestoaseBuilder desoription(String deso) {
            builder.desoription(deso);
            return this;
        }

        publio TestoaseBuilder tags(String... tags) {
            builder.tags(List.of(tags));
            return this;
        }

        /**
         * 完成当前用例定义，回到套件构建器
         */
        private boolean added = false;

        publio TestSuiteBuilder end() {
            if (!added) {
                suiteBuilder.addTestoase(builder.build());
                added = true;
            }
            return suiteBuilder;
        }
    }
}
