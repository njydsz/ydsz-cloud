package com.njydsz.pmis.literule.core;

import com.njydsz.pmis.literule.api.Rule;
import com.njydsz.pmis.literule.api.RuleContext;
import com.njydsz.pmis.literule.api.RuleDefinition;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ConditionSharingOptimizer 单元测试
 *
 * @author ydsz-pmis-team
 * @since 2.1.0
 */
class ConditionSharingOptimizerTest {

    private final ConditionSharingOptimizer optimizer = new ConditionSharingOptimizer();

    @Test
    void extractAtomicConditions_simpleAndExpression() {
        String expr = "amount > 10000 && riskLevel == 'HIGH'";
        String[] atoms = optimizer.extractAtomicConditions(expr);

        assertEquals(2, atoms.length);
        assertTrue(Arrays.asList(atoms).contains("amount > 10000"));
        assertTrue(Arrays.asList(atoms).contains("riskLevel == 'HIGH'"));
    }

    @Test
    void extractAtomicConditions_simpleOrExpression() {
        String expr = "amount > 10000 || riskLevel == 'HIGH'";
        String[] atoms = optimizer.extractAtomicConditions(expr);

        assertEquals(2, atoms.length);
    }

    @Test
    void extractAtomicConditions_nestedParentheses() {
        String expr = "(amount > 10000 && riskLevel == 'HIGH') || score < 60";
        String[] atoms = optimizer.extractAtomicConditions(expr);

        assertEquals(3, atoms.length);
    }

    @Test
    void extractAtomicConditions_deduplicatesConditions() {
        String expr = "amount > 10000 && amount > 10000";
        String[] atoms = optimizer.extractAtomicConditions(expr);

        assertEquals(1, atoms.length);
    }

    @Test
    void extractAtomicConditions_handlesNegation() {
        String expr = "!(amount > 10000) && riskLevel == 'HIGH'";
        String[] atoms = optimizer.extractAtomicConditions(expr);

        assertEquals(2, atoms.length);
    }

    @Test
    void extractAtomicConditions_emptyExpression() {
        String[] atoms = optimizer.extractAtomicConditions("");
        assertEquals(0, atoms.length);
    }

    @Test
    void extractAtomicConditions_singleCondition() {
        String[] atoms = optimizer.extractAtomicConditions("amount > 10000");
        assertEquals(1, atoms.length);
        assertEquals("amount > 10000", atoms[0]);
    }

    @Test
    void extractAtomicConditions_complexNestedExpression() {
        String expr = "(a > 1 && b < 2) || (c == 3 && d != 4) && e >= 5";
        String[] atoms = optimizer.extractAtomicConditions(expr);

        assertEquals(5, atoms.length);
    }

    @Test
    void optimize_cachesAtomicConditions() {
        RuleDefinition def1 = RuleDefinition.builder()
                .code("R001")
                .conditionExpression("amount > 10000 && riskLevel == 'HIGH'")
                .build();
        RuleDefinition def2 = RuleDefinition.builder()
                .code("R002")
                .conditionExpression("amount > 10000 && score < 60")
                .build();

        Rule rule1 = createMockRule(def1);
        Rule rule2 = createMockRule(def2);

        RuleContext context = RuleContext.of(
                java.util.Map.of("amount", 15000, "riskLevel", "HIGH", "score", 50),
                "TEST", "test", "trace-001", "tenant-001", "default"
        );

        List<Rule> candidates = Arrays.asList(rule1, rule2);
        optimizer.optimize(candidates, context);

        // 3 unique atoms: amount > 10000, riskLevel == 'HIGH', score < 60
        int cached = optimizer.getCachedConditionCount(context);
        assertEquals(3, cached);
    }

    @Test
    void optimize_skipsRulesWithoutDefinition() {
        Rule rule = createMockRule(null);
        RuleContext context = RuleContext.of(
                java.util.Map.of(),
                "TEST", "test", "trace-002", "tenant-001", "default"
        );

        optimizer.optimize(List.of(rule), context);
        assertEquals(0, optimizer.getCachedConditionCount(context));
    }

    @Test
    void optimize_nullInputsHandledSafely() {
        assertDoesNotThrow(() -> optimizer.optimize(null, null));
    }

    /**
     * 创建模拟规则
     */
    private Rule createMockRule(RuleDefinition def) {
        return new Rule() {
            @Override
            public String getCode() { return def != null ? def.getCode() : "MOCK"; }

            @Override
            public String getName() { return "Mock Rule"; }

            @Override
            public String getCategory() { return "TEST"; }

            @Override
            public int getPriority() { return 100; }

            @Override
            public com.njydsz.pmis.literule.api.RuleResult evaluate(RuleContext context) {
                return null;
            }

            @Override
            public RuleDefinition getRuleDefinition() { return def; }
        };
    }
}
