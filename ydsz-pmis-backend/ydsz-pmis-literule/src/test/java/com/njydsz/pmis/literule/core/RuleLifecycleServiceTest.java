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
import org.mockito.Mockito;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * RuleLifecycleService 单元测试（P3-1 规则生命周期管理增强）
 *
 * <p>测试目标：验证生命周期管理服务的核心能力，包括：
 * <ul>
 *   <li>退役检测（休眠规则、高错误率、长期停用、低影响）</li>
 *   <li>回滚预览（字段差异对比、回滚安全性校验）</li>
 *   <li>一键退役（状态变更、禁用、审计记录）</li>
 *   <li>批量退役（部分成功部分失败）</li>
 *   <li>生命周期概览（按状态统计）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleLifecycleService 单元测试")
class RuleLifecycleServiceTest {

    private RuleEngine ruleEngine;
    private RuleConfigProvider configProvider;
    private RuleAdminService ruleAdminService;
    private RuleVersionRepository versionRepository;
    private RuleLifecycleService service;

    @BeforeEach
    void setUp() {
        ruleEngine = mock(RuleEngine.class);
        configProvider = mock(RuleConfigProvider.class);
        ruleAdminService = mock(RuleAdminService.class);
        versionRepository = mock(RuleVersionRepository.class);
        service = new RuleLifecycleService(
                ruleEngine, configProvider, ruleAdminService, versionRepository);
        statMap.clear();
    }

    // ==================== 退役检测 ====================

    @Nested
    @DisplayName("退役检测")
    class RetirementDetectionTest {

        @Test
        @DisplayName("休眠规则 — 评估次数达标且零触发 → DORMANT")
        void shouldDetectDormantRule() {
            // 规则：评估 2000 次零触发
            setupRule("R001", "休眠规则", "PUBLISHED", true);
            setupStats("R001", 2000, 0, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).hasSize(1);
            RetirementSuggestion s = suggestions.get(0);
            assertThat(s.getRuleCode()).isEqualTo("R001");
            assertThat(s.getReason()).isEqualTo(RetirementSuggestion.Reason.DORMANT);
            assertThat(s.getTotalEvaluations()).isEqualTo(2000);
            assertThat(s.getTotalTriggered()).isEqualTo(0);
            assertThat(s.getTriggerRate()).isEqualTo(0.0);
            assertThat(s.getRecommendedActions()).isNotEmpty();
            assertThat(s.getConfidence()).isGreaterThan(0.0).isLessThanOrEqualTo(1.0);
        }

        @Test
        @DisplayName("高错误率规则 — 错误率超过阈值 → HIGH_ERROR_RATE")
        void shouldDetectHighErrorRateRule() {
            setupRule("R002", "高错误率规则", "PUBLISHED", true);
            // 1000 次评估，500 次触发，400 次错误 → 错误率 40%
            setupStats("R002", 1000, 500, 400);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).hasSize(1);
            RetirementSuggestion s = suggestions.get(0);
            assertThat(s.getReason()).isEqualTo(RetirementSuggestion.Reason.HIGH_ERROR_RATE);
            assertThat(s.getErrorRate()).isCloseTo(0.40, org.assertj.core.data.Offset.offset(0.001));
        }

        @Test
        @DisplayName("低影响规则 — 触发率极低 → LOW_IMPACT")
        void shouldDetectLowImpactRule() {
            setupRule("R003", "低影响规则", "PUBLISHED", true);
            // 10000 次评估，5 次触发 → 触发率 0.0005
            setupStats("R003", 10000, 5, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).hasSize(1);
            RetirementSuggestion s = suggestions.get(0);
            assertThat(s.getReason()).isEqualTo(RetirementSuggestion.Reason.LOW_IMPACT);
            assertThat(s.getTriggerRate()).isCloseTo(0.0005, org.assertj.core.data.Offset.offset(0.0001));
        }

        @Test
        @DisplayName("长期停用规则 — 停用超过 90 天 → STALE_DISABLED")
        void shouldDetectStaleDisabledRule() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R004")
                    .name("长期停用规则")
                    .status("DISABLED")
                    .enabled(false)
                    .effectiveTo(LocalDateTime.now().minusDays(120)
                            .format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
                    .build();
            when(configProvider.loadAllRules()).thenReturn(List.of(rule));
            when(configProvider.findByCode("R004")).thenReturn(rule);
            setupStats("R004", 0, 0, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).hasSize(1);
            RetirementSuggestion s = suggestions.get(0);
            assertThat(s.getReason()).isEqualTo(RetirementSuggestion.Reason.STALE_DISABLED);
        }

        @Test
        @DisplayName("正常规则 — 不生成退役建议")
        void shouldNotDetectNormalRule() {
            setupRule("R005", "正常规则", "PUBLISHED", true);
            // 2000 次评估，500 次触发，10 次错误 → 触发率 25%，错误率 0.5%
            setupStats("R005", 2000, 500, 10);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("已归档规则 — 跳过检测")
        void shouldSkipArchivedRule() {
            setupRule("R006", "已归档规则", "ARCHIVED", false);
            setupStats("R006", 5000, 0, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("数据不足 — 评估次数低于最小样本量 → 不生成建议")
        void shouldNotDetectWhenInsufficientData() {
            setupRule("R007", "低样本规则", "PUBLISHED", true);
            // 仅 100 次评估，低于默认最小样本量 500
            setupStats("R007", 100, 0, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).isEmpty();
        }

        @Test
        @DisplayName("多条退役候选 — 按置信度降序排列")
        void shouldSortByConfidenceDesc() {
            setupRule("R008", "规则A", "PUBLISHED", true);
            setupRule("R009", "规则B", "PUBLISHED", true);
            // R008: 5000 次评估零触发
            setupStats("R008", 5000, 0, 0);
            // R009: 1000 次评估零触发
            setupStats("R009", 1000, 0, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).hasSize(2);
            assertThat(suggestions.get(0).getConfidence())
                    .isGreaterThanOrEqualTo(suggestions.get(1).getConfidence());
        }

        @Test
        @DisplayName("检测指定单条规则 — 返回退役建议")
        void shouldDetectSingleRule() {
            setupRule("R010", "单条检测", "PUBLISHED", true);
            setupStats("R010", 2000, 0, 0);

            RetirementSuggestion suggestion = service.detectRetirement("R010");

            assertThat(suggestion).isNotNull();
            assertThat(suggestion.getRuleCode()).isEqualTo("R010");
            assertThat(suggestion.getReason()).isEqualTo(RetirementSuggestion.Reason.DORMANT);
        }

        @Test
        @DisplayName("空规则列表 — 返回空列表")
        void shouldHandleEmptyRules() {
            when(configProvider.loadAllRules()).thenReturn(List.of());
            when(ruleEngine.getStats()).thenReturn(RuleEngineStats.empty());

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).isEmpty();
        }
    }

    // ==================== 回滚预览 ====================

    @Nested
    @DisplayName("回滚预览")
    class RollbackPreviewTest {

        @Test
        @DisplayName("正常回滚预览 — 生成字段差异")
        void shouldGenerateDiffPreview() {
            RuleDefinition current = RuleDefinition.builder()
                    .code("R001")
                    .name("规则-V3")
                    .description("V3描述")
                    .conditionExpression("a > 10")
                    .status("PUBLISHED")
                    .version(3)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(current);

            // 目标版本 V2 的 JSON
            String v2Json = "{\"name\":\"规则-V2\",\"description\":\"V2描述\",\"conditionExpression\":\"a > 5\",\"status\":\"PUBLISHED\"}";
            RuleVersion v2 = RuleVersion.builder()
                    .ruleCode("R001")
                    .version(2)
                    .definitionJson(v2Json)
                    .operator("admin")
                    .changeDesc("调整阈值")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(v2));

            RollbackPreview preview = service.previewRollback("R001", 2);

            assertThat(preview).isNotNull();
            assertThat(preview.getRuleCode()).isEqualTo("R001");
            assertThat(preview.getCurrentVersion()).isEqualTo(3);
            assertThat(preview.getTargetVersion()).isEqualTo(2);
            assertThat(preview.isRollbackAllowed()).isTrue();
            assertThat(preview.getTargetVersionOperator()).isEqualTo("admin");
            assertThat(preview.getTargetVersionChangeDesc()).isEqualTo("调整阈值");
            assertThat(preview.getDiffCount()).isGreaterThan(0);

            // 验证字段差异包含 conditionExpression 的变更
            assertThat(preview.getDiffs())
                    .anyMatch(d -> d.getField().equals("conditionExpression")
                            && d.getDiffType() == RollbackPreview.DiffType.MODIFIED);
        }

        @Test
        @DisplayName("规则不存在 — 返回不允许回滚")
        void shouldBlockWhenRuleNotFound() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            RollbackPreview preview = service.previewRollback("R999", 1);

            assertThat(preview).isNotNull();
            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("规则不存在");
        }

        @Test
        @DisplayName("目标版本不存在 — 返回不允许回滚")
        void shouldBlockWhenVersionNotFound() {
            setupRule("R001", "规则", "PUBLISHED", true);
            when(versionRepository.listVersions("R001")).thenReturn(List.of());

            RollbackPreview preview = service.previewRollback("R001", 99);

            assertThat(preview).isNotNull();
            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("目标版本不存在");
        }

        @Test
        @DisplayName("已归档规则 — 不允许回滚")
        void shouldBlockArchivedRule() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .name("已归档规则")
                    .status("ARCHIVED")
                    .version(3)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);

            String v2Json = "{\"name\":\"规则-V2\",\"status\":\"PUBLISHED\"}";
            RuleVersion v2 = RuleVersion.builder()
                    .ruleCode("R001")
                    .version(2)
                    .definitionJson(v2Json)
                    .operator("admin")
                    .changeDesc("V2")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(v2));

            RollbackPreview preview = service.previewRollback("R001", 2);

            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("已归档");
        }

        @Test
        @DisplayName("版本仓库未配置 — 返回不允许回滚")
        void shouldBlockWhenNoVersionRepository() {
            RuleLifecycleService serviceNoRepo = new RuleLifecycleService(
                    ruleEngine, configProvider, ruleAdminService, null);

            RollbackPreview preview = serviceNoRepo.previewRollback("R001", 1);

            assertThat(preview).isNotNull();
            assertThat(preview.isRollbackAllowed()).isFalse();
            assertThat(preview.getRollbackBlockedReason()).contains("版本仓库未配置");
        }

        @Test
        @DisplayName("字段差异 — ADDED 类型")
        void shouldDetectAddedField() {
            RuleDefinition current = RuleDefinition.builder()
                    .code("R001")
                    .name("规则")
                    .description(null)
                    .conditionExpression("a > 10")
                    .status("PUBLISHED")
                    .version(2)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(current);

            // V1 有 description 字段
            String v1Json = "{\"name\":\"规则\",\"description\":\"V1描述\",\"conditionExpression\":\"a > 10\",\"status\":\"PUBLISHED\"}";
            RuleVersion v1 = RuleVersion.builder()
                    .ruleCode("R001")
                    .version(1)
                    .definitionJson(v1Json)
                    .operator("admin")
                    .changeDesc("初始版本")
                    .createdAt(LocalDateTime.now().minusDays(2))
                    .build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(v1));

            RollbackPreview preview = service.previewRollback("R001", 1);

            assertThat(preview.getDiffs())
                    .anyMatch(d -> d.getField().equals("description")
                            && d.getDiffType() == RollbackPreview.DiffType.ADDED);
        }
    }

    // ==================== 一键回滚 ====================

    @Nested
    @DisplayName("一键回滚")
    class RollbackTest {

        @Test
        @DisplayName("正常回滚 — 委托 RuleAdminService 执行")
        void shouldRollbackSuccessfully() {
            RuleDefinition current = RuleDefinition.builder()
                    .code("R001")
                    .name("规则-V2")
                    .conditionExpression("a > 10")
                    .status("PUBLISHED")
                    .version(2)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(current);

            String v1Json = "{\"name\":\"规则-V1\",\"conditionExpression\":\"a > 5\",\"status\":\"PUBLISHED\"}";
            RuleVersion v1 = RuleVersion.builder()
                    .ruleCode("R001")
                    .version(1)
                    .definitionJson(v1Json)
                    .operator("admin")
                    .changeDesc("初始版本")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(v1));

            RuleDefinition restored = RuleDefinition.builder()
                    .code("R001")
                    .name("规则-V1")
                    .conditionExpression("a > 5")
                    .status("PUBLISHED")
                    .version(3)
                    .build();
            when(ruleAdminService.rollback("R001", 1, "admin")).thenReturn(restored);

            RuleDefinition result = service.rollback("R001", 1, "admin");

            assertThat(result).isNotNull();
            assertThat(result.getVersion()).isEqualTo(3);
            Mockito.verify(ruleAdminService).rollback("R001", 1, "admin");
        }

        @Test
        @DisplayName("回滚被拒绝 — 已归档规则抛异常")
        void shouldRejectRollbackForArchivedRule() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .name("已归档规则")
                    .status("ARCHIVED")
                    .version(2)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);

            String v1Json = "{\"name\":\"规则-V1\",\"status\":\"PUBLISHED\"}";
            RuleVersion v1 = RuleVersion.builder()
                    .ruleCode("R001")
                    .version(1)
                    .definitionJson(v1Json)
                    .operator("admin")
                    .changeDesc("V1")
                    .createdAt(LocalDateTime.now().minusDays(1))
                    .build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(v1));

            assertThatThrownBy(() -> service.rollback("R001", 1, "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("回滚被拒绝");
        }
    }

    // ==================== 一键退役 ====================

    @Nested
    @DisplayName("一键退役")
    class RetireRuleTest {

        @Test
        @DisplayName("正常退役 — PUBLISHED → ARCHIVED + 禁用")
        void shouldRetirePublishedRule() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .name("待退役规则")
                    .status("PUBLISHED")
                    .enabled(true)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);

            RuleDefinition retired = RuleDefinition.builder()
                    .code("R001")
                    .name("待退役规则")
                    .status("ARCHIVED")
                    .enabled(false)
                    .build();
            when(ruleAdminService.save(any(RuleDefinition.class), anyString(), anyString()))
                    .thenReturn(retired);

            RuleDefinition result = service.retireRule("R001", "admin", "休眠规则");

            assertThat(result.getStatus()).isEqualTo("ARCHIVED");
            assertThat(result.isEnabled()).isFalse();
            Mockito.verify(ruleAdminService).save(any(RuleDefinition.class), eq("admin"), anyString());
        }

        @Test
        @DisplayName("重复退役 — 已归档规则抛异常")
        void shouldRejectRetireArchivedRule() {
            RuleDefinition rule = RuleDefinition.builder()
                    .code("R001")
                    .status("ARCHIVED")
                    .enabled(false)
                    .build();
            when(configProvider.findByCode("R001")).thenReturn(rule);

            assertThatThrownBy(() -> service.retireRule("R001", "admin", "测试"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("已归档");
        }

        @Test
        @DisplayName("规则不存在 — 抛异常")
        void shouldRejectRetireNonExistentRule() {
            when(configProvider.findByCode("R999")).thenReturn(null);

            assertThatThrownBy(() -> service.retireRule("R999", "admin", "测试"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("规则不存在");
        }
    }

    // ==================== 批量退役 ====================

    @Nested
    @DisplayName("批量退役")
    class BulkRetireTest {

        @Test
        @DisplayName("批量退役 — 部分成功部分失败")
        void shouldHandlePartialSuccess() {
            RuleDefinition r1 = RuleDefinition.builder()
                    .code("R001").name("规则1").status("PUBLISHED").enabled(true).build();
            RuleDefinition r2 = RuleDefinition.builder()
                    .code("R002").name("规则2").status("ARCHIVED").enabled(false).build();
            when(configProvider.findByCode("R001")).thenReturn(r1);
            when(configProvider.findByCode("R002")).thenReturn(r2);
            when(ruleAdminService.save(any(), anyString(), anyString()))
                    .thenReturn(r1);

            Map<String, String> results = service.bulkRetire(
                    List.of("R001", "R002"), "admin", "批量退役");

            assertThat(results).hasSize(2);
            assertThat(results.get("R001")).isEqualTo("SUCCESS");
            assertThat(results.get("R002")).startsWith("FAILED");
        }

        @Test
        @DisplayName("空列表 — 返回空结果")
        void shouldHandleEmptyList() {
            Map<String, String> results = service.bulkRetire(List.of(), "admin", "测试");
            assertThat(results).isEmpty();
        }
    }

    // ==================== 生命周期概览 ====================

    @Nested
    @DisplayName("生命周期概览")
    class LifecycleSummaryTest {

        @Test
        @DisplayName("按状态统计规则数量")
        void shouldGenerateSummary() {
            List<RuleDefinition> rules = new ArrayList<>();
            rules.add(RuleDefinition.builder().code("R001").status("PUBLISHED").build());
            rules.add(RuleDefinition.builder().code("R002").status("PUBLISHED").build());
            rules.add(RuleDefinition.builder().code("R003").status("DISABLED").build());
            rules.add(RuleDefinition.builder().code("R004").status("ARCHIVED").build());
            rules.add(RuleDefinition.builder().code("R005").status("DRAFT").build());
            when(configProvider.loadAllRules()).thenReturn(rules);

            Map<String, Integer> summary = service.getLifecycleSummary();

            assertThat(summary.get("PUBLISHED")).isEqualTo(2);
            assertThat(summary.get("DISABLED")).isEqualTo(1);
            assertThat(summary.get("ARCHIVED")).isEqualTo(1);
            assertThat(summary.get("DRAFT")).isEqualTo(1);
        }

        @Test
        @DisplayName("空规则列表 — 所有状态计数为 0")
        void shouldHandleEmptySummary() {
            when(configProvider.loadAllRules()).thenReturn(List.of());

            Map<String, Integer> summary = service.getLifecycleSummary();

            for (RuleStatus status : RuleStatus.values()) {
                assertThat(summary.get(status.name())).isEqualTo(0);
            }
        }
    }

    // ==================== 配置 ====================

    @Nested
    @DisplayName("配置参数")
    class ConfigurationTest {

        @Test
        @DisplayName("自定义配置 — 休眠阈值降低后检测到更多规则")
        void shouldRespectCustomConfig() {
            LiteRuleProperties.LifecycleConfig config = new LiteRuleProperties.LifecycleConfig();
            config.setDormantMinEvaluations(100);
            config.setMinSampleSize(50);
            service.configure(config);

            setupRule("R001", "规则", "PUBLISHED", true);
            // 仅 200 次评估零触发，低于默认 1000 但高于自定义 100
            setupStats("R001", 200, 0, 0);

            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();

            assertThat(suggestions).hasSize(1);
            assertThat(suggestions.get(0).getReason()).isEqualTo(RetirementSuggestion.Reason.DORMANT);
        }

        @Test
        @DisplayName("null 配置 — 不影响默认参数")
        void shouldHandleNullConfig() {
            service.configure(null);

            setupRule("R001", "规则", "PUBLISHED", true);
            setupStats("R001", 200, 0, 0);

            // 200 次评估低于默认最小样本量 500，不应检测到
            List<RetirementSuggestion> suggestions = service.detectRetirementCandidates();
            assertThat(suggestions).isEmpty();
        }
    }

    // ==================== 辅助方法 ====================

    private void setupRule(String code, String name, String status, boolean enabled) {
        RuleDefinition rule = RuleDefinition.builder()
                .code(code)
                .name(name)
                .status(status)
                .enabled(enabled)
                .version(1)
                .build();
        List<RuleDefinition> existing = new ArrayList<>();
        if (configProvider.loadAllRules() != null) {
            existing.addAll(configProvider.loadAllRules());
        }
        existing.add(rule);
        when(configProvider.loadAllRules()).thenReturn(existing);
        when(configProvider.findByCode(code)).thenReturn(rule);
    }

    private java.util.Map<String, RuleEngineStats.RuleStat> statMap = new java.util.concurrent.ConcurrentHashMap<>();

    private void setupStats(String ruleCode, long executions, long triggered, long errors) {
        RuleEngineStats.RuleStat stat = RuleEngineStats.RuleStat.builder()
                .executions(executions)
                .triggered(triggered)
                .errors(errors)
                .totalElapsedMs(executions * 2)
                .build();
        statMap.put(ruleCode, stat);

        long totalExec = statMap.values().stream().mapToLong((RuleEngineStats.RuleStat s) -> s.getExecutions()).sum();
        long totalTrig = statMap.values().stream().mapToLong((RuleEngineStats.RuleStat s) -> s.getTriggered()).sum();
        long totalErr = statMap.values().stream().mapToLong((RuleEngineStats.RuleStat s) -> s.getErrors()).sum();

        RuleEngineStats stats = RuleEngineStats.builder()
                .perRuleStats(new java.util.concurrent.ConcurrentHashMap<>(statMap))
                .totalEvaluations(totalExec)
                .totalTriggered(totalTrig)
                .totalErrors(totalErr)
                .build();
        when(ruleEngine.getStats()).thenReturn(stats);
    }

    private static String eq(String value) {
        return org.mockito.ArgumentMatchers.eq(value);
    }
}
