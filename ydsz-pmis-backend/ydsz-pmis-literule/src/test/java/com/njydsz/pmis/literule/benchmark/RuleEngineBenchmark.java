package com.njydsz.pmis.literule.benchmark;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.core.DefaultRuleEngine;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;

import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * DefaultRuleEngine 规模化性能基准（P2-10）
 *
 * <p>对比不同规则规模下的注册与评估性能，验证增量保序插入的优化效果。
 *
 * <p>运行：
 * <pre>
 * mvn test -Dtest=RuleEngineBenchmark -pl ydsz-pmis-literule
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.5.1
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@State(Scope.Benchmark)
public class RuleEngineBenchmark {

    @Param({"100", "1000", "5000"})
    public int ruleCount;

    private DefaultRuleEngine engine;
    private RuleContext context;

    @Setup
    public void setup() {
        engine = new DefaultRuleEngine();
        for (int i = 0; i < ruleCount; i++) {
            final int prio = i;
            final String code = "BENCH_" + i;
            engine.register(new Rule() {
                @Override public String getCode() { return code; }
                @Override public String getName() { return "基准规则" + i; }
                @Override public String getCategory() { return "BENCH"; }
                @Override public int getPriority() { return prio; }
                @Override public RuleResult evaluate(RuleContext ctx) {
                    return RuleResult.builder().ruleCode(code).triggered(false).build();
                }
            });
        }
        context = RuleContext.of(Map.of("x", 1));
    }

    @Benchmark
    public void registerSingle() {
        Rule r = new Rule() {
            @Override public String getCode() { return "TEMP_" + System.nanoTime(); }
            @Override public String getName() { return "临时"; }
            @Override public String getCategory() { return "BENCH"; }
            @Override public int getPriority() { return 50; }
            @Override public RuleResult evaluate(RuleContext ctx) {
                return RuleResult.builder().ruleCode(getCode()).triggered(false).build();
            }
        };
        engine.register(r);
        engine.unregister(r.getCode());
    }

    @Benchmark
    public int evaluateAll() {
        return engine.evaluate(context).size();
    }

    /**
     * 入口方法（IDE 直接运行）
     */
    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(RuleEngineBenchmark.class.getSimpleName())
                .forks(1)
                .warmupIterations(3)
                .measurementIterations(5)
                .build();
        new Runner(opt).run();
    }
}
