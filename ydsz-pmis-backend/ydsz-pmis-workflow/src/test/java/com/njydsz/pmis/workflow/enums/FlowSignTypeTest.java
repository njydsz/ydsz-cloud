package com.njydsz.pmis.workflow.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * FlowSignType 枚举单元测试
 *
 * <p>GAP-P1-7: 验证加签类型枚举的 5 个取值，以及 {@code name()} 与 DB 默认值 'ORIGINAL' 的一致性。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
class FlowSignTypeTest {

    @Test
    @DisplayName("枚举应包含 5 个取值：ORIGINAL/BEFORE/AFTER/PARALLEL/ADD")
    void shouldContainAllFiveValues() {
        assertThat(FlowSignType.values())
                .containsExactlyInAnyOrder(
                        FlowSignType.ORIGINAL,
                        FlowSignType.BEFORE,
                        FlowSignType.AFTER,
                        FlowSignType.PARALLEL,
                        FlowSignType.ADD);
    }

    @Test
    @DisplayName("ORIGINAL.name() 应与 DB 默认值 'ORIGINAL' 一致")
    void originalNameShouldMatchDbDefault() {
        assertThat(FlowSignType.ORIGINAL.name()).isEqualTo("ORIGINAL");
    }

    @Test
    @DisplayName("FlowSignType.valueOf 应能按字符串名称解析所有取值")
    void valueOfShouldParseAllNames() {
        for (FlowSignType type : FlowSignType.values()) {
            assertThat(FlowSignType.valueOf(type.name())).isEqualTo(type);
        }
    }
}
