package com.njydsz.pmis.execution.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ReconcileType 枚举测试
 */
@DisplayName("ReconcileType 对账类型测试")
class ReconcileTypeTest {

    @Test
    @DisplayName("values 应返回 7 种对账类型")
    void values_seven() {
        assertThat(ReconcileType.values()).hasSize(7);
    }

    @Test
    @DisplayName("getCode 应返回枚举名")
    void getCode_name() {
        assertThat(ReconcileType.MISSING_COST_FOR_APPROVED_TIME.getCode()).isEqualTo("MISSING_COST_FOR_APPROVED_TIME");
        assertThat(ReconcileType.GHOST_COST_FOR_REJECTED_TIME.getCode()).isEqualTo("GHOST_COST_FOR_REJECTED_TIME");
        assertThat(ReconcileType.DAILY_HOURS_OVERFLOW.getCode()).isEqualTo("DAILY_HOURS_OVERFLOW");
        assertThat(ReconcileType.WEEKLY_HOURS_OVERLOAD.getCode()).isEqualTo("WEEKLY_HOURS_OVERLOAD");
        assertThat(ReconcileType.CROSS_PROJECT_CONFLICT.getCode()).isEqualTo("CROSS_PROJECT_CONFLICT");
        assertThat(ReconcileType.AMOUNT_DRIFT.getCode()).isEqualTo("AMOUNT_DRIFT");
        assertThat(ReconcileType.ALLOCATED_BEFORE_APPROVAL.getCode()).isEqualTo("ALLOCATED_BEFORE_APPROVAL");
    }

    @Test
    @DisplayName("getDesc 应与 getCode 一致")
    void getDesc_sameAsCode() {
        for (ReconcileType t : ReconcileType.values()) {
            assertThat(t.getDesc()).isEqualTo(t.getCode());
        }
    }

    @Test
    @DisplayName("fromCode 应能反向解析所有枚举")
    void fromCode_all() {
        for (ReconcileType t : ReconcileType.values()) {
            assertThat(ReconcileType.fromCode(t.getCode())).isEqualTo(t);
        }
    }

    @Test
    @DisplayName("fromCode 未知 code 应返回 null")
    void fromCode_unknown() {
        assertThat(ReconcileType.fromCode("UNKNOWN_TYPE")).isNull();
    }

    @Test
    @DisplayName("fromCode null 应返回 null")
    void fromCode_null() {
        assertThat(ReconcileType.fromCode(null)).isNull();
    }
}
