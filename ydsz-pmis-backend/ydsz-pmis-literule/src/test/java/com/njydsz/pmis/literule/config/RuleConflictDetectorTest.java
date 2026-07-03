package com.njydsz.pmis.literule.config;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import com.njydsz.pmis.literule.spi.RuleConfigProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 规则冲突检测器单元测试
 *
 * <p>覆盖 RuleConflictDetector.detect() 的核心场景：
 * <ul>
 *   <li>条件相同 + 严重度相同 → IDENTICAL_CONDITION (WARN)</li>
 *   <li>条件相同 + 严重度不同 → CONTRADICTORY_SEVERITY (ERROR)</li>
 *   <li>同 category + name 相同 + 条件不同 → NAME_COLLISION (WARN)</li>
 *   <li>更新场景：自身跳过</li>
 *   <li>跨租户不检测</li>
 *   <li>无冲突返回空列表</li>
 *   <li>条件表达式空白归一化（"a > 1" 等价于 "a>1"）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class RuleConflictDetectorTest {

    private FakeRuleConfigProvider configProvider;
    private RuleConflictDetector detector;

    @BeforeEach
    void setUp() {
        configProvider = new FakeRuleConfigProvider();
        detector = new RuleConflictDetector(configProvider);
    }

    // ---------- 场景 1：条件相同 + 严重度相同 → IDENTICAL_CONDITION (WARN) ----------

    @Test
    void identicalConditionSameSeverityShouldProduceIdenticalConditionWarn() {
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_01")
                .name("规则A")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_01")
                .name("规则A副本")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertEquals(1, conflicts.size());
        RuleConflict c = conflicts.get(0);
        assertEquals(RuleConflict.Type.IDENTICAL_CONDITION, c.getType());
        assertEquals(RuleConflict.Level.WARN, c.getLevel());
        assertEquals("R_NEW_01", c.getNewRuleCode());
        assertEquals("R_EXIST_01", c.getConflictingRuleCode());
        assertTrue(c.getDescription().contains("R_EXIST_01"));
    }

    // ---------- 场景 2：条件相同 + 严重度不同 → CONTRADICTORY_SEVERITY (ERROR) ----------

    @Test
    void identicalConditionDifferentSeverityShouldProduceContradictorySeverityError() {
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_02")
                .name("规则B")
                .category("RISK")
                .conditionExpression("grossMargin < 0.05 && confirmedRevenue > 0")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_02")
                .name("规则B冲突版")
                .category("RISK")
                .conditionExpression("grossMargin < 0.05 && confirmedRevenue > 0")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertEquals(1, conflicts.size());
        RuleConflict c = conflicts.get(0);
        assertEquals(RuleConflict.Type.CONTRADICTORY_SEVERITY, c.getType());
        assertEquals(RuleConflict.Level.ERROR, c.getLevel());
        assertTrue(c.getDescription().contains("严重度不同"));
    }

    // ---------- 场景 3：severityExpression vs defaultSeverity 也应识别为不同严重度 ----------

    @Test
    void severityExpressionVsDefaultSeverityShouldAlsoConflict() {
        // 已有规则使用 defaultSeverity=RED
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_03")
                .name("规则C")
                .category("RISK")
                .conditionExpression("amount > 1000")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        // 新规则使用 severityExpression（动态决定）
        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_03")
                .name("规则C动态版")
                .category("RISK")
                .conditionExpression("amount > 1000")
                .severityExpression("amount > 5000 ? 'RED' : 'YELLOW'")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertEquals(1, conflicts.size());
        assertEquals(RuleConflict.Type.CONTRADICTORY_SEVERITY, conflicts.get(0).getType());
        assertEquals(RuleConflict.Level.ERROR, conflicts.get(0).getLevel());
    }

    // ---------- 场景 4：同 category + name 相同 + 条件不同 → NAME_COLLISION (WARN) ----------

    @Test
    void sameCategoryAndNameDifferentConditionShouldProduceNameCollisionWarn() {
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_04")
                .name("同名规则")
                .category("FINANCE")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build());

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_04")
                .name("同名规则")
                .category("FINANCE")
                .conditionExpression("evmRedCount >= 5")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertEquals(1, conflicts.size());
        RuleConflict c = conflicts.get(0);
        assertEquals(RuleConflict.Type.NAME_COLLISION, c.getType());
        assertEquals(RuleConflict.Level.WARN, c.getLevel());
        assertTrue(c.getDescription().contains("同名规则"));
        assertTrue(c.getDescription().contains("FINANCE"));
    }

    // ---------- 场景 5：不同 category + name 相同 → 不应触发 NAME_COLLISION ----------

    @Test
    void differentCategorySameNameShouldNotCollide() {
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_05")
                .name("同名规则")
                .category("FINANCE")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build());

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_05")
                .name("同名规则")
                .category("OPERATION")  // 不同 category
                .conditionExpression("evmRedCount >= 5")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertTrue(conflicts.isEmpty(), "不同 category 下的同名规则不应触发 NAME_COLLISION");
    }

    // ---------- 场景 6：更新场景：自身跳过 ----------

    @Test
    void updateSelfShouldNotConflictWithItself() {
        seedExisting(RuleDefinition.builder()
                .code("R_SELF")
                .name("自身更新规则")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        // 同 code 的新版本
        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_SELF")
                .name("自身更新规则")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertTrue(conflicts.isEmpty(), "更新自身规则时不应与自己冲突");
    }

    // ---------- 场景 7：跨租户不检测 ----------

    @Test
    void crossTenantShouldNotConflict() {
        // 租户 1 已有规则
        seedExisting(RuleDefinition.builder()
                .code("R_TENANT1")
                .name("租户1规则")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        // 租户 2 新规则，与租户 1 完全相同
        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_TENANT2")
                .name("租户2规则")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(2L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertTrue(conflicts.isEmpty(), "跨租户不应检测冲突");
    }

    // ---------- 场景 8：无冲突返回空列表 ----------

    @Test
    void noConflictShouldReturnEmptyList() {
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_08")
                .name("不冲突规则")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_08")
                .name("全新规则")
                .category("FINANCE")
                .conditionExpression("amount > 5000")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertNotNull(conflicts);
        assertTrue(conflicts.isEmpty());
    }

    // ---------- 场景 9：条件表达式空白归一化（"a > 1" 等价于 "a>1"） ----------

    @Test
    void conditionWithDifferentWhitespaceShouldBeIdentical() {
        // 已有规则：紧凑写法 "a>1"
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_09")
                .name("紧凑写法")
                .category("RISK")
                .conditionExpression("evmRedCount>=3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        // 新规则：带空格写法 "evmRedCount >= 3"
        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_09")
                .name("带空格写法")
                .category("RISK")
                .conditionExpression("evmRedCount >= 3")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        assertEquals(1, conflicts.size());
        assertEquals(RuleConflict.Type.IDENTICAL_CONDITION, conflicts.get(0).getType(),
                "空白差异应被 normalize 视为相同条件");
    }

    // ---------- 场景 10：空 conditionExpression 不应误判为相同 ----------

    @Test
    void emptyConditionShouldNotBeIdentical() {
        seedExisting(RuleDefinition.builder()
                .code("R_EXIST_10")
                .name("空条件规则")
                .category("RISK")
                .conditionExpression(null)
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build());

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_NEW_10")
                .name("另一空条件规则")
                .category("RISK")
                .conditionExpression("")
                .defaultSeverity(RuleSeverity.RED)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = detector.detect(newDef);

        // 空条件不应触发 IDENTICAL_CONDITION（避免无关规则相互误报）
        assertTrue(conflicts.stream().noneMatch(c -> c.getType() == RuleConflict.Type.IDENTICAL_CONDITION),
                "空条件不应触发 IDENTICAL_CONDITION");
        assertTrue(conflicts.stream().noneMatch(c -> c.getType() == RuleConflict.Type.CONTRADICTORY_SEVERITY),
                "空条件不应触发 CONTRADICTORY_SEVERITY");
    }

    // ---------- 场景 11：configProvider 抛异常时返回空列表（降级容错） ----------

    @Test
    void configProviderThrowsShouldReturnEmptyList() {
        FakeRuleConfigProvider failing = new FakeRuleConfigProvider();
        failing.throwOnLoad = true;
        RuleConflictDetector d = new RuleConflictDetector(failing);

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R_FAIL")
                .name("容错测试")
                .category("RISK")
                .conditionExpression("1 > 0")
                .defaultSeverity(RuleSeverity.YELLOW)
                .tenantId(1L)
                .build();

        List<RuleConflict> conflicts = d.detect(newDef);

        assertNotNull(conflicts);
        assertTrue(conflicts.isEmpty(), "configProvider 异常时应降级返回空列表");
    }

    // ---------- 辅助方法 ----------

    private void seedExisting(RuleDefinition def) {
        configProvider.store.put(def.getCode(), def);
    }

    /**
     * 内存版 RuleConfigProvider
     */
    static class FakeRuleConfigProvider implements RuleConfigProvider {
        final Map<String, RuleDefinition> store = new ConcurrentHashMap<>();
        volatile boolean throwOnLoad = false;

        @Override
        public List<RuleDefinition> loadEnabledRules() {
            if (throwOnLoad) {
                throw new RuntimeException("模拟加载失败");
            }
            return new ArrayList<>(store.values());
        }

        @Override
        public List<RuleDefinition> loadAllRules() {
            if (throwOnLoad) {
                throw new RuntimeException("模拟加载失败");
            }
            return new ArrayList<>(store.values());
        }

        @Override
        public RuleDefinition save(RuleDefinition definition, String operator) {
            store.put(definition.getCode(), definition);
            return definition;
        }

        @Override
        public void toggleEnabled(String ruleCode, boolean enabled, String operator) {
            // no-op
        }

        @Override
        public RuleDefinition findByCode(String ruleCode) {
            return store.get(ruleCode);
        }
    }
}
