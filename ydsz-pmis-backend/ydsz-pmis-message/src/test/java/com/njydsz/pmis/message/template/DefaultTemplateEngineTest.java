package com.njydsz.pmis.message.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * {@link DefaultTemplateEngine} 单元测试。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("DefaultTemplateEngine 模板渲染测试")
class DefaultTemplateEngineTest {

    private final DefaultTemplateEngine engine = new DefaultTemplateEngine();

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
    @DisplayName("params 为 null 时返回原模板")
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
}
