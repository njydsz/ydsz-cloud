package com.njydsz.pmis.user.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * AttendanceStatus 枚举测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("AttendanceStatus 枚举")
class AttendanceStatusTest {

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(AttendanceStatus.fromCode("NORMAL")).isEqualTo(AttendanceStatus.NORMAL);
        assertThat(AttendanceStatus.fromCode("late")).isEqualTo(AttendanceStatus.LATE);
        assertThat(AttendanceStatus.fromCode(null)).isNull();
        assertThat(AttendanceStatus.fromCode("WRONG")).isNull();
    }

    @Test
    @DisplayName("描述非空")
    void descNotEmpty() {
        for (AttendanceStatus s : AttendanceStatus.values()) {
            assertThat(s.getCode()).isNotBlank();
            assertThat(s.getDesc()).isNotBlank();
        }
    }
}
