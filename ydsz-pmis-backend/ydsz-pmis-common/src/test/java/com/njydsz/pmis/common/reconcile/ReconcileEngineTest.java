package com.njydsz.pmis.common.reconcile;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReconcileEngine 单元测试
 *
 * <p>覆盖对账码注册、单次/全量执行、未知 code 与异常捕获场景。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ReconcileEngine 对账引擎测试")
class ReconcileEngineTest {

    private ReconcileEngine engine;

    @BeforeEach
    void setUp() {
        engine = new ReconcileEngine(List.of(
                new SampleHandler("A", "对账A", 0, 0, true),
                new SampleHandler("B", "对账B", 3, 3, true)
        ));
    }

    @Test
    @DisplayName("listCodes 应返回所有注册的 code")
    void listCodes() {
        assertThat(engine.listCodes()).containsExactlyInAnyOrder("A", "B");
    }

    @Test
    @DisplayName("run 指定 code 应返回结果")
    void run_code() {
        ReconcileResult r = engine.run("A");
        assertThat(r.getCode()).isEqualTo("A");
        assertThat(r.getName()).isEqualTo("对账A");
        assertThat(r.isSuccess()).isTrue();
        assertThat(r.getDiffCount()).isZero();
    }

    @Test
    @DisplayName("run 未知 code 应返回失败结果")
    void run_unknown() {
        ReconcileResult r = engine.run("X");
        assertThat(r.isSuccess()).isFalse();
    }

    @Test
    @DisplayName("runAll 应执行所有 handler")
    void runAll() {
        List<ReconcileResult> results = engine.runAll();
        assertThat(results).hasSize(2);
        assertThat(results).extracting(ReconcileResult::isSuccess).containsOnly(true);
    }

    @Test
    @DisplayName("handler 抛异常应被捕获并标记失败")
    void run_exception() {
        ReconcileEngine eng = new ReconcileEngine(List.of(new ErrorHandler()));
        ReconcileResult r = eng.run("ERR");
        assertThat(r.isSuccess()).isFalse();
        assertThat(r.getMessage()).contains("boom");
    }

    static class SampleHandler implements ReconcileHandler {
        private final String code;
        private final String name;
        private final long diff;
        private final long fix;
        private final boolean success;

        SampleHandler(String code, String name, long diff, long fix, boolean success) {
            this.code = code;
            this.name = name;
            this.diff = diff;
            this.fix = fix;
            this.success = success;
        }

        @Override public String code() { return code; }
        @Override public String name() { return name; }

        @Override
        public ReconcileResult reconcile() {
            ReconcileResult r = new ReconcileResult();
            r.setCode(code);
            r.setName(name);
            r.setSuccess(success);
            r.setDiffCount(diff);
            r.setAutoFixedCount(fix);
            return r;
        }
    }

    static class ErrorHandler implements ReconcileHandler {
        @Override public String code() { return "ERR"; }
        @Override public String name() { return "对账ERR"; }
        @Override
        public ReconcileResult reconcile() {
            throw new RuntimeException("boom");
        }
    }
}
