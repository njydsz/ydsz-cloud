package com.njydsz.common.json;

import com.njydsz.common.json.testbean.MoneyBean;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D2 回归测试：验证移除旧 {@code api.JsonSerializer} 接口后，
 * {@code @JsonSerialize(using=...)} 经新版 {@code serializer.JsonSerializer}
 * 单一调用路径生效。
 */
class CustomSerializerD2Test {

    /**
     * D2：{@code @JsonSerialize} 注解的自定义序列化器（新版 JSONWriter 接口）被正确调用，
     * 输出经自定义逻辑格式化（cents → "x.xx" 字符串）。
     */
    @Test
    void customSerializerNewInterfaceIsInvoked() {
        MoneyBean money = new MoneyBean(12345L); // 123.45
        String json = YdszJson.toJson(money);
        // 自定义序列化器输出为 JSON 字符串 "123.45"
        assertEquals("\"123.45\"", json,
            () -> "custom serializer (new JSONWriter interface) should format cents, got: " + json);
    }

    /**
     * D2：零值边界。
     */
    @Test
    void customSerializerHandlesZero() {
        MoneyBean money = new MoneyBean(0L);
        String json = YdszJson.toJson(money);
        assertEquals("\"0.00\"", json);
    }

    /**
     * D2：负值边界。
     */
    @Test
    void customSerializerHandlesNegative() {
        MoneyBean money = new MoneyBean(-50L); // -0.50
        String json = YdszJson.toJson(money);
        assertTrue(json.contains("0.50"), () -> "got: " + json);
    }

    /**
     * D2：通过 JsonMapper 实例调用同样生效（覆盖 invokeCustomSerializer 在 Mapper 路径）。
     */
    @Test
    void customSerializerWorksViaMapperInstance() {
        MoneyBean money = new MoneyBean(99L);
        JsonMapper mapper = new JsonMapper();
        String json = mapper.toJson(money);
        assertEquals("\"0.99\"", json);
    }
}
