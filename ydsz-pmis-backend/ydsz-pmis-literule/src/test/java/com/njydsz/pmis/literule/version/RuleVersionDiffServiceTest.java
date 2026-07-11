package com.njydsz.pmis.literule.version;

import com.njydsz.pmis.literule.api.RuleDefinition;
import com.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * RuleVersionDiffService 单元测试
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
@DisplayName("规则版本 Diff 服务测试")
class RuleVersionDiffServiceTest {

    private final RuleVersionDiffService diffService = new RuleVersionDiffService();

    @Test
    @DisplayName("无变更 - 所有字段相同")
    void testNoChanges() {
        RuleDefinition def = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(1)
                .build();

        RuleVersionDiff diff = diffService.diff(def, def);

        assertFalse(diff.hasChanges());
        assertEquals(0, diff.changeCount());
    }

    @Test
    @DisplayName("条件表达式变更")
    void testConditionExpressionChanged() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 200")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(2)
                .build();

        RuleVersionDiff diff = diffService.diff(oldDef, newDef);

        assertTrue(diff.hasChanges());
        assertEquals(1, diff.changeCount());

        RuleVersionDiff.DiffEntry condEntry = diff.getEntries().stream()
                .filter(e -> "conditionExpression".equals(e.getField()))
                .findFirst().orElse(null);
        assertNotNull(condEntry);
        assertEquals(RuleVersionDiff.DiffType.MODIFIED, condEntry.getType());
        assertEquals("amount > 100", condEntry.getOldValue());
        assertEquals("amount > 200", condEntry.getNewValue());
    }

    @Test
    @DisplayName("多字段变更")
    void testMultipleFieldChanges() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .code("R001")
                .name("旧名称")
                .conditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(50)
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R001")
                .name("新名称")
                .conditionExpression("amount > 500")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(2)
                .build();

        RuleVersionDiff diff = diffService.diff(oldDef, newDef);

        assertTrue(diff.hasChanges());
        assertEquals(4, diff.changeCount()); // name, condition, severity, priority

        assertEquals(1, diff.getOldVersion());
        assertEquals(2, diff.getNewVersion());
        assertEquals("R001", diff.getRuleCode());
        assertNotNull(diff.getSummary());
        assertTrue(diff.getSummary().contains("v1 → v2"));
    }

    @Test
    @DisplayName("新增字段（旧版本为 null）")
    void testAddedField() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 100")
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 100")
                .description("新增的描述")
                .version(2)
                .build();

        RuleVersionDiff diff = diffService.diff(oldDef, newDef);

        assertTrue(diff.hasChanges());
        RuleVersionDiff.DiffEntry descEntry = diff.getEntries().stream()
                .filter(e -> "description".equals(e.getField()))
                .findFirst().orElse(null);
        assertNotNull(descEntry);
        assertEquals(RuleVersionDiff.DiffType.ADDED, descEntry.getType());
        assertNull(descEntry.getOldValue());
        assertEquals("新增的描述", descEntry.getNewValue());
    }

    @Test
    @DisplayName("删除字段（新版本为 null）")
    void testRemovedField() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 100")
                .description("旧描述")
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .code("R001")
                .name("测试规则")
                .conditionExpression("amount > 100")
                .version(2)
                .build();

        RuleVersionDiff diff = diffService.diff(oldDef, newDef);

        assertTrue(diff.hasChanges());
        RuleVersionDiff.DiffEntry descEntry = diff.getEntries().stream()
                .filter(e -> "description".equals(e.getField()))
                .findFirst().orElse(null);
        assertNotNull(descEntry);
        assertEquals(RuleVersionDiff.DiffType.REMOVED, descEntry.getType());
        assertEquals("旧描述", descEntry.getOldValue());
        assertNull(descEntry.getNewValue());
    }

    @Test
    @DisplayName("旧版本为 null - 整条新增")
    void testOldNull() {
        RuleDefinition newDef = RuleDefinition.builder()
                .code("R001")
                .name("新规则")
                .version(1)
                .build();

        RuleVersionDiff diff = diffService.diff(null, newDef);

        assertTrue(diff.hasChanges());
        assertEquals(1, diff.getEntries().size());
        assertEquals(RuleVersionDiff.DiffType.ADDED, diff.getEntries().get(0).getType());
    }

    @Test
    @DisplayName("新版本为 null - 整条删除")
    void testNewNull() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .code("R001")
                .name("旧规则")
                .version(1)
                .build();

        RuleVersionDiff diff = diffService.diff(oldDef, null);

        assertTrue(diff.hasChanges());
        assertEquals(1, diff.getEntries().size());
        assertEquals(RuleVersionDiff.DiffType.REMOVED, diff.getEntries().get(0).getType());
    }
}
