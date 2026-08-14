package com.njydsz.common.json.jakarta;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.JsonbConfig;

/**
 * YdszJson 的 JSON-B Builder 实现。
 *
 * <p>实现 {@link JsonbBuilder} 接口，使业务代码可通过标准 {@code JsonbBuilder.create()} 方式获取 YdszJson-backed 的 {@link Jsonb} 实例。</p>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 标准 JSON-B 创建方式
 * Jsonb jsonb = JsonbBuilder.create();
 * String json = jsonb.toJson(user);
 *
 * // 使用配置（当前版本忽略配置，使用 YdszJson 全局配置）
 * JsonbConfig config = new JsonbConfig().withNullValues(true);
 * Jsonb jsonbWithConfig = JsonbBuilder.create(config);
 * </pre>
 *
 * <p><b>SPI 注册：</b>通过 {@code META-INF/services/jakarta.json.bind.spi.JsonbProvider}
 * 注册，使 {@code JsonbBuilder.create()} 自动发现 YdszJsonb。</p>
 *
 * @author ydsz-team
 * @since 1.2.1
 */
public class YdszJsonbBuilder extends JsonbBuilder {

    @Override
    public Jsonb build() {
        return YdszJsonb.defaultInstance();
    }

    @Override
    public JsonbBuilder withConfig(JsonbConfig config) {
        // 当前版本：配置项通过 YdszJson.builder() 设置，此处返回 this 待后续版本对齐
        return this;
    }
}
