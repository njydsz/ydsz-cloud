package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReconcileLevel 枚举测试
 */
@DisplayName("ReconcileLevel 对账等级测试")
class ReconcileLevelTest {

    @Test
    @DisplayName("values 应包含 3 个等级")
    void values_three() {
        assertThat(ReconcileLevel.values()).hasSize(3);
    }

    @Test
    @DisplayName("枚举值与名称一致")
    void enumName() {
        assertThat(ReconcileLevel.INFO.name()).isEqualTo("INFO");
        assertThat(ReconcileLevel.WARN.name()).isEqualTo("WARN");
        assertThat(ReconcileLevel.ERROR.name()).isEqualTo("ERROR");
    }

    @Test
    @DisplayName("valueOf 应能解析所有枚举")
    void valueOf_all() {
        assertThat(ReconcileLevel.valueOf("INFO")).isEqualTo(ReconcileLevel.INFO);
        assertThat(ReconcileLevel.valueOf("WARN")).isEqualTo(ReconcileLevel.WARN);
        assertThat(ReconcileLevel.valueOf("ERROR")).isEqualTo(ReconcileLevel.ERROR);
    }

    @Test
    @DisplayName("枚举顺序 INFO < WARN < ERROR")
    void ordinalOrder() {
        assertThat(ReconcileLevel.INFO.ordinal()).isLessThan(ReconcileLevel.WARN.ordinal());
        assertThat(ReconcileLevel.WARN.ordinal()).isLessThan(ReconcileLevel.ERROR.ordinal());
    }
}
