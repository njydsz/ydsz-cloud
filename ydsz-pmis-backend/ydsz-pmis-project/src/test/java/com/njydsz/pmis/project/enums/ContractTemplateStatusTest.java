package com.njydsz.pmis.project.enums;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ContractTemplateStatus 合同模板状态机单元测试
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("ContractTemplateStatus 合同模板状态机测试")
class ContractTemplateStatusTest {

    @Test
    @DisplayName("DEPRECATED 是终态")
    void terminalStates() {
        assertThat(ContractTemplateStatus.DEPRECATED.isTerminal()).isTrue();
        assertThat(ContractTemplateStatus.DRAFT.isTerminal()).isFalse();
        assertThat(ContractTemplateStatus.PUBLISHED.isTerminal()).isFalse();
    }

    @Test
    @DisplayName("DRAFT -> PUBLISHED")
    void draftTransition() {
        assertThat(ContractTemplateStatus.DRAFT.canTransitTo(ContractTemplateStatus.PUBLISHED)).isTrue();
        assertThat(ContractTemplateStatus.DRAFT.canTransitTo(ContractTemplateStatus.DEPRECATED)).isFalse();
    }

    @Test
    @DisplayName("PUBLISHED -> DEPRECATED 或回退 DRAFT")
    void publishedTransition() {
        assertThat(ContractTemplateStatus.PUBLISHED.canTransitTo(ContractTemplateStatus.DEPRECATED)).isTrue();
        assertThat(ContractTemplateStatus.PUBLISHED.canTransitTo(ContractTemplateStatus.DRAFT)).isTrue();
    }

    @Test
    @DisplayName("DEPRECATED 不能迁移")
    void deprecatedTerminal() {
        for (ContractTemplateStatus s : ContractTemplateStatus.values()) {
            if (s == ContractTemplateStatus.DEPRECATED) continue;
            assertThat(ContractTemplateStatus.DEPRECATED.canTransitTo(s)).isFalse();
        }
    }

    @Test
    @DisplayName("自身不允许迁移")
    void selfNotAllowed() {
        for (ContractTemplateStatus s : ContractTemplateStatus.values()) {
            assertThat(s.canTransitTo(s)).isFalse();
        }
    }

    @Test
    @DisplayName("canTransitTo null 应返回 false")
    void nullTarget() {
        assertThat(ContractTemplateStatus.DRAFT.canTransitTo(null)).isFalse();
    }

    @Test
    @DisplayName("fromCode 解析")
    void fromCode() {
        assertThat(ContractTemplateStatus.fromCode("DRAFT")).isEqualTo(ContractTemplateStatus.DRAFT);
        assertThat(ContractTemplateStatus.fromCode("published")).isEqualTo(ContractTemplateStatus.PUBLISHED);
        assertThat(ContractTemplateStatus.fromCode("XXX")).isNull();
        assertThat(ContractTemplateStatus.fromCode(null)).isNull();
    }
}
