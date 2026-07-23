package com.njydsz.common.web.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseOpenApiConfiguration;

/**
 * Web 端 OpenAPI 文档配置
 *
 * <p>继承 {@link BaseOpenApiConfiguration}，为 Web 端配置 Knife4j/Swagger 文档信息。
 * 包括文档标题、描述、分组等。
 *
 * @author ydsz-team
 * @see BaseOpenApiConfiguration
 */
@AutoConfiguration
public class WebOpenApiConfiguration extends BaseOpenApiConfiguration {

    @Override
    protected String getTitle() {
        return "YDSZ Web API 文档";
    }

    @Override
    protected String getDescription() {
        return "<div style='font-size:14px;color:#333;'>YDSZ 公共框架 - PC Web 端 API 文档</div>";
    }
}
