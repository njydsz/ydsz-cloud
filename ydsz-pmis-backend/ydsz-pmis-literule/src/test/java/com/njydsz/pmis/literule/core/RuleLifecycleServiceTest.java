package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleEngineStats;
import com.njydsz.pmis.literule.api.RetirementSuggestion;
import com.njydsz.pmis.literule.api.RollbackPreview;
import com.njydsz.pmis.literule.api.RuleStatus;
import com.njydsz.pmis.literule.config.LiteRuleProperties;
import com.njydsz.pmis.literule.config.RuleAdminService;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleLifecycleService} 单元测试。
 *
 * <p>覆盖退役检测、回滚预览、一键回滚、一键退役、批量退役、生命周期概览等核心能力。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则生命周期管理服务测试")
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

    @InjectMocks
    private RuleLifecycleService lifecycleService;

    private static final DateTimeFormatter FMT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @BeforeEach
    void setUp() {
        // 使用默认配置参数
        lifecycleService.setDormantMinEvaluations(1000);
        lifecycleService.setHighErrorRateThreshold(0.30);
        lifecycleService.setStaleDisabledDays(90);
        lifecycleService.setLowImpactTriggerRate(0.001);
        lifecycleService.setMinSampleSize(500);
    }

    private RuleDefinition buildRule(String code, String status, int version) {
        return RuleDefinition.builder()
                .code(code)
                .name("规则-" + code)
                .conditionExpression("amount > 1000")
                .status(status)
                .version(version)
                .enabled(true)
                .build();
    }

    private RuleEngineStats.RuleStat buildStat(long exec, long triggered, long errors) {
        return RuleEngineStats.RuleStat.builder()
                .executions(exec)
                .triggered(triggered)
                .errors(errors)
                .totalElapsedMs(exec * 2L)
                .build();
    }

    private RuleEngineStats buildStats(Map<String, RuleEngineStats.RuleStat> perRule) {
        return RuleEngineStats.builder()
                .totalEvaluations(perRule.values().stream().mapToLong(RuleEngineStats.RuleStat::getExecutions).sum())
                .totalTriggered(perRule.values().stream().mapToLong(RuleEngineStats.RuleStat::getTriggered).sum())
                .totalErrors(perRule.values().stream().mapToLong(RuleEngineStats.RuleStat::getErrors).sum())
                .perRuleStats(perRule)
                .build();
    }

    @Nested
    @DisplayName("退役检测：detectRetirementCandidates")
    class DetectRetirementCandidatesTest {

        @Test
        @DisplayName("正常场景：规则列表为空时返回空列表")
        void shouldReturnEmptyWhenNoRules() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
            verify(configProvider).loadAllRules();
        }

        @Test
        @DisplayName("正常场景：规则列表为 null 时返回空列表")
        void shouldReturnEmptyWhenRulesNull() {
            when(configProvider.loadAllRules()).thenReturn(null);

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：已归档规则不参与检测")
        void shouldSkipArchivedRules() {
            RuleDefinition archived = buildRule("R_ARCHIVED", "ARCHIVED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(archived));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：休眠规则（评估次数达标且零触发）被识别")
        void shouldDetectDormantRule() {
            RuleDefinition rule = buildRule("R_DORMANT", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R_DORMANT", buildStat(2000, 0, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isEqualTo(RetirementSuggestion.Reason.DORMANT);
            assertThat(result.get(0).getRuleCode()).isEqualTo("R_DORMANT");
            assertThat(result.get(0).getTotalEvaluations()).isEqualTo(2000);
            assertThat(result.get(0).getConfidence()).isGreaterThan(0.0);
        }

        @Test
        @DisplayName("正常场景：高错误率规则被识别")
        void shouldDetectHighErrorRateRule() {
            RuleDefinition rule = buildRule("R_ERR", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R_ERR", buildStat(1000, 100, 400));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isEqualTo(RetirementSuggestion.Reason.HIGH_ERROR_RATE);
        }

        @Test
        @DisplayName("正常场景：低影响规则被识别")
        void shouldDetectLowImpactRule() {
            RuleDefinition rule = buildRule("R_LOW", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R_LOW", buildStat(1000, 1, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isEqualTo(RetirementSuggestion.Reason.LOW_IMPACT);
        }

        @Test
        @DisplayName("正常场景：长期停用规则被识别")
        void shouldDetectStaleDisabledRule() {
            String oldDate = LocalDateTime.now().minusDays(120).format(FMT);
            RuleDefinition rule = buildRule("R_STALE", "DISABLED", 1);
            rule.setEffectiveTo(oldDate);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getReason()).isEqualTo(RetirementSuggestion.Reason.STALE_DISABLED);
        }

        @Test
        @DisplayName("正常场景：样本量不足的规则不生成建议")
        void shouldNotGenerateSuggestionWhenSampleInsufficient() {
            RuleDefinition rule = buildRule("R_SMALL", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R_SMALL", buildStat(100, 0, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：多条建议按置信度降序排列")
        void shouldSortByConfidenceDesc() {
            RuleDefinition rule1 = buildRule("R1", "PUBLISHED", 1);
            RuleDefinition rule2 = buildRule("R2", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule1, rule2));
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R1", buildStat(2000, 0, 0));
            perRule.put("R2", buildStat(1500, 0, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).hasSize(2);
            assertThat(result.get(0).getConfidence())
                    .isGreaterThanOrEqualTo(result.get(1).getConfidence());
        }

        @Test
        @DisplayName("边界场景：stats 为 null 时不抛异常")
        void shouldHandleNullStats() {
            RuleDefinition rule = buildRule("R_NULL_STATS", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(ruleEngine.getStats()).thenReturn(null);

            List<RetirementSuggestion> result = lifecycleService.detectRetirementCandidates();

            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("退役检测：detectRetirement")
    class DetectRetirementTest {

        @Test
        @DisplayName("边界场景：ruleCode 为 null 返回 null")
        void shouldReturnNullWhenRuleCodeNull() {
            assertThat(lifecycleService.detectRetirement(null)).isNull();
        }

        @Test
        @DisplayName("边界场景：ruleCode 为空字符串返回 null")
        void shouldReturnNullWhenRuleCodeBlank() {
            assertThat(lifecycleService.detectRetirement("  ")).isNull();
        }

        @Test
        @DisplayName("边界场景：规则不存在返回 null")
        void shouldReturnNullWhenRuleNotFound() {
            when(configProvider.findByCode("R_NOT_EXIST")).thenReturn(null);

            assertThat(lifecycleService.detectRetirement("R_NOT_EXIST")).isNull();
        }

        @Test
        @DisplayName("边界场景：已归档规则返回 null")
        void shouldReturnNullWhenArchived() {
            RuleDefinition rule = buildRule("R_ARCH", "ARCHIVED", 1);
            when(configProvider.findByCode("R_ARCH")).thenReturn(rule);

            assertThat(lifecycleService.detectRetirement("R_ARCH")).isNull();
        }

        @Test
        @DisplayName("正常场景：休眠规则返回建议")
        void shouldReturnSuggestionForDormantRule() {
            RuleDefinition rule = buildRule("R_D", "PUBLISHED", 1);
            when(configProvider.findByCode("R_D")).thenReturn(rule);
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R_D", buildStat(2000, 0, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            RetirementSuggestion result = lifecycleService.detectRetirement("R_D");

            assertThat(result).isNotNull();
            assertThat(result.getReason()).isEqualTo(RetirementSuggestion.Reason.DORMANT);
        }
    }

    @Nested
    @DisplayName("回滚预览：previewRollback")
    class PreviewRollbackTest {

        @Test
        @DisplayName("边界场景：versionRepository 为 null 时返回不允许回滚")
        void shouldReturnNotAllowedWhenVersionRepoNull() {
            RuleLifecycleService service = new RuleLifecycleService(
                    ruleEngine, configProvider, ruleAdminService, null);

            RollbackPreview preview = service.previewRollback("R001", 1);

            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("版本仓库未配置");
        }

        @Test
        @DisplayName("边界场景：规则不存在返回不允许回滚")
        void shouldReturnNotAllowedWhenRuleNotFound() {
            when(configProvider.findByCode("R_NOT_EXIST")).thenReturn(null);

            RollbackPreview preview = lifecycleService.previewRollback("R_NOT_EXIST", 1);

            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("规则不存在");
        }

        @Test
        @DisplayName("边界场景：目标版本不存在返回不允许回滚")
        void shouldReturnNotAllowedWhenVersionNotFound() {
            RuleDefinition rule = buildRule("R001", "PUBLISHED", 2);
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(versionRepository.listVersions("R001")).thenReturn(List.of());

            RollbackPreview preview = lifecycleService.previewRollback("R001", 5);

            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("目标版本不存在");
        }

        @Test
        @DisplayName("边界场景：ARCHIVED 状态规则不允许回滚")
        void shouldReturnNotAllowedWhenArchived() {
            RuleDefinition rule = buildRule("R_ARCH", "ARCHIVED", 2);
            when(configProvider.findByCode("R_ARCH")).thenReturn(rule);
            RuleVersion version = RuleVersion.builder()
                    .ruleCode("R_ARCH").version(1)
                    .definitionJson("{\"name\":\"old\"}")
                    .operator("admin").build();
            when(versionRepository.listVersions("R_ARCH")).thenReturn(List.of(version));

            RollbackPreview preview = lifecycleService.previewRollback("R_ARCH", 1);

            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("已归档");
        }

        @Test
        @DisplayName("正常场景：生成字段差异列表")
        void shouldGenerateDiffs() {
            RuleDefinition current = RuleDefinition.builder()
                    .code("R001").name("新名称").status("PUBLISHED").version(2)
                    .conditionExpression("amount > 2000").build();
            when(configProvider.findByCode("R001")).thenReturn(current);
            RuleVersion version = RuleVersion.builder()
                    .ruleCode("R001").version(1)
                    .definitionJson("{\"name\":\"旧名称\",\"conditionExpression\":\"amount > 1000\"}")
                    .operator("admin").changeDesc("初始版本").build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(version));

            RollbackPreview preview = lifecycleService.previewRollback("R001", 1);

            assertThat(preview.isRollbackAllowed()).isTrue();
            assertThat(preview.getDiffCount()).isGreaterThan(0);
            assertThat(preview.getCurrentVersion()).isEqualTo(2);
            assertThat(preview.getTargetVersion()).isEqualTo(1);
        }

        @Test
        @DisplayName("边界场景：版本 JSON 为空时仍返回预览")
        void shouldHandleEmptyJson() {
            RuleDefinition rule = buildRule("R001", "PUBLISHED", 2);
            when(configProvider.findByCode("R001")).thenReturn(rule);
            RuleVersion version = RuleVersion.builder()
                    .ruleCode("R001").version(1)
                    .definitionJson("")
                    .operator("admin").build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(version));

            RollbackPreview preview = lifecycleService.previewRollback("R001", 1);

            assertThat(preview.isRollbackAllowed()).isTrue();
        }
    }

    @Nested
    @DisplayName("一键回滚：rollback")
    class RollbackTest {

        @Test
        @DisplayName("异常场景：预览不允许回滚时抛 IllegalStateException")
        void shouldThrowWhenRollbackNotAllowed() {
            RuleLifecycleService service = new RuleLifecycleService(
                    ruleEngine, configProvider, ruleAdminService, null);

            assertThatThrownBy(() -> service.rollback("R001", 1, "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("回滚被拒绝");
        }

        @Test
        @DisplayName("正常场景：预览通过后委托 ruleAdminService 执行回滚")
        void shouldDelegateToAdminServiceWhenAllowed() {
            RuleDefinition current = buildRule("R001", "PUBLISHED", 2);
            when(configProvider.findByCode("R001")).thenReturn(current);
            RuleVersion version = RuleVersion.builder()
                    .ruleCode("R001").version(1)
                    .definitionJson("{\"name\":\"old\"}")
                    .operator("admin").build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(version));
            RuleDefinition restored = buildRule("R001", "PUBLISHED", 1);
            when(ruleAdminService.rollback("R001", 1, "admin")).thenReturn(restored);

            RuleDefinition result = lifecycleService.rollback("R001", 1, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo(1);
            verify(ruleAdminService).rollback("R001", 1, "admin");
        }
    }

    @Nested
    @DisplayName("一键退役：retireRule")
    class RetireRuleTest {

        @Test
        @DisplayName("异常场景：规则不存在抛 IllegalArgumentException")
        void shouldThrowWhenRuleNotExist() {
            when(configProvider.findByCode("R_NOT_EXIST")).thenReturn(null);

            assertThatThrownBy(() -> lifecycleService.retireRule("R_NOT_EXIST", "admin", "测试"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("规则不存在");
        }

        @Test
        @DisplayName("异常场景：已归档规则抛 IllegalStateException")
        void shouldThrowWhenAlreadyArchived() {
            RuleDefinition rule = buildRule("R_ARCH", "ARCHIVED", 1);
            when(configProvider.findByCode("R_ARCH")).thenReturn(rule);

            assertThatThrownBy(() -> lifecycleService.retireRule("R_ARCH", "admin", "测试"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已归档");
        }

        @Test
        @DisplayName("正常场景：PUBLISHED 规则可退役为 ARCHIVED")
        void shouldRetirePublishedRule() {
            RuleDefinition rule = buildRule("R001", "PUBLISHED", 1);
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(ruleAdminService.save(any(RuleDefinition.class), eq("admin"), anyString()))
                    .thenReturn(rule);

            RuleDefinition result = lifecycleService.retireRule("R001", "admin", "休眠规则");

            assertThat(result).isNotNull();
            assertThat(rule.getStatus()).isEqualTo(RuleStatus.ARCHIVED.name());
            assertThat(rule.isEnabled()).isFalse();
            verify(ruleAdminService).save(any(RuleDefinition.class), eq("admin"), anyString());
        }

        @Test
        @DisplayName("正常场景：DISABLED 规则可退役为 ARCHIVED")
        void shouldRetireDisabledRule() {
            RuleDefinition rule = buildRule("R002", "DISABLED", 1);
            when(configProvider.findByCode("R002")).thenReturn(rule);
            when(ruleAdminService.save(any(RuleDefinition.class), eq("admin"), anyString()))
                    .thenReturn(rule);

            RuleDefinition result = lifecycleService.retireRule("R002", "admin", "长期停用");

            assertThat(result).isNotNull();
            assertThat(rule.getStatus()).isEqualTo(RuleStatus.ARCHIVED.name());
        }
    }

    @Nested
    @DisplayName("批量退役：bulkRetire")
    class BulkRetireTest {

        @Test
        @DisplayName("边界场景：空列表返回空结果")
        void shouldReturnEmptyWhenListEmpty() {
            Map<String, String> result = lifecycleService.bulkRetire(null, "admin", "测试");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：部分成功部分失败")
        void shouldHandlePartialSuccess() {
            RuleDefinition okRule = buildRule("R_OK", "PUBLISHED", 1);
            when(configProvider.findByCode("R_OK")).thenReturn(okRule);
            when(configProvider.findByCode("R_FAIL")).thenReturn(null);
            when(ruleAdminService.save(any(RuleDefinition.class), eq("admin"), anyString()))
                    .thenReturn(okRule);

            Map<String, String> result = lifecycleService.bulkRetire(
                    List.of("R_OK", "R_FAIL"), "admin", "批量退役");

            assertThat(result).hasSize(2);
            assertThat(result.get("R_OK")).isEqualTo("SUCCESS");
            assertThat(result.get("R_FAIL")).startsWith("FAILED:");
        }
    }

    @Nested
    @DisplayName("生命周期概览：getLifecycleSummary")
    class GetLifecycleSummaryTest {

        @Test
        @DisplayName("正常场景：按状态统计规则数量")
        void shouldCountByStatus() {
            RuleDefinition r1 = buildRule("R1", "PUBLISHED", 1);
            RuleDefinition r2 = buildRule("R2", "PUBLISHED", 1);
            RuleDefinition r3 = buildRule("R3", "DRAFT", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2, r3));

            Map<String, Integer> summary = lifecycleService.getLifecycleSummary();

            assertThat(summary.get("PUBLISHED")).isEqualTo(2);
            assertThat(summary.get("DRAFT")).isEqualTo(1);
            assertThat(summary.get("ARCHIVED")).isEqualTo(0);
        }

        @Test
        @DisplayName("边界场景：规则状态为空时默认为 PUBLISHED")
        void shouldDefaultToPublishedWhenStatusBlank() {
            RuleDefinition rule = buildRule("R1", null, 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));

            Map<String, Integer> summary = lifecycleService.getLifecycleSummary();

            assertThat(summary.get("PUBLISHED")).isEqualTo(1);
        }

        @Test
        @DisplayName("边界场景：规则列表为 null 时所有状态计数为 0")
        void shouldReturnZerosWhenNull() {
            when(configProvider.loadAllRules()).thenReturn(null);

            Map<String, Integer> summary = lifecycleService.getLifecycleSummary();

            assertThat(summary).isNotEmpty();
            assertThat(summary.get("PUBLISHED")).isEqualTo(0);
        }
    }

    @Nested
    @DisplayName("退役候选计数：getRetirementCandidateCount")
    class GetRetirementCandidateCountTest {

        @Test
        @DisplayName("正常场景：返回退役候选数量")
        void shouldReturnCandidateCount() {
            RuleDefinition rule = buildRule("R_DORMANT", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            Map<String, RuleEngineStats.RuleStat> perRule = new HashMap<>();
            perRule.put("R_DORMANT", buildStat(2000, 0, 0));
            when(ruleEngine.getStats()).thenReturn(buildStats(perRule));

            int count = lifecycleService.getRetirementCandidateCount();

            assertThat(count).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("配置：configure")
    class ConfigureTest {

        @Test
        @DisplayName("边界场景：配置为 null 时直接返回")
        void shouldReturnWhenConfigNull() {
            lifecycleService.configure(null);

            // 不抛异常即视为通过
        }

        @Test
        @DisplayName("正常场景：从配置对象初始化阈值参数")
        void shouldConfigureFromProperties() {
            LiteRuleProperties.LifecycleConfig config = new LiteRuleProperties.LifecycleConfig();
            config.setDormantMinEvaluations(5000);
            config.setHighErrorRateThreshold(0.5);
            config.setStaleDisabledDays(180);
            config.setLowImpactTriggerRate(0.005);
            config.setMinSampleSize(1000);

            lifecycleService.configure(config);

            assertThat(lifecycleService.getDormantMinEvaluations()).isEqualTo(5000);
            assertThat(lifecycleService.getHighErrorRateThreshold()).isEqualTo(0.5);
            assertThat(lifecycleService.getStaleDisabledDays()).isEqualTo(180);
            assertThat(lifecycleService.getLowImpactTriggerRate()).isEqualTo(0.005);
            assertThat(lifecycleService.getMinSampleSize()).isEqualTo(1000);
        }
    }
}
