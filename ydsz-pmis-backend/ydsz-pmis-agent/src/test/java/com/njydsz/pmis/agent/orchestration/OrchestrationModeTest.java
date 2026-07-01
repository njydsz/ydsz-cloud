package com.njydsz.pmis.agent.orchestration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * OrchestrationMode 枚举测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("OrchestrationMode 编排模式枚举")
class OrchestrationModeTest {

    @Test
    @DisplayName("4 种编排模式 code/desc 一致")
    void codes() {
        assertThat(OrchestrationMode.SEQUENTIAL.getCode()).isEqualTo("SEQUENTIAL");
        assertThat(OrchestrationMode.PARALLEL.getCode()).isEqualTo("PARALLEL");
        assertThat(OrchestrationMode.VOTING.getCode()).isEqualTo("VOTING");
        assertThat(OrchestrationMode.CASCADE.getCode()).isEqualTo("CASCADE");
        assertThat(OrchestrationMode.SEQUENTIAL.getDesc()).isEqualTo("顺序执行");
        assertThat(OrchestrationMode.PARALLEL.getDesc()).isEqualTo("并行执行");
        assertThat(OrchestrationMode.VOTING.getDesc()).isEqualTo("投票融合");
        assertThat(OrchestrationMode.CASCADE.getDesc()).isEqualTo("级联执行");
    }

    @Test
    @DisplayName("fromCode - 正常解析")
    void fromCodeNormal() {
        assertThat(OrchestrationMode.fromCode("SEQUENTIAL")).isEqualTo(OrchestrationMode.SEQUENTIAL);
        assertThat(OrchestrationMode.fromCode("PARALLEL")).isEqualTo(OrchestrationMode.PARALLEL);
        assertThat(OrchestrationMode.fromCode("VOTING")).isEqualTo(OrchestrationMode.VOTING);
        assertThat(OrchestrationMode.fromCode("CASCADE")).isEqualTo(OrchestrationMode.CASCADE);
    }

    @Test
    @DisplayName("fromCode - 大小写不敏感")
    void fromCodeIgnoreCase() {
        assertThat(OrchestrationMode.fromCode("sequential")).isEqualTo(OrchestrationMode.SEQUENTIAL);
        assertThat(OrchestrationMode.fromCode("Parallel")).isEqualTo(OrchestrationMode.PARALLEL);
    }

    @Test
    @DisplayName("fromCode - 未知返回 null")
    void fromCodeUnknown() {
        assertThat(OrchestrationMode.fromCode("XXX")).isNull();
        assertThat(OrchestrationMode.fromCode("")).isNull();
        assertThat(OrchestrationMode.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("枚举数量")
    void values() {
        assertThat(OrchestrationMode.values()).hasSize(4);
    }
}
