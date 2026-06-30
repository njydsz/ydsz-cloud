package com.njydsz.pmis.user.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * LeaveType 枚举测试
 */
@DisplayName("LeaveType 枚举")
class LeaveTypeTest {

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(LeaveType.fromCode("ANNUAL")).isEqualTo(LeaveType.ANNUAL);
        assertThat(LeaveType.fromCode("sick")).isEqualTo(LeaveType.SICK);
        assertThat(LeaveType.fromCode(null)).isNull();
        assertThat(LeaveType.fromCode("WRONG")).isNull();
    }

    @Test
    @DisplayName("覆盖全部 7 种类型")
    void allTypesPresent() {
        assertThat(LeaveType.values()).hasSize(7);
    }
}
