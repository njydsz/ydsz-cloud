package com.njydsz.pmis.common.web.config;

import com.njydsz.pmis.common.base.config.BaseOpenApiConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * Web 端 OpenAPI 文档配置
 *
 * <p>继承 {@link BaseOpenApiConfiguration}，为 Web 端配置 Knife4j/Swagger 文档信息。
 * 包括文档标题、描述、分组等。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see BaseOpenApiConfiguration
 */
@AutoConfiguration
public class WebOpenApiConfiguration extends BaseOpenApiConfiguration {

    /**
     * 获取 API 文档标题
     *
     * @return 文档标题
     */
    @Override
    protected String getTitle() {
        return "REMI Web API 文档";
    }

    /**
     * 获取 API 文档描述（支持 HTML）
     *
     * @return 文档描述
     */
    @Override
    protected String getDescription() {
        return "<div style='font-size:14px;color:#333;'>REMI 公共框架 - PC Web 端 API 文档</div>";
    }
}
