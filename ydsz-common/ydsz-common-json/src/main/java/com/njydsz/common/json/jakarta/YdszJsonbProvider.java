package com.njydsz.common.json.jakarta;

import jakarta.json.bind.Jsonb;
import jakarta.json.bind.JsonbBuilder;
import jakarta.json.bind.spi.JsonbProvider;

/**
 * YdszJson JSON-B Provider（SPI 注册入口）。
 *
 * <p>通过 {@code META-INF/services/jakarta.json.bind.spi.JsonbProvider} 注册，
 * 使 {@link JsonbBuilder#create()} 自动发现 YdszJson-backed 的 JSON-B 实现。</p>
 *
 * <p><b>对标：</b>对标 Eclipse Yasson 的 {@code YassonJsonbProvider}、
 * Jackson JSON-B 适配器的 {@code JacksonJsonbProvider}。</p>
 *
 * @author ydsz-team
 * @since 1.2.1
 */
public class YdszJsonbProvider extends JsonbProvider {

    @Override
    public JsonbBuilder create() {
        return new YdszJsonbBuilder();
    }
}
