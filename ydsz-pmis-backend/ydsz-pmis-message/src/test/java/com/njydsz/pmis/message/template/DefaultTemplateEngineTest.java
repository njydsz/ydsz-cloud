package com.njydsz.pmis.message.template;

import com.njydsz.pmis.common.exception.BizException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link DefaultTemplateEngine} 单元测试（含 P0-3 增强：条件 / 循环 / 必填校验）。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DefaultTemplateEngine 模板渲染测试")
class DefaultTemplateEngineTest {

    private final DefaultTemplateEngine engine = new DefaultTemplateEngine();

    // ========== 基础变量替换（向后兼容） ==========

    @Test
    @DisplayName("嵌套变量替换 a.b.c")
    void shouldRenderNestedVariable() {
        Map<String, Object> user = new HashMap<>();
        user.put("name", "张三");
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        String result = engine.render("hello ${user.name}", params);
        assertEquals("hello 张三", result);
    }

    @Test
    @DisplayName("未命中占位符替换为空串")
    void shouldReplaceMissingWithEmpty() {
        Map<String, Object> params = new HashMap<>();
        String result = engine.render("a=${missing}b", params);
        assertEquals("a=b", result);
    }

    @Test
    @DisplayName("null 模板返回空串")
    void shouldReturnEmptyForNullTemplate() {
        assertEquals("", engine.render(null, null));
    }

    @Test
    @DisplayName("params 为 null 时返回原模板（变量保留）")
    void shouldReturnOriginalWhenParamsNull() {
        assertEquals("keep ${var}", engine.render("keep ${var}", null));
    }

    @Test
    @DisplayName("简单变量替换")
    void shouldRenderSimpleVariable() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "ABC");
        String result = engine.render("您的验证码是 ${code}", params);
        assertEquals("您的验证码是 ABC", result);
        assertNotNull(result);
    }

    // ========== P0-3: 条件渲染 {{#if}} ==========

    @Test
    @DisplayName("{{#if}} truthy 时渲染 true 分支")
    void shouldRenderTrueBranchWhenTruthy() {
        Map<String, Object> params = new HashMap<>();
        params.put("vip", true);
        String tpl = "{{#if vip}}尊享会员{{/if}}";
        assertEquals("尊享会员", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#if}} falsy 时渲染空串")
    void shouldRenderEmptyWhenFalsy() {
        Map<String, Object> params = new HashMap<>();
        params.put("vip", false);
        String tpl = "{{#if vip}}尊享会员{{/if}}";
        assertEquals("", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#if}}...{{else}}...{{/if}} truthy 时渲染 true 分支")
    void shouldRenderTrueBranchWithElse() {
        Map<String, Object> params = new HashMap<>();
        params.put("vip", true);
        String tpl = "{{#if vip}}尊享会员{{else}}普通用户{{/if}}";
        assertEquals("尊享会员", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#if}}...{{else}}...{{/if}} falsy 时渲染 false 分支")
    void shouldRenderFalseBranchWithElse() {
        Map<String, Object> params = new HashMap<>();
        params.put("vip", false);
        String tpl = "{{#if vip}}尊享会员{{else}}普通用户{{/if}}";
        assertEquals("普通用户", engine.render(tpl, params));
    }

    @Test
    @DisplayName("truthy 判定：非空字符串为 true")
    void shouldTreatNonBlankStringAsTruthy() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        String tpl = "{{#if name}}有名字{{else}}无名{{/if}}";
        assertEquals("有名字", engine.render(tpl, params));
    }

    @Test
    @DisplayName("truthy 判定：空白字符串为 false")
    void shouldTreatBlankStringAsFalsy() {
        Map<String, Object> params = new HashMap<>();
        params.put("name", "   ");
        String tpl = "{{#if name}}有名字{{else}}无名{{/if}}";
        assertEquals("无名", engine.render(tpl, params));
    }

    @Test
    @DisplayName("truthy 判定：非零数字为 true")
    void shouldTreatNonZeroNumberAsTruthy() {
        Map<String, Object> params = new HashMap<>();
        params.put("count", 5);
        assertEquals("有数量", engine.render("{{#if count}}有数量{{else}}无{{/if}}", params));
    }

    @Test
    @DisplayName("truthy 判定：数字 0 为 false")
    void shouldTreatZeroAsFalsy() {
        Map<String, Object> params = new HashMap<>();
        params.put("count", 0);
        assertEquals("无", engine.render("{{#if count}}有数量{{else}}无{{/if}}", params));
    }

    @Test
    @DisplayName("truthy 判定：非空集合为 true")
    void shouldTreatNonEmptyCollectionAsTruthy() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of("a"));
        assertEquals("有项", engine.render("{{#if items}}有项{{else}}无项{{/if}}", params));
    }

    @Test
    @DisplayName("truthy 判定：空集合为 false")
    void shouldTreatEmptyCollectionAsFalsy() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of());
        assertEquals("无项", engine.render("{{#if items}}有项{{else}}无项{{/if}}", params));
    }

    @Test
    @DisplayName("{{#if}} 缺失变量视为 falsy")
    void shouldTreatMissingVariableAsFalsy() {
        Map<String, Object> params = new HashMap<>();
        String tpl = "{{#if missing}}有{{else}}无{{/if}}";
        assertEquals("无", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#if}} 嵌套变量路径 a.b.c 支持")
    void shouldSupportNestedIfPath() {
        Map<String, Object> user = new HashMap<>();
        user.put("vip", true);
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        assertEquals("VIP", engine.render("{{#if user.vip}}VIP{{/if}}", params));
    }

    // ========== P0-3: 循环渲染 {{#each}} ==========

    @Test
    @DisplayName("{{#each}} 遍历字符串列表，{{this}} 替换为元素")
    void shouldIterateStringListWithThis() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of("苹果", "香蕉", "橙子"));
        String tpl = "{{#each items}}-${this}-{{/each}}";
        assertEquals("-苹果--香蕉--橙子-", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 遍历 Map 列表，{{this.prop}} 取属性")
    void shouldIterateMapListWithThisProp() {
        Map<String, Object> i1 = new HashMap<>();
        i1.put("name", "张三");
        Map<String, Object> i2 = new HashMap<>();
        i2.put("name", "李四");
        Map<String, Object> params = new HashMap<>();
        params.put("users", List.of(i1, i2));
        String tpl = "{{#each users}}${this.name} {{/each}}";
        assertEquals("张三 李四 ", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 支持 {{@index}} 索引")
    void shouldSupportIndexInEach() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of("A", "B", "C"));
        String tpl = "{{#each items}}${@index}:${this} {{/each}}";
        assertEquals("0:A 1:B 2:C ", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 空列表渲染空串")
    void shouldRenderEmptyForEmptyList() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", List.of());
        String tpl = "{{#each items}}-${this}-{{/each}}";
        assertEquals("", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 非可迭代值渲染空串")
    void shouldRenderEmptyForNonIterable() {
        Map<String, Object> params = new HashMap<>();
        params.put("items", "不是列表");
        String tpl = "{{#each items}}-${this}-{{/each}}";
        assertEquals("", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 缺失变量渲染空串")
    void shouldRenderEmptyForMissingList() {
        Map<String, Object> params = new HashMap<>();
        String tpl = "{{#each missing}}-${this}-{{/each}}";
        assertEquals("", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 块内可访问父级作用域变量")
    void shouldAccessParentScopeInEach() {
        Map<String, Object> params = new HashMap<>();
        params.put("prefix", "项");
        params.put("items", List.of("A", "B"));
        String tpl = "{{#each items}}${prefix}-${this} {{/each}}";
        assertEquals("项-A 项-B ", engine.render(tpl, params));
    }

    @Test
    @DisplayName("{{#each}} 内嵌 {{#if}} 条件渲染")
    void shouldSupportIfInsideEach() {
        Map<String, Object> u1 = new HashMap<>();
        u1.put("name", "张三");
        u1.put("vip", true);
        Map<String, Object> u2 = new HashMap<>();
        u2.put("name", "李四");
        u2.put("vip", false);
        Map<String, Object> params = new HashMap<>();
        params.put("users", List.of(u1, u2));
        String tpl = "{{#each users}}${this.name}{{#if this.vip}}(VIP){{/if}} {{/each}}";
        assertEquals("张三(VIP) 李四 ", engine.render(tpl, params));
    }

    // ========== P0-3: 必填参数校验 ==========

    @Test
    @DisplayName("必填参数齐全时正常渲染")
    void shouldRenderWhenRequiredKeysPresent() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "1234");
        params.put("phone", "13800000000");
        String tpl = "验证码 ${code} 发送到 ${phone}";
        String result = engine.render(tpl, params, Set.of("code", "phone"));
        assertEquals("验证码 1234 发送到 13800000000", result);
    }

    @Test
    @DisplayName("必填参数缺失时抛 BizException")
    void shouldThrowWhenRequiredKeyMissing() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "1234");
        // phone 缺失
        BizException ex = assertThrows(BizException.class, () ->
                engine.render("验证码 ${code}", params, Set.of("code", "phone")));
        assertTrue(ex.getMessage().contains("phone"));
    }

    @Test
    @DisplayName("必填参数为 null 时抛 BizException")
    void shouldThrowWhenRequiredKeyIsNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", null);
        BizException ex = assertThrows(BizException.class, () ->
                engine.render("验证码 ${code}", params, Set.of("code")));
        assertTrue(ex.getMessage().contains("code"));
    }

    @Test
    @DisplayName("必填参数为空白字符串时抛 BizException")
    void shouldThrowWhenRequiredKeyIsBlank() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "   ");
        BizException ex = assertThrows(BizException.class, () ->
                engine.render("验证码 ${code}", params, Set.of("code")));
        assertTrue(ex.getMessage().contains("code"));
    }

    @Test
    @DisplayName("requiredKeys 为 null 时跳过校验")
    void shouldSkipValidationWhenRequiredKeysNull() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "1234");
        // 不抛异常
        String result = engine.render("验证码 ${code}", params, null);
        assertEquals("验证码 1234", result);
    }

    @Test
    @DisplayName("requiredKeys 为空集合时跳过校验")
    void shouldSkipValidationWhenRequiredKeysEmpty() {
        Map<String, Object> params = new HashMap<>();
        String result = engine.render("无参数模板", params, Set.of());
        assertEquals("无参数模板", result);
    }

    @Test
    @DisplayName("必填参数支持嵌套路径 a.b.c")
    void shouldValidateNestedRequiredKey() {
        Map<String, Object> user = new HashMap<>();
        user.put("name", "张三");
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        // 嵌套路径存在
        String r1 = engine.render("${user.name}", params, Set.of("user.name"));
        assertEquals("张三", r1);
        // 嵌套路径缺失
        assertThrows(BizException.class, () ->
                engine.render("${user.age}", params, Set.of("user.age")));
    }

    // ========== 综合场景 ==========

    @Test
    @DisplayName("综合：变量 + 条件 + 循环混合渲染")
    void shouldRenderMixedTemplate() {
        Map<String, Object> u1 = new HashMap<>();
        u1.put("name", "张三");
        u1.put("amount", 100);
        Map<String, Object> u2 = new HashMap<>();
        u2.put("name", "李四");
        u2.put("amount", 0);
        Map<String, Object> params = new HashMap<>();
        params.put("title", "对账单");
        params.put("users", List.of(u1, u2));
        String tpl = "${title}:{{#each users}}${this.name}={{#if this.amount}}有{{else}}无{{/if}};{{/each}}";
        assertEquals("对账单:张三=有;李四=无;", engine.render(tpl, params));
    }

    @Test
    @DisplayName("向后兼容：原 ${var} 模板行为不变")
    void shouldKeepBackwardCompatibility() {
        Map<String, Object> params = new HashMap<>();
        params.put("code", "ABC");
        // 不含块语法的模板，渲染结果与原实现一致
        assertEquals("您的验证码是 ABC", engine.render("您的验证码是 ${code}", params));
    }
}
