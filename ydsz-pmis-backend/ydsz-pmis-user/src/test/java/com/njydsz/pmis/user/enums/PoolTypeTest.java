package com.njydsz.pmis.user.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * PoolType 枚举测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("PoolType 资源池类型")
class PoolTypeTest {

    @Test
    @DisplayName("枚举基础属性")
    void basics() {
        assertThat(PoolType.HQ.getCode()).isEqualTo("HQ");
        assertThat(PoolType.DIVISION.getDesc()).isEqualTo("事业部池");
        assertThat(PoolType.RESERVE.getPriority()).isEqualTo(3);
    }

    @Test
    @DisplayName("fromCode 兼容大小写与无效")
    void fromCode() {
        assertThat(PoolType.fromCode("HQ")).isEqualTo(PoolType.HQ);
        assertThat(PoolType.fromCode("hq")).isEqualTo(PoolType.HQ);
        assertThat(PoolType.fromCode("XX")).isNull();
        assertThat(PoolType.fromCode(null)).isNull();
    }

    @Test
    @DisplayName("inferByLevel L13+ 总部池")
    void inferHq() {
        assertThat(PoolType.inferByLevel("L13")).isEqualTo(PoolType.HQ);
        assertThat(PoolType.inferByLevel("L15")).isEqualTo(PoolType.HQ);
    }

    @Test
    @DisplayName("inferByLevel L4-L12 事业部池")
    void inferDivision() {
        assertThat(PoolType.inferByLevel("L4")).isEqualTo(PoolType.DIVISION);
        assertThat(PoolType.inferByLevel("L12")).isEqualTo(PoolType.DIVISION);
    }

    @Test
    @DisplayName("inferByLevel L1-L3 备用池")
    void inferReserve() {
        assertThat(PoolType.inferByLevel("L1")).isEqualTo(PoolType.RESERVE);
        assertThat(PoolType.inferByLevel("L3")).isEqualTo(PoolType.RESERVE);
    }

    @Test
    @DisplayName("inferByLevel 异常输入降级 RESERVE")
    void inferFallback() {
        assertThat(PoolType.inferByLevel(null)).isEqualTo(PoolType.RESERVE);
        assertThat(PoolType.inferByLevel("")).isEqualTo(PoolType.RESERVE);
        assertThat(PoolType.inferByLevel("LXX")).isEqualTo(PoolType.RESERVE);
    }
}
