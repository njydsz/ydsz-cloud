package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleEngine;
import com.njydsz.pmis.literule.api.RuleResult;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.expr.AviatorExpressionEvaluator;
import com.njydsz.pmis.literule.expr.ExpressionEvaluator;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import com.njydsz.pmis.literule.spi.RuleVersion;
import com.njydsz.pmis.literule.spi.RuleVersionRepository;
import com.njydsz.pmis.literule.event.RuleConfigRefreshEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * RuleAdminService 集成测试
 *
 * <p>验证规则 CRUD → 热加载 → dry-run → 版本管理全流程。
 *
 * @author ydsz-pmis-team
 */
@DisplayName("RuleAdminService 管理服务集成测试")
class RuleAdminServiceTest {

    private RuleEngine ruleEngine;
    private ExpressionEvaluator evaluator;
    private RuleConfigProvider configProvider;
    private RuleVersionRepository versionRepository;
    private ApplicationEventPublisher eventPublisher;
    private RuleAdminService adminService;
    private List<RuleConfigRefreshEvent> publishedEvents;

    @BeforeEach
    void setUp() {
        ruleEngine = new com.njydsz.pmis.literule.core.DefaultRuleEngine();
        evaluator = new AviatorExpressionEvaluator();
        configProvider = mock(RuleConfigProvider.class);
        versionRepository = mock(RuleVersionRepository.class);
        publishedEvents = new CopyOnWriteArrayList<>();
        eventPublisher = e -> publishedEvents.add((RuleConfigRefreshEvent) e);

        adminService = new RuleAdminService(ruleEngine, evaluator, configProvider,
                versionRepository, eventPublisher);
    }

    @Test
    @DisplayName("保存规则 → 发布热加载事件 → 引擎自动注册")
    void testSaveRulePublishesEvent() {
        RuleDefinition def = RuleDefinition.builder()
                .code("TEST_001")
                .name("测试规则")
                .category("TEST")
                .conditionExpression("value >= 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .titleTemplate("值 ${value} 超标")
                .build();

        when(configProvider.save(any(), any())).thenReturn(RuleDefinition.builder()
                .code("TEST_001").name("测试规则").category("TEST")
                .conditionExpression("value >= 100").defaultSeverity(RuleSeverity.YELLOW)
                .titleTemplate("值 ${value} 超标").version(1).build());

        RuleDefinition saved = adminService.save(def, "admin", "新增测试规则");

        assertNotNull(saved);
        assertEquals(1, publishedEvents.size());
        assertEquals(RuleConfigRefreshEvent.ChangeType.CREATE, publishedEvents.get(0).getChangeType());
        verify(versionRepository).saveVersion(any(), eq("admin"), eq("新增测试规则"));
    }

    @Test
    @DisplayName("表达式校验失败时拒绝保存")
    void testSaveRejectsInvalidExpression() {
        RuleDefinition def = RuleDefinition.builder()
                .code("BAD_001")
                .name("坏规则")
                .category("TEST")
                .conditionExpression("func(")  // 非法表达式
                .defaultSeverity(RuleSeverity.YELLOW)
                .build();

        assertThrows(IllegalArgumentException.class, () ->
                adminService.save(def, "admin", "测试"));
        verify(configProvider, never()).save(any(), any());
    }

    @Test
    @DisplayName("dry-run 仿真 - 全部规则")
    void testDryRunAllRules() {
        // 注册一条静态规则
        ruleEngine.register(new com.njydsz.pmis.literule.impl.StaticRule("R1", "规则1", "TEST", ctx ->
                RuleResult.triggered("R1", "规则1", "TEST", RuleSeverity.RED, "触发", "")));

        List<RuleResult> results = adminService.dryRun(null, Map.of("x", 1));
        assertEquals(1, results.size());
        assertTrue(results.get(0).isTriggered());
    }

    @Test
    @DisplayName("dry-run 仿真 - 单条 DB 规则")
    void testDryRunSingleDbRule() {
        RuleDefinition def = RuleDefinition.builder()
                .code("DB_001")
                .name("DB规则")
                .category("TEST")
                .conditionExpression("cost >= 500000")
                .severityExpression("cost >= 1000000 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .titleTemplate("成本 ${cost} 元")
                .build();

        when(configProvider.findByCode("DB_001")).thenReturn(def);

        // 600000 -> YELLOW
        List<RuleResult> results1 = adminService.dryRun("DB_001", Map.of("cost", 600000));
        assertEquals(1, results1.size());
        assertTrue(results1.get(0).isTriggered());
        assertEquals(RuleSeverity.YELLOW, results1.get(0).getSeverity());
        assertEquals("成本 600000 元", results1.get(0).getTitle());

        // 1200000 -> RED
        List<RuleResult> results2 = adminService.dryRun("DB_001", Map.of("cost", 1200000));
        assertEquals(RuleSeverity.RED, results2.get(0).getSeverity());
    }

    @Test
    @DisplayName("版本历史查询")
    void testListVersions() {
        List<RuleVersion> versions = List.of(
                RuleVersion.builder().ruleCode("R1").version(2).operator("admin").build(),
                RuleVersion.builder().ruleCode("R1").version(1).operator("SYSTEM").build());
        when(versionRepository.listVersions("R1")).thenReturn(versions);

        List<RuleVersion> result = adminService.listVersions("R1");
        assertEquals(2, result.size());
    }

    @Test
    @DisplayName("规则启停切换发布事件")
    void testTogglePublishesEvent() {
        adminService.toggle("R1", false, "admin");

        verify(configProvider).toggleEnabled("R1", false, "admin");
        assertEquals(1, publishedEvents.size());
        assertEquals(RuleConfigRefreshEvent.ChangeType.TOGGLE, publishedEvents.get(0).getChangeType());
    }

    @Test
    @DisplayName("回滚发布更新事件")
    void testRollbackPublishesEvent() {
        RuleDefinition restored = RuleDefinition.builder()
                .code("R1").name("规则").category("TEST")
                .conditionExpression("x > 0").defaultSeverity(RuleSeverity.YELLOW)
                .version(3).build();
        when(versionRepository.rollback("R1", 1, "admin")).thenReturn(restored);

        RuleDefinition result = adminService.rollback("R1", 1, "admin");

        assertNotNull(result);
        assertEquals(3, result.getVersion());
        assertEquals(1, publishedEvents.size());
        assertEquals(RuleConfigRefreshEvent.ChangeType.UPDATE, publishedEvents.get(0).getChangeType());
    }
}
