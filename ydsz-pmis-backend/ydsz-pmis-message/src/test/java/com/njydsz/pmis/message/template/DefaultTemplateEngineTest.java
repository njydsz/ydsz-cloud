package com.njydsz.pmis.message.template;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * DefaultTemplateEngine 单元测试
 */
@DisplayName("DefaultTemplateEngine 模板引擎测试")
class DefaultTemplateEngineTest {

    private final DefaultTemplateEngine engine = new DefaultTemplateEngine();

    @Test
    @DisplayName("${var} 占位符替换")
    void renderSimple() {
        String tpl = "Hello, ${name}! 您的订单号是 ${orderNo}";
        Map<String, Object> params = new HashMap<>();
        params.put("name", "张三");
        params.put("orderNo", "ORD-001");
        assertThat(engine.render(tpl, params)).isEqualTo("Hello, 张三! 您的订单号是 ORD-001");
    }

    @Test
    @DisplayName("嵌套 key 解析")
    void renderNested() {
        String tpl = "用户: ${user.name}, 角色: ${user.role}";
        Map<String, Object> user = new HashMap<>();
        user.put("name", "李四");
        user.put("role", "ADMIN");
        Map<String, Object> params = new HashMap<>();
        params.put("user", user);
        assertThat(engine.render(tpl, params)).isEqualTo("用户: 李四, 角色: ADMIN");
    }

    @Test
    @DisplayName("缺失 key 渲染为空字符串")
    void renderMissingKey() {
        String tpl = "Hi ${unknown}";
        assertThat(engine.render(tpl, new HashMap<>())).isEqualTo("Hi ");
    }

    @Test
    @DisplayName("null 模板返回空字符串")
    void renderNullTemplate() {
        assertThat(engine.render(null, null)).isEmpty();
    }

    @Test
    @DisplayName("无占位符时原样返回")
    void renderNoPlaceholder() {
        assertThat(engine.render("纯文本内容", new HashMap<>())).isEqualTo("纯文本内容");
    }

    @Test
    @DisplayName("null 值转空串")
    void renderNullValue() {
        String tpl = "Hi ${x}";
        Map<String, Object> p = new HashMap<>();
        p.put("x", null);
        assertThat(engine.render(tpl, p)).isEqualTo("Hi ");
    }
}
