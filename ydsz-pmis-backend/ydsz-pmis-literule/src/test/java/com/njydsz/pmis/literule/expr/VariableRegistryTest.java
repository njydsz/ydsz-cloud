package com.njydsz.pmis.literule.expr;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * VariableRegistry 单元测试
 *
 * <p>覆盖：
 * <ul>
 *   <li>EmptyVariableRegistry：空实现向后兼容</li>
 *   <li>InMemoryVariableRegistry：动态注册 + 查询 + 线程安全</li>
 *   <li>ExpressionValidationService + VariableRegistry 集成：UNDEFINED_VARIABLE 校验</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
class VariableRegistryTest {

    private AviatorExpressionEvaluator evaluator;
    private InMemoryVariableRegistry registry;

    @BeforeEach
    void setUp() {
        evaluator = new AviatorExpressionEvaluator(true);
        registry = new InMemoryVariableRegistry();
        // 注册一组常见变量
        registry.register(VariableDefinition.builder()
                .name("cpi").type("java.lang.Number").description("成本绩效指数").category("EVM")
                .sampleValue(0.85).build());
        registry.register(VariableDefinition.builder()
                .name("budgetAmount").type("java.lang.Number").description("项目预算").category("BUDGET")
                .sampleValue(500000).build());
        registry.register(VariableDefinition.builder()
                .name("evmRedCount").type("java.lang.Integer").description("红色预警数量").category("EVM")
                .sampleValue(3).build());
    }

    // ---------- EmptyVariableRegistry ----------

    @Test
    void emptyRegistryShouldReturnNullOnLookup() {
        EmptyVariableRegistry empty = new EmptyVariableRegistry();
        assertNull(empty.lookup("anyVar"));
        assertTrue(empty.isEmpty());
        assertTrue(empty.listAll().isEmpty());
    }

    // ---------- InMemoryVariableRegistry ----------

    @Test
    void inMemoryRegistryShouldLookupRegisteredVariables() {
        VariableDefinition def = registry.lookup("cpi");
        assertNotNull(def);
        assertEquals("cpi", def.getName());
        assertEquals("java.lang.Number", def.getType());
        assertEquals("成本绩效指数", def.getDescription());
        assertEquals("EVM", def.getCategory());
    }

    @Test
    void inMemoryRegistryShouldReturnNullForUnregistered() {
        assertNull(registry.lookup("unregisteredVar"));
        assertFalse(registry.contains("unregisteredVar"));
    }

    @Test
    void inMemoryRegistryShouldListAllByCategory() {
        List<VariableDefinition> evmVars = registry.listByCategory("EVM");
        assertEquals(2, evmVars.size());
        assertTrue(evmVars.stream().anyMatch(v -> v.getName().equals("cpi")));
        assertTrue(evmVars.stream().anyMatch(v -> v.getName().equals("evmRedCount")));

        List<VariableDefinition> budgetVars = registry.listByCategory("BUDGET");
        assertEquals(1, budgetVars.size());
        assertEquals("budgetAmount", budgetVars.get(0).getName());
    }

    @Test
    void inMemoryRegistryShouldSupportBatchRegister() {
        InMemoryVariableRegistry r = new InMemoryVariableRegistry();
        r.registerAll(List.of(
                VariableDefinition.builder().name("a").type("String").build(),
                VariableDefinition.builder().name("b").type("Number").build()
        ));
        assertEquals(2, r.size());
        assertTrue(r.contains("a"));
        assertTrue(r.contains("b"));
    }

    @Test
    void inMemoryRegistryShouldClearAll() {
        InMemoryVariableRegistry r = new InMemoryVariableRegistry();
        r.register(VariableDefinition.builder().name("temp").type("String").build());
        assertEquals(1, r.size());
        r.clear();
        assertEquals(0, r.size());
        assertTrue(r.isEmpty());
    }

    @Test
    void registerNullDefinitionShouldThrow() {
        InMemoryVariableRegistry r = new InMemoryVariableRegistry();
        assertThrows(IllegalArgumentException.class, () -> r.register(null));
        assertThrows(IllegalArgumentException.class,
                () -> r.register(VariableDefinition.builder().name(null).build()));
    }

    // ---------- ExpressionValidationService + VariableRegistry 集成 ----------

    @Test
    void validationServiceWithoutRegistryShouldNotCheckUndefinedVariables() {
        // 不注入 registry，使用 EmptyVariableRegistry，UNDEFINED_VARIABLE 校验跳过
        ExpressionValidationService svc = new ExpressionValidationService(evaluator);
        ExpressionValidationResult result = svc.validateCondition("undefinedVar > 1");
        assertTrue(result.isValid(), "无 registry 时不应触发 UNDEFINED_VARIABLE 校验");
    }

    @Test
    void validationServiceWithEmptyRegistryShouldNotCheck() {
        // 注入空 registry，仍跳过
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, new EmptyVariableRegistry());
        ExpressionValidationResult result = svc.validateCondition("undefinedVar > 1");
        assertTrue(result.isValid());
    }

    @Test
    void validationServiceWithRegistryShouldReportUndefinedVariable() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        // cpi 已注册，undefinedVar 未注册
        ExpressionValidationResult result = svc.validateCondition("cpi < 0.85 && undefinedVar > 1");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE, result.getErrorType());
        assertTrue(result.getErrorMessage().contains("undefinedVar"));
        assertTrue(result.getErrorMessage().contains("1 个"));
        // referencedVariables 应同时包含已注册和未注册的
        assertTrue(result.getReferencedVariables().contains("cpi"));
        assertTrue(result.getReferencedVariables().contains("undefinedVar"));
    }

    @Test
    void validationServiceWithAllRegisteredShouldPass() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        ExpressionValidationResult result = svc.validateCondition("cpi < 0.85 && evmRedCount >= 3");

        assertTrue(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.OK, result.getErrorType());
    }

    @Test
    void validationServiceShouldReportMultipleUndefinedVariables() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        ExpressionValidationResult result = svc.validateCondition("cpi < 0.85 && typo1 > 1 || typo2 < 0");

        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE, result.getErrorType());
        assertTrue(result.getErrorMessage().contains("typo1"));
        assertTrue(result.getErrorMessage().contains("typo2"));
        assertTrue(result.getErrorMessage().contains("2 个"));
    }

    @Test
    void validationServiceShouldCheckSeverityExpressionVariables() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        ExpressionValidationResult result = svc.validateSeverity("cpi < 0.70 ? 'RED' : 'YELLOW'");
        assertTrue(result.isValid());

        ExpressionValidationResult result2 = svc.validateSeverity("undefinedSeverityVar > 1 ? 'RED' : 'YELLOW'");
        assertFalse(result2.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE, result2.getErrorType());
    }

    @Test
    void validationServiceShouldCheckTemplateVariables() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        // 模板中引用已注册变量
        ExpressionValidationResult ok = svc.validateTemplate("CPI ${cpi} 红色预警 ${evmRedCount}");
        assertTrue(ok.isValid());
        assertTrue(ok.getReferencedVariables().contains("cpi"));
        assertTrue(ok.getReferencedVariables().contains("evmRedCount"));

        // 模板中引用未注册变量
        ExpressionValidationResult bad = svc.validateTemplate("项目 ${projectName} 的 CPI 为 ${cpi}");
        assertFalse(bad.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.UNDEFINED_VARIABLE, bad.getErrorType());
        assertTrue(bad.getErrorMessage().contains("projectName"));
    }

    @Test
    void syntaxErrorShouldTakePrecedenceOverUndefinedVariable() {
        // 语法错误时直接返回，不进入变量校验
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        ExpressionValidationResult result = svc.validateCondition("cpi < ");
        assertFalse(result.isValid());
        assertEquals(ExpressionValidationResult.ErrorType.SYNTAX_ERROR, result.getErrorType());
    }

    // ---------- listAvailableVariables ----------

    @Test
    void listAvailableVariablesShouldReturnRegistered() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        List<VariableDefinition> vars = svc.listAvailableVariables();
        assertEquals(3, vars.size());
    }

    @Test
    void listVariablesByCategoryShouldFilterByCategory() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        List<VariableDefinition> evmVars = svc.listVariablesByCategory("EVM");
        assertEquals(2, evmVars.size());
        List<VariableDefinition> budgetVars = svc.listVariablesByCategory("BUDGET");
        assertEquals(1, budgetVars.size());
    }

    @Test
    void getVariableRegistryShouldReturnInjected() {
        ExpressionValidationService svc = new ExpressionValidationService(evaluator, registry);
        assertSame(registry, svc.getVariableRegistry());
    }

    // ---------- VariableDefinition.getSimpleType ----------

    @Test
    void variableDefinitionGetSimpleTypeShouldStripJavaLangPrefix() {
        VariableDefinition def = VariableDefinition.builder()
                .name("test").type("java.lang.Number").build();
        assertEquals("Number", def.getSimpleType());
    }

    @Test
    void variableDefinitionGetSimpleTypeShouldHandleNull() {
        VariableDefinition def = VariableDefinition.builder()
                .name("test").type(null).build();
        assertEquals("Object", def.getSimpleType());
    }
}
