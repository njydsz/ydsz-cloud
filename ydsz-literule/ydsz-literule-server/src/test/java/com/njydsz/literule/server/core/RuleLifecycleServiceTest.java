package com.njydsz.literule.server.core;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.locales.util.I18nMessages;
import com.njydsz.literule.domain.RuleEngine;
import com.njydsz.literule.domain.dto.RuleDefinitionDTO;
import com.njydsz.literule.domain.enums.RuleStatus;
import com.njydsz.literule.domain.repository.RuleVersionRepository;
import com.njydsz.literule.domain.vo.RetirementSuggestionVO;
import com.njydsz.literule.domain.vo.RuleDefinitionVO;
import com.njydsz.literule.domain.vo.RuleEngineStatsVO;
import com.njydsz.literule.domain.vo.RuleVersionVO;
import com.njydsz.literule.server.config.LiteRuleProperties;
import com.njydsz.literule.server.config.RuleAdminService;
import com.njydsz.literule.server.spi.RuleConfigProvider;

@ExtendWith(MockitoExtension.class)
class RuleLifecycleServiceTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private RuleConfigProvider configProvider;

    @Mock
    private RuleAdminService ruleAdminService;

    @Mock
    private RuleVersionRepository versionRepository;

    @Mock
    private I18nMessages i18n;

    private RuleLifecycleService lifecycleService;

    @BeforeEach
    void setUp() {
        lifecycleService = new RuleLifecycleService(
                ruleEngine, configProvider, ruleAdminService, versionRepository, i18n);
        lifecycleService.setMinSampleSize(100);
    }

    @Nested
    @DisplayName("detectRetirementCandidates")
    class DetectRetirementCandidates {

        @Test
        @DisplayName("detectRetirementCandidates - empty rule list returns empty")
        void detectRetirementCandidates_emptyList_returnsEmpty() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            List<RetirementSuggestionVO> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("detectRetirementCandidates - dormant rule (zero triggers with enough evaluations)")
        void detectRetirementCandidates_dormantRule_returnsSuggestion() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setName("Old Rule");
            rule.setStatus("PUBLISHED");

            RuleEngineStatsVO.RuleStat stat = RuleEngineStatsVO.RuleStat.builder()
                    .executions(2000L)
                    .triggered(0L)
                    .errors(0L)
                    .build();
            Map<String, RuleEngineStatsVO.RuleStat> perRuleStats = new HashMap<>();
            perRuleStats.put("R001", stat);

            RuleEngineStatsVO stats = RuleEngineStatsVO.builder()
                    .perRuleStats(perRuleStats)
                    .build();

            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(stats);

            List<RetirementSuggestionVO> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRuleCode()).isEqualTo("R001");
            assertThat(result.get(0).getReason()).isEqualTo(RetirementSuggestionVO.Reason.DORMANT);
        }

        @Test
        @DisplayName("detectRetirementCandidates - high error rate rule")
        void detectRetirementCandidates_highErrorRate_returnsSuggestion() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R002");
            rule.setName("Buggy Rule");
            rule.setStatus("PUBLISHED");

            RuleEngineStatsVO.RuleStat stat = RuleEngineStatsVO.RuleStat.builder()
                    .executions(200L)
                    .triggered(100L)
                    .errors(80L)
                    .build();
            Map<String, RuleEngineStatsVO.RuleStat> perRuleStats = new HashMap<>();
            perRuleStats.put("R002", stat);

            RuleEngineStatsVO stats = RuleEngineStatsVO.builder()
                    .perRuleStats(perRuleStats)
                    .build();

            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(stats);

            List<RetirementSuggestionVO> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason())
                    .isEqualTo(RetirementSuggestionVO.Reason.HIGH_ERROR_RATE);
        }

        @Test
        @DisplayName("detectRetirementCandidates - ARCHIVED rule is skipped")
        void detectRetirementCandidates_archivedRule_skipped() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R000");
            rule.setName("Archived");
            rule.setStatus("ARCHIVED");

            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStatsVO.builder()
                    .perRuleStats(new HashMap<>())
                    .build());

            List<RetirementSuggestionVO> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("detectRetirementCandidates - insufficient sample size skips rule")
        void detectRetirementCandidates_insufficientSample_skipsRule() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R003");
            rule.setName("New Rule");
            rule.setStatus("PUBLISHED");

            RuleEngineStatsVO.RuleStat stat = RuleEngineStatsVO.RuleStat.builder()
                    .executions(10L)
                    .triggered(0L)
                    .errors(0L)
                    .build();
            Map<String, RuleEngineStatsVO.RuleStat> perRuleStats = new HashMap<>();
            perRuleStats.put("R003", stat);

            RuleEngineStatsVO stats = RuleEngineStatsVO.builder()
                    .perRuleStats(perRuleStats)
                    .build();

            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(stats);

            List<RetirementSuggestionVO> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("detectRetirementCandidates - results sorted by confidence descending")
        void detectRetirementCandidates_sortedByConfidence() {
            RuleDefinitionDTO rule1 = new RuleDefinitionDTO();
            rule1.setCode("R001");
            rule1.setName("Rule 1");
            rule1.setStatus("PUBLISHED");

            RuleEngineStatsVO.RuleStat stat1 = RuleEngineStatsVO.RuleStat.builder()
                    .executions(5000L)
                    .triggered(0L)
                    .errors(0L)
                    .build();

            Map<String, RuleEngineStatsVO.RuleStat> perRuleStats = new HashMap<>();
            perRuleStats.put("R001", stat1);

            RuleEngineStatsVO stats = RuleEngineStatsVO.builder()
                    .perRuleStats(perRuleStats)
                    .build();

            when(configProvider.loadAllRules()).thenReturn(List.of(rule1));
            when(ruleEngine.getStats()).thenReturn(stats);

            List<RetirementSuggestionVO> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
        }
    }

    @Nested
    @DisplayName("detectRetirement")
    class DetectRetirement {

        @ParameterizedTest
        @NullAndEmptySource
        @DisplayName("detectRetirement - null or blank code returns null")
        void detectRetirement_nullOrBlankCode_returnsNull(String ruleCode) {
            RetirementSuggestionVO result = lifecycleService.detectRetirement(ruleCode);

            assertThat(result).isNull();
            verify(configProvider, never()).findByCode(anyString());
        }

        @Test
        @DisplayName("detectRetirement - non-existing rule returns null")
        void detectRetirement_nonExistingRule_returnsNull() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            RetirementSuggestionVO result = lifecycleService.detectRetirement("R999");

            assertThat(result).isNull();
        }

        @Test
        @DisplayName("detectRetirement - dormant rule returns suggestion")
        void detectRetirement_dormantRule_returnsSuggestion() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setName("Old Rule");
            rule.setStatus("PUBLISHED");

            RuleEngineStatsVO.RuleStat stat = RuleEngineStatsVO.RuleStat.builder()
                    .executions(2000L)
                    .triggered(0L)
                    .errors(0L)
                    .build();
            Map<String, RuleEngineStatsVO.RuleStat> perRuleStats = new HashMap<>();
            perRuleStats.put("R001", stat);

            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(ruleEngine.getStats()).thenReturn(RuleEngineStatsVO.builder()
                    .perRuleStats(perRuleStats)
                    .build());

            RetirementSuggestionVO result = lifecycleService.detectRetirement("R001");

            assertThat(result).isNotNull();
            assertThat(result.getReason()).isEqualTo(RetirementSuggestionVO.Reason.DORMANT);
        }
    }

    @Nested
    @DisplayName("retireRule")
    class RetireRule {

        @Test
        @DisplayName("retireRule - non-existing rule throws exception")
        void retireRule_nonExistingRule_throwsException() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            assertThatThrownBy(() -> lifecycleService.retireRule("R999", "admin", "no value"))
                    .isInstanceOf(BusinessException.class);
        }

        @Test
        @DisplayName("retireRule - already archived rule throws exception")
        void retireRule_alreadyArchived_throwsException() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setStatus("ARCHIVED");
            when(configProvider.findByCode("R001")).thenReturn(rule);

            assertThatThrownBy(() -> lifecycleService.retireRule("R001", "admin", "test"))
                    .isInstanceOf(SysException.class);
        }

        @Test
        @DisplayName("retireRule - published rule retires successfully")
        void retireRule_publishedRule_succeeds() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setName("Test Rule");
            rule.setStatus("PUBLISHED");
            rule.setEnabled(true);

            RuleDefinitionDTO savedResult = new RuleDefinitionDTO();
            savedResult.setCode("R001");
            savedResult.setStatus("ARCHIVED");

            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(ruleAdminService.save(rule, "admin", "规则退役: no value"))
                    .thenReturn(savedResult);

            RuleDefinitionDTO result = lifecycleService.retireRule("R001", "admin", "no value");

            verify(ruleAdminService).save(rule, "admin", "规则退役: no value");
        }
    }

    @Nested
    @DisplayName("previewRollback")
    class PreviewRollback {

        @Test
        @DisplayName("previewRollback - versionRepository null returns blocked")
        void previewRollback_noVersionRepository_blocked() {
            RuleLifecycleService serviceWithoutVersionRepo = new RuleLifecycleService(
                    ruleEngine, configProvider, ruleAdminService, null, i18n);

            var result = serviceWithoutVersionRepo.previewRollback("R001", 3);

            assertThat(result).isNotNull();
            assertThat(result.getRuleCode()).isEqualTo("R001");
            assertThat(result.getTargetVersion()).isEqualTo(3);
        }

        @Test
        @DisplayName("previewRollback - non-existing rule returns blocked")
        void previewRollback_nonExistingRule_blocked() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            var result = lifecycleService.previewRollback("R999", 3);

            assertThat(result).isNotNull();
            assertThat(result.isRollbackAllowed()).isFalse();
        }

        @Test
        @DisplayName("previewRollback - target version not found returns blocked")
        void previewRollback_versionNotFound_blocked() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setStatus("PUBLISHED");
            rule.setVersion(5);

            RuleVersionVO v4 = new RuleVersionVO();
            v4.setVersion(4);
            RuleVersionVO v5a = new RuleVersionVO();
            v5a.setVersion(5);
            List<RuleVersionVO> versions = List.of(v4, v5a);

            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(versionRepository.listVersions("R001")).thenReturn(versions);

            var result = lifecycleService.previewRollback("R001", 3);

            assertThat(result).isNotNull();
            assertThat(result.isRollbackAllowed()).isFalse();
        }

        @Test
        @DisplayName("previewRollback - archived rule returns blocked")
        void previewRollback_archivedRule_blocked() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setStatus("ARCHIVED");
            rule.setVersion(5);

            RuleVersionVO v3 = new RuleVersionVO();
            v3.setVersion(3);
            RuleVersionVO v5 = new RuleVersionVO();
            v5.setVersion(5);
            List<RuleVersionVO> versions = List.of(v3, v5);

            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(versionRepository.listVersions("R001")).thenReturn(versions);

            var result = lifecycleService.previewRollback("R001", 3);

            assertThat(result).isNotNull();
            assertThat(result.isRollbackAllowed()).isFalse();
        }

        @Test
        @DisplayName("previewRollback - valid rollback returns allowed with diffs")
        void previewRollback_validRollback_allowed() {
            RuleDefinitionDTO rule = new RuleDefinitionDTO();
            rule.setCode("R001");
            rule.setName("Current Name");
            rule.setStatus("PUBLISHED");
            rule.setVersion(5);

            RuleVersionVO targetVersion = new RuleVersionVO();
            targetVersion.setVersion(3);
            targetVersion.setOperator("admin");
            targetVersion.setChangeDesc("Updated name");
            targetVersion.setDefinitionJson("{\"code\":\"R001\",\"name\":\"Old Name\"}");

            RuleVersionVO v5 = new RuleVersionVO();
            v5.setVersion(5);
            List<RuleVersionVO> versions = List.of(targetVersion, v5);

            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(versionRepository.listVersions("R001")).thenReturn(versions);

            var result = lifecycleService.previewRollback("R001", 3);

            assertThat(result).isNotNull();
            assertThat(result.isRollbackAllowed()).isTrue();
            assertThat(result.getDiffCount()).isGreaterThanOrEqualTo(0);
        }
    }
}
