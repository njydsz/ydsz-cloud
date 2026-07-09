package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionTraceNode;
import com.njydsz.pmis.literule.spi.RuleConfigBroadcaster;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link RuleAdminService} 单元测试。
 *
 * <p>覆盖规则 CRUD、搜索、启停、版本管理、dry-run、表达式评估、冲突检测等能力。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("规则管理服务测试")
@ExtendWith(MockitoExtension.class)
class RuleAdminServiceTest {

    @Mock
    private RuleEngine ruleEngine;

    @Mock
    private ExpressionEvaluator evaluator;

    @Mock
    private RuleConfigProvider configProvider;

    @Mock
    private RuleVersionRepository versionRepository;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @Mock
    private RuleConfigBroadcaster broadcaster;

    @Mock
    private RuleConflictDetector conflictDetector;

    @InjectMocks
    private RuleAdminService adminService;

    private RuleDefinition buildRule(String code, String name, String status, int version) {
        return RuleDefinition.builder()
                .code(code)
                .name(name)
                .conditionExpression("amount > 1000")
                .status(status)
                .version(version)
                .enabled(true)
                .build();
    }

    @BeforeEach
    void setUp() {
        adminService.setConflictDetector(conflictDetector);
    }

    @Nested
    @DisplayName("查询：listAll / getByCode")
    class QueryTest {

        @Test
        @DisplayName("正常场景：listAll 委托 configProvider")
        void shouldListAll() {
            List<RuleDefinition> rules = List.of(buildRule("R001", "规则1", "PUBLISHED", 1));
            when(configProvider.loadAllRules()).thenReturn(rules);

            List<RuleDefinition> result = adminService.listAll();

            assertThat(result).isSameAs(rules);
        }

        @Test
        @DisplayName("正常场景：getByCode 委托 configProvider")
        void shouldGetByCode() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(configProvider.findByCode("R001")).thenReturn(rule);

            RuleDefinition result = adminService.getByCode("R001");

            assertThat(result).isSameAs(rule);
        }
    }

    @Nested
    @DisplayName("搜索：search / searchCount")
    class SearchTest {

        @Test
        @DisplayName("正常场景：按关键词搜索")
        void shouldSearchByKeyword() {
            RuleDefinition r1 = buildRule("R001", "金额超限", "PUBLISHED", 1);
            r1.setDescription("金额规则");
            RuleDefinition r2 = buildRule("R002", "成本超支", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));

            List<RuleDefinition> result = adminService.search("金额", null, null, null, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：按状态过滤")
        void shouldFilterByStatus() {
            RuleDefinition r1 = buildRule("R001", "规则1", "PUBLISHED", 1);
            RuleDefinition r2 = buildRule("R002", "规则2", "DRAFT", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));

            List<RuleDefinition> result = adminService.search(null, "PUBLISHED", null, null, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：按分类过滤")
        void shouldFilterByCategory() {
            RuleDefinition r1 = buildRule("R001", "规则1", "PUBLISHED", 1);
            r1.setCategory("finance");
            RuleDefinition r2 = buildRule("R002", "规则2", "PUBLISHED", 1);
            r2.setCategory("hr");
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));

            List<RuleDefinition> result = adminService.search(null, null, "finance", null, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：按启停状态过滤")
        void shouldFilterByEnabled() {
            RuleDefinition r1 = buildRule("R001", "规则1", "PUBLISHED", 1);
            r1.setEnabled(true);
            RuleDefinition r2 = buildRule("R002", "规则2", "PUBLISHED", 1);
            r2.setEnabled(false);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));

            List<RuleDefinition> result = adminService.search(null, null, null, false, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("R002");
        }

        @Test
        @DisplayName("正常场景：多关键词 AND 匹配")
        void shouldMatchMultipleKeywords() {
            RuleDefinition r1 = buildRule("R001", "金额超限规则", "PUBLISHED", 1);
            r1.setDescription("财务金额监控");
            RuleDefinition r2 = buildRule("R002", "金额规则", "PUBLISHED", 1);
            r2.setDescription("成本监控");
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2));

            List<RuleDefinition> result = adminService.search("金额 财务", null, null, null, 0, 10);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：分页返回")
        void shouldPaginate() {
            RuleDefinition r1 = buildRule("R001", "规则A", "PUBLISHED", 1);
            RuleDefinition r2 = buildRule("R002", "规则B", "PUBLISHED", 1);
            RuleDefinition r3 = buildRule("R003", "规则C", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2, r3));

            List<RuleDefinition> result = adminService.search(null, null, null, null, 1, 1);

            assertThat(result).hasSize(1);
            assertThat(result.get(0).getCode()).isEqualTo("R002");
        }

        @Test
        @DisplayName("边界场景：offset 超出范围返回空列表")
        void shouldReturnEmptyWhenOffsetExceeds() {
            when(configProvider.loadAllRules()).thenReturn(List.of(buildRule("R001", "规则1", "PUBLISHED", 1)));

            List<RuleDefinition> result = adminService.search(null, null, null, null, 100, 10);

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：searchCount 返回总数")
        void shouldReturnSearchCount() {
            RuleDefinition r1 = buildRule("R001", "金额", "PUBLISHED", 1);
            RuleDefinition r2 = buildRule("R002", "金额", "PUBLISHED", 1);
            RuleDefinition r3 = buildRule("R003", "其他", "PUBLISHED", 1);
            when(configProvider.loadAllRules()).thenReturn(List.of(r1, r2, r3));

            int count = adminService.searchCount("金额", null, null, null);

            assertThat(count).isEqualTo(2);
        }
    }

    @Nested
    @DisplayName("保存：save")
    class SaveTest {

        @Test
        @DisplayName("异常场景：条件表达式非法抛 IllegalArgumentException")
        void shouldThrowWhenConditionInvalid() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(false);

            assertThatThrownBy(() -> adminService.save(rule, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("条件表达式语法错误");
        }

        @Test
        @DisplayName("异常场景：严重度表达式非法抛 IllegalArgumentException")
        void shouldThrowWhenSeverityExpressionInvalid() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            rule.setSeverityExpression("invalid >> expr");
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(evaluator.validate("invalid >> expr")).thenReturn(false);

            assertThatThrownBy(() -> adminService.save(rule, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("严重度表达式语法错误");
        }

        @Test
        @DisplayName("异常场景：非法状态值抛 IllegalArgumentException")
        void shouldThrowWhenStatusInvalid() {
            RuleDefinition rule = buildRule("R001", "规则1", "INVALID_STATUS", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);

            assertThatThrownBy(() -> adminService.save(rule, "admin", "新增"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法的规则状态");
        }

        @Test
        @DisplayName("异常场景：新建规则初始状态非 DRAFT/PUBLISHED 抛 IllegalStateException")
        void shouldThrowWhenNewRuleInitialStatusInvalid() {
            RuleDefinition rule = buildRule("R001", "规则1", "ARCHIVED", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);

            assertThatThrownBy(() -> adminService.save(rule, "admin", "新增"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("初始状态");
        }

        @Test
        @DisplayName("异常场景：非法状态转换抛 IllegalStateException")
        void shouldThrowWhenStatusTransitionInvalid() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 2);
            RuleDefinition existing = buildRule("R001", "规则1", "ARCHIVED", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(existing);

            assertThatThrownBy(() -> adminService.save(rule, "admin", "更新"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("不允许的状态转换");
        }

        @Test
        @DisplayName("正常场景：新建规则保存成功并发布 CREATE 事件")
        void shouldSaveNewRule() {
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            when(configProvider.save(rule, "admin")).thenReturn(rule);
            when(conflictDetector.detect(rule)).thenReturn(List.of());

            RuleDefinition result = adminService.save(rule, "admin", "新增规则");

            assertThat(result).isNotNull();
            verify(versionRepository).saveVersion(rule, "admin", "新增规则");
            ArgumentCaptor<RuleConfigRefreshEvent> captor = ArgumentCaptor.forClass(RuleConfigRefreshEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getChangeType()).isEqualTo(RuleConfigRefreshEvent.ChangeType.CREATE);
            assertThat(captor.getValue().getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("正常场景：更新规则保存成功并发布 UPDATE 事件")
        void shouldSaveUpdatedRule() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 2);
            RuleDefinition existing = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(existing);
            when(configProvider.save(rule, "admin")).thenReturn(rule);
            when(conflictDetector.detect(rule)).thenReturn(List.of());

            adminService.save(rule, "admin", "更新规则");

            ArgumentCaptor<RuleConfigRefreshEvent> captor = ArgumentCaptor.forClass(RuleConfigRefreshEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getChangeType()).isEqualTo(RuleConfigRefreshEvent.ChangeType.UPDATE);
        }

        @Test
        @DisplayName("正常场景：versionRepository 为 null 时不保存版本快照")
        void shouldNotSaveVersionWhenRepoNull() {
            RuleAdminService service = new RuleAdminService(
                    ruleEngine, evaluator, configProvider, null, eventPublisher);
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            when(configProvider.save(rule, "admin")).thenReturn(rule);

            service.save(rule, "admin", "新增");

            verify(versionRepository, never()).saveVersion(any(), anyString(), anyString());
        }

        @Test
        @DisplayName("正常场景：版本快照保存失败时不影响主流程")
        void shouldContinueWhenVersionSnapshotFails() {
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            when(configProvider.save(rule, "admin")).thenReturn(rule);
            when(conflictDetector.detect(rule)).thenReturn(List.of());
            doThrow(new RuntimeException("DB 异常"))
                    .when(versionRepository).saveVersion(any(), anyString(), anyString());

            RuleDefinition result = adminService.save(rule, "admin", "新增");

            assertThat(result).isNotNull();
            verify(eventPublisher).publishEvent(any(RuleConfigRefreshEvent.class));
        }

        @Test
        @DisplayName("正常场景：配置广播器时同时广播事件")
        void shouldBroadcastWhenConfigured() {
            adminService.setBroadcaster(broadcaster);
            when(broadcaster.isAvailable()).thenReturn(true);
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            when(configProvider.save(rule, "admin")).thenReturn(rule);
            when(conflictDetector.detect(rule)).thenReturn(List.of());

            adminService.save(rule, "admin", "新增");

            verify(broadcaster).broadcast(any(RuleConfigRefreshEvent.class), anyString());
        }

        @Test
        @DisplayName("正常场景：广播失败不影响本地刷新")
        void shouldContinueWhenBroadcastFails() {
            adminService.setBroadcaster(broadcaster);
            when(broadcaster.isAvailable()).thenReturn(true);
            doThrow(new RuntimeException("广播失败"))
                    .when(broadcaster).broadcast(any(), anyString());
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            when(configProvider.save(rule, "admin")).thenReturn(rule);
            when(conflictDetector.detect(rule)).thenReturn(List.of());

            RuleDefinition result = adminService.save(rule, "admin", "新增");

            assertThat(result).isNotNull();
            verify(eventPublisher).publishEvent(any(RuleConfigRefreshEvent.class));
        }

        @Test
        @DisplayName("异常场景：ERROR 级冲突阻塞保存")
        void shouldBlockSaveOnErrorConflict() {
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            RuleConflict errorConflict = RuleConflict.builder()
                    .type(RuleConflict.Type.CONTRADICTORY_SEVERITY)
                    .level(RuleConflict.Level.ERROR)
                    .newRuleCode("R001")
                    .conflictingRuleCode("R002")
                    .description("严重度冲突")
                    .build();
            when(conflictDetector.detect(rule)).thenReturn(List.of(errorConflict));

            assertThatThrownBy(() -> adminService.save(rule, "admin", "新增"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("冲突检测未通过");
        }

        @Test
        @DisplayName("正常场景：WARN 级冲突不阻塞保存")
        void shouldNotBlockOnWarnConflict() {
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            RuleConflict warnConflict = RuleConflict.builder()
                    .type(RuleConflict.Type.IDENTICAL_CONDITION)
                    .level(RuleConflict.Level.WARN)
                    .newRuleCode("R001")
                    .conflictingRuleCode("R002")
                    .description("重复定义")
                    .build();
            when(conflictDetector.detect(rule)).thenReturn(List.of(warnConflict));
            when(configProvider.save(rule, "admin")).thenReturn(rule);

            RuleDefinition result = adminService.save(rule, "admin", "新增");

            assertThat(result).isNotNull();
        }

        @Test
        @DisplayName("正常场景：关闭冲突检测时不调用检测器")
        void shouldSkipConflictDetectionWhenDisabled() {
            adminService.setConflictDetectionEnabled(false);
            RuleDefinition rule = buildRule("R001", "规则1", "DRAFT", 1);
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(configProvider.findByCode("R001")).thenReturn(null);
            when(configProvider.save(rule, "admin")).thenReturn(rule);

            adminService.save(rule, "admin", "新增");

            verify(conflictDetector, never()).detect(any());
        }
    }

    @Nested
    @DisplayName("启停切换：toggle")
    class ToggleTest {

        @Test
        @DisplayName("正常场景：切换启停并发布事件")
        void shouldToggleAndPublishEvent() {
            adminService.toggle("R001", false, "admin");

            verify(configProvider).toggleEnabled("R001", false, "admin");
            ArgumentCaptor<RuleConfigRefreshEvent> captor = ArgumentCaptor.forClass(RuleConfigRefreshEvent.class);
            verify(eventPublisher).publishEvent(captor.capture());
            assertThat(captor.getValue().getChangeType()).isEqualTo(RuleConfigRefreshEvent.ChangeType.TOGGLE);
        }
    }

    @Nested
    @DisplayName("更新责任人：updateOwner")
    class UpdateOwnerTest {

        @Test
        @DisplayName("异常场景：ruleCode 为空抛 IllegalArgumentException")
        void shouldThrowWhenRuleCodeBlank() {
            assertThatThrownBy(() -> adminService.updateOwner("  ", "owner1", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("ruleCode");
        }

        @Test
        @DisplayName("异常场景：规则不存在抛 IllegalArgumentException")
        void shouldThrowWhenRuleNotFound() {
            when(configProvider.findByCode("R001")).thenReturn(null);

            assertThatThrownBy(() -> adminService.updateOwner("R001", "owner1", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("规则不存在");
        }

        @Test
        @DisplayName("正常场景：更新责任人")
        void shouldUpdateOwner() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(configProvider.findByCode("R001")).thenReturn(rule);

            adminService.updateOwner("R001", "newOwner", "admin");

            assertThat(rule.getOwner()).isEqualTo("newOwner");
            verify(configProvider).save(rule, "admin");
        }
    }

    @Nested
    @DisplayName("更新分类路径：updateCategoryPath")
    class UpdateCategoryPathTest {

        @Test
        @DisplayName("异常场景：ruleCode 为空抛 IllegalArgumentException")
        void shouldThrowWhenRuleCodeBlank() {
            assertThatThrownBy(() -> adminService.updateCategoryPath("  ", "finance", "admin"))
                    .isInstanceOf(IllegalArgumentException.class);
        }

        @Test
        @DisplayName("异常场景：分类路径为空抛 IllegalArgumentException")
        void shouldThrowWhenPathBlank() {
            assertThatThrownBy(() -> adminService.updateCategoryPath("R001", "  ", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("分类路径不能为空");
        }

        @Test
        @DisplayName("异常场景：分类路径以 / 开头抛异常")
        void shouldThrowWhenPathStartsWithSlash() {
            assertThatThrownBy(() -> adminService.updateCategoryPath("R001", "/finance", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("不能以 / 开头或结尾");
        }

        @Test
        @DisplayName("异常场景：分类路径包含连续 / 抛异常")
        void shouldThrowWhenPathContainsDoubleSlash() {
            assertThatThrownBy(() -> adminService.updateCategoryPath("R001", "finance//credit", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("连续 /");
        }

        @Test
        @DisplayName("异常场景：分类路径深度超过 5 级抛异常")
        void shouldThrowWhenPathTooDeep() {
            assertThatThrownBy(() -> adminService.updateCategoryPath("R001", "a/b/c/d/e/f", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("深度不能超过 5 级");
        }

        @Test
        @DisplayName("异常场景：分类路径段含非法字符抛异常")
        void shouldThrowWhenPathSegmentInvalid() {
            assertThatThrownBy(() -> adminService.updateCategoryPath("R001", "finance/credit@x", "admin"))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("非法字符");
        }

        @Test
        @DisplayName("正常场景：更新分类路径并同步一级分类")
        void shouldUpdateCategoryPath() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(configProvider.findByCode("R001")).thenReturn(rule);

            adminService.updateCategoryPath("R001", "finance/credit/loan", "admin");

            assertThat(rule.getCategoryPath()).isEqualTo("finance/credit/loan");
            assertThat(rule.getCategory()).isEqualTo("finance");
            verify(configProvider).save(rule, "admin");
        }

        @Test
        @DisplayName("正常场景：单级分类路径")
        void shouldUpdateSingleLevelPath() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(configProvider.findByCode("R001")).thenReturn(rule);

            adminService.updateCategoryPath("R001", "finance", "admin");

            assertThat(rule.getCategoryPath()).isEqualTo("finance");
            assertThat(rule.getCategory()).isEqualTo("finance");
        }
    }

    @Nested
    @DisplayName("版本管理：listVersions / rollback")
    class VersionTest {

        @Test
        @DisplayName("正常场景：listVersions 委托 versionRepository")
        void shouldListVersions() {
            RuleVersion v1 = RuleVersion.builder().ruleCode("R001").version(1).build();
            when(versionRepository.listVersions("R001")).thenReturn(List.of(v1));

            List<RuleVersion> result = adminService.listVersions("R001");

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("边界场景：versionRepository 为 null 时返回空列表")
        void shouldReturnEmptyWhenRepoNull() {
            RuleAdminService service = new RuleAdminService(
                    ruleEngine, evaluator, configProvider, null, eventPublisher);

            List<RuleVersion> result = service.listVersions("R001");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("异常场景：versionRepository 为 null 时回滚抛 IllegalStateException")
        void shouldThrowWhenRollbackWithoutRepo() {
            RuleAdminService service = new RuleAdminService(
                    ruleEngine, evaluator, configProvider, null, eventPublisher);

            assertThatThrownBy(() -> service.rollback("R001", 1, "admin"))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("版本仓库未配置");
        }

        @Test
        @DisplayName("正常场景：回滚并发布事件")
        void shouldRollback() {
            RuleDefinition restored = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(versionRepository.rollback("R001", 1, "admin")).thenReturn(restored);

            RuleDefinition result = adminService.rollback("R001", 1, "admin");

            assertThat(result).isNotNull();
            verify(eventPublisher).publishEvent(any(RuleConfigRefreshEvent.class));
        }
    }

    @Nested
    @DisplayName("Dry-run 仿真：dryRun")
    class DryRunTest {

        @Test
        @DisplayName("异常场景：dryRun 禁用时抛 IllegalStateException")
        void shouldThrowWhenDryRunDisabled() {
            adminService.setDryRunEnabled(false);

            assertThatThrownBy(() -> adminService.dryRun("R001", Map.of()))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("Dry-run");
        }

        @Test
        @DisplayName("正常场景：单规则仿真")
        void shouldDryRunSingleRule() {
            RuleDefinition rule = buildRule("R001", "规则1", "PUBLISHED", 1);
            when(configProvider.findByCode("R001")).thenReturn(rule);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(true);

            List<RuleResult> result = adminService.dryRun("R001", Map.of("amount", 2000));

            assertThat(result).hasSize(1);
        }

        @Test
        @DisplayName("边界场景：规则不存在返回空列表")
        void shouldReturnEmptyWhenRuleNotFound() {
            when(configProvider.findByCode("R_NOT_EXIST")).thenReturn(null);

            List<RuleResult> result = adminService.dryRun("R_NOT_EXIST", Map.of());

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("正常场景：全部规则仿真委托 ruleEngine")
        void shouldDryRunAllRules() {
            when(ruleEngine.dryRun(any())).thenReturn(List.of());

            List<RuleResult> result = adminService.dryRun(null, Map.of());

            assertThat(result).isEmpty();
            verify(ruleEngine).dryRun(any());
        }
    }

    @Nested
    @DisplayName("表达式评估：evaluateWithExpression")
    class EvaluateWithExpressionTest {

        @Test
        @DisplayName("边界场景：条件表达式非法返回未触发结果")
        void shouldReturnNotTriggeredWhenConditionInvalid() {
            when(evaluator.validate("invalid expr")).thenReturn(false);

            RuleResult result = adminService.evaluateWithExpression(
                    "R001", "invalid expr", null, RuleSeverity.YELLOW, Map.of());

            assertThat(result.isTriggered()).isFalse();
            assertThat(result.getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("边界场景：严重度表达式非法返回未触发结果")
        void shouldReturnNotTriggeredWhenSeverityInvalid() {
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(evaluator.validate("invalid severity")).thenReturn(false);

            RuleResult result = adminService.evaluateWithExpression(
                    "R001", "amount > 1000", "invalid severity", RuleSeverity.YELLOW, Map.of());

            assertThat(result.isTriggered()).isFalse();
        }

        @Test
        @DisplayName("正常场景：表达式合法时返回评估结果")
        void shouldEvaluateExpression() {
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(true);

            RuleResult result = adminService.evaluateWithExpression(
                    "R001", "amount > 1000", null, RuleSeverity.YELLOW, Map.of("amount", 2000));

            assertThat(result.getRuleCode()).isEqualTo("R001");
        }

        @Test
        @DisplayName("边界场景：facts 为 null 时不抛异常")
        void shouldHandleNullFacts() {
            when(evaluator.validate("amount > 1000")).thenReturn(true);
            when(evaluator.evalBoolean(eq("amount > 1000"), any())).thenReturn(false);

            RuleResult result = adminService.evaluateWithExpression(
                    "R001", "amount > 1000", null, RuleSeverity.YELLOW, null);

            assertThat(result).isNotNull();
        }
    }

    @Nested
    @DisplayName("表达式校验与追踪")
    class ExpressionTest {

        @Test
        @DisplayName("正常场景：validateExpression 委托 evaluator")
        void shouldValidateExpression() {
            when(evaluator.validate("a > b")).thenReturn(true);

            boolean result = adminService.validateExpression("a > b");

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("边界场景：空表达式追踪返回错误节点")
        void shouldReturnErrorNodeForBlankExpression() {
            ExpressionEvaluator.TraceResult result = adminService.traceExpression("", Map.of());

            assertThat(result.result()).isFalse();
            assertThat(result.traceTree().getError()).isEqualTo("表达式为空");
        }

        @Test
        @DisplayName("边界场景：null 表达式追踪返回错误节点")
        void shouldReturnErrorNodeForNullExpression() {
            ExpressionEvaluator.TraceResult result = adminService.traceExpression(null, Map.of());

            assertThat(result.result()).isFalse();
            assertThat(result.traceTree().getError()).isEqualTo("表达式为空");
        }

        @Test
        @DisplayName("正常场景：委托 evaluator 进行追踪求值")
        void shouldDelegateTraceExpression() {
            ExpressionTraceNode root = ExpressionTraceNode.builder()
                    .nodeType(ExpressionTraceNode.NodeType.ROOT)
                    .expression("a > b")
                    .result(true)
                    .build();
            ExpressionEvaluator.TraceResult traceResult = new ExpressionEvaluator.TraceResult(true, root);
            when(evaluator.evalBooleanWithTrace(eq("a > b"), any())).thenReturn(traceResult);

            ExpressionEvaluator.TraceResult result = adminService.traceExpression("a > b", Map.of("a", 5, "b", 3));

            assertThat(result.result()).isTrue();
            assertThat(result.traceTree().getExpression()).isEqualTo("a > b");
        }
    }
}
