package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 合同模板状态机测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractTemplateStatus 合同模板状态机")
class ContractTemplateStatusTest {

    @Test
    @DisplayName("终态")
    void terminal() {
        assertThat(ContractTemplateStatus.DEPRECATED.isTerminal()).isTrue();
        assertThat(ContractTemplateStatus.DRAFT.isTerminal()).isFalse();
        assertThat(ContractTemplateStatus.PUBLISHED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DRAFT->PUBLISHED")
    void draftPub() {
        assertThat(ContractTemplateStatus.DRAFT.canTransitTo(ContractTemplateStatus.PUBLISHED)).isTrue();
        assertThat(ContractTemplateStatus.DRAFT.canTransitTo(ContractTemplateStatus.DEPRECATED)).isFalse();
    }

    @Test
    @DisplayName("PUBLISHED->DRAFT/DEPRECATED")
    void pubTrans() {
        assertThat(ContractTemplateStatus.PUBLISHED.canTransitTo(ContractTemplateStatus.DRAFT)).isTrue();
        assertThat(ContractTemplateStatus.PUBLISHED.canTransitTo(ContractTemplateStatus.DEPRECATED)).isTrue();
    }

    @Test
    @DisplayName("DEPRECATED 终态")
    void deprecatedNoTrans() {
        assertThat(ContractTemplateStatus.DEPRECATED.canTransitTo(ContractTemplateStatus.DRAFT)).isFalse();
        assertThat(ContractTemplateStatus.DEPRECATED.canTransitTo(ContractTemplateStatus.PUBLISHED)).isFalse();
    }

    @Test
    @DisplayName("自身不可迁移")
    void selfNoTrans() {
        assertThat(ContractTemplateStatus.DRAFT.canTransitTo(ContractTemplateStatus.DRAFT)).isFalse();
    }

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(ContractTemplateStatus.fromCode("DRAFT")).isEqualTo(ContractTemplateStatus.DRAFT);
        assertThat(ContractTemplateStatus.fromCode("published")).isEqualTo(ContractTemplateStatus.PUBLISHED);
        assertThat(ContractTemplateStatus.fromCode(null)).isNull();
        assertThat(ContractTemplateStatus.fromCode("XXX")).isNull();
    }
}
