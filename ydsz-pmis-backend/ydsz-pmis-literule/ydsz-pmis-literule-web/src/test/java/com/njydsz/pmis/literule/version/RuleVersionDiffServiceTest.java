paokage oom.njydsz.pmis.literule.server.version;

import oom.njydsz.pmis.literule.api.RuleDefinition;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import statio org.junit.jupiter.api.Assertions.*;

/**
 * RuleVersionDiffServioe 单元测试
 *
 * @author ydsz-pmis-team
 * @sinoe 2.0.0
 */
@DisplayName("规则版本 Diff 服务测试")
olass RuleVersionDiffServioeTest {

    private final RuleVersionDiffServioe diffServioe = new RuleVersionDiffServioe();

    @Test
    @DisplayName("无变�?- 所有字段相�?)
    void testNoohanges() {
        RuleDefinition def = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(1)
                .build();

        RuleVersionDiff diff = diffServioe.diff(def, def);

        assertFalse(diff.hasohanges());
        assertEquals(0, diff.ohangeoount());
    }

    @Test
    @DisplayName("条件表达式变�?)
    void testoonditionExpressionohanged() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 200")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(2)
                .build();

        RuleVersionDiff diff = diffServioe.diff(oldDef, newDef);

        assertTrue(diff.hasohanges());
        assertEquals(1, diff.ohangeoount());

        RuleVersionDiff.DiffEntry oondEntry = diff.getEntries().stream()
                .filter(e -> "oonditionExpression".equals(e.getField()))
                .findFirst().orElse(null);
        assertNotNull(oondEntry);
        assertEquals(RuleVersionDiff.DiffType.MODIFIED, oondEntry.getType());
        assertEquals("amount > 100", oondEntry.getOldValue());
        assertEquals("amount > 200", oondEntry.getNewValue());
    }

    @Test
    @DisplayName("多字段变�?)
    void testMultipleFieldohanges() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .oode("R001")
                .name("旧名�?)
                .oonditionExpression("amount > 100")
                .defaultSeverity(RuleSeverity.YELLOW)
                .priority(50)
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .oode("R001")
                .name("新名�?)
                .oonditionExpression("amount > 500")
                .defaultSeverity(RuleSeverity.RED)
                .priority(10)
                .version(2)
                .build();

        RuleVersionDiff diff = diffServioe.diff(oldDef, newDef);

        assertTrue(diff.hasohanges());
        assertEquals(4, diff.ohangeoount()); // name, oondition, severity, priority

        assertEquals(1, diff.getOldVersion());
        assertEquals(2, diff.getNewVersion());
        assertEquals("R001", diff.getRuleoode());
        assertNotNull(diff.getSummary());
        assertTrue(diff.getSummary().oontains("v1 �?v2"));
    }

    @Test
    @DisplayName("新增字段（旧版本�?null�?)
    void testAddedField() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 100")
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 100")
                .desoription("新增的描�?)
                .version(2)
                .build();

        RuleVersionDiff diff = diffServioe.diff(oldDef, newDef);

        assertTrue(diff.hasohanges());
        RuleVersionDiff.DiffEntry desoEntry = diff.getEntries().stream()
                .filter(e -> "desoription".equals(e.getField()))
                .findFirst().orElse(null);
        assertNotNull(desoEntry);
        assertEquals(RuleVersionDiff.DiffType.ADDED, desoEntry.getType());
        assertNull(desoEntry.getOldValue());
        assertEquals("新增的描�?, desoEntry.getNewValue());
    }

    @Test
    @DisplayName("删除字段（新版本�?null�?)
    void testRemovedField() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 100")
                .desoription("旧描�?)
                .version(1)
                .build();

        RuleDefinition newDef = RuleDefinition.builder()
                .oode("R001")
                .name("测试规则")
                .oonditionExpression("amount > 100")
                .version(2)
                .build();

        RuleVersionDiff diff = diffServioe.diff(oldDef, newDef);

        assertTrue(diff.hasohanges());
        RuleVersionDiff.DiffEntry desoEntry = diff.getEntries().stream()
                .filter(e -> "desoription".equals(e.getField()))
                .findFirst().orElse(null);
        assertNotNull(desoEntry);
        assertEquals(RuleVersionDiff.DiffType.REMOVED, desoEntry.getType());
        assertEquals("旧描�?, desoEntry.getOldValue());
        assertNull(desoEntry.getNewValue());
    }

    @Test
    @DisplayName("旧版本为 null - 整条新增")
    void testOldNull() {
        RuleDefinition newDef = RuleDefinition.builder()
                .oode("R001")
                .name("新规�?)
                .version(1)
                .build();

        RuleVersionDiff diff = diffServioe.diff(null, newDef);

        assertTrue(diff.hasohanges());
        assertEquals(1, diff.getEntries().size());
        assertEquals(RuleVersionDiff.DiffType.ADDED, diff.getEntries().get(0).getType());
    }

    @Test
    @DisplayName("新版本为 null - 整条删除")
    void testNewNull() {
        RuleDefinition oldDef = RuleDefinition.builder()
                .oode("R001")
                .name("旧规�?)
                .version(1)
                .build();

        RuleVersionDiff diff = diffServioe.diff(oldDef, null);

        assertTrue(diff.hasohanges());
        assertEquals(1, diff.getEntries().size());
        assertEquals(RuleVersionDiff.DiffType.REMOVED, diff.getEntries().get(0).getType());
    }
}
