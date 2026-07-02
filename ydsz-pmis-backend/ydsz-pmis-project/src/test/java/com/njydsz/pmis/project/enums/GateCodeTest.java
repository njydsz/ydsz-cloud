package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * GateCode 门径评审点枚举单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("GateCode 门径评审点测试")
class GateCodeTest {

    @Test
    @DisplayName("fromCode 大小写无关")
    void fromCode() {
        assertThat(GateCode.fromCode("cd1")).isEqualTo(GateCode.CD1);
        assertThat(GateCode.fromCode("CD3")).isEqualTo(GateCode.CD3);
        assertThat(GateCode.fromCode(null)).isNull();
        assertThat(GateCode.fromCode("UNKNOWN")).isNull();
    }

    @Test
    @DisplayName("next 顺序 CD1->CD2->CD3->CD4->CD5->null")
    void next() {
        assertThat(GateCode.next(null)).isEqualTo(GateCode.CD1);
        assertThat(GateCode.next(GateCode.CD1)).isEqualTo(GateCode.CD2);
        assertThat(GateCode.next(GateCode.CD2)).isEqualTo(GateCode.CD3);
        assertThat(GateCode.next(GateCode.CD3)).isEqualTo(GateCode.CD4);
        assertThat(GateCode.next(GateCode.CD4)).isEqualTo(GateCode.CD5);
        assertThat(GateCode.next(GateCode.CD5)).isNull();
    }
}
