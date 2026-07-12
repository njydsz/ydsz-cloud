package com.njydsz.pmis.common.app.config;

import com.njydsz.pmis.common.base.config.BaseOpenApiConfiguration;
import org.springframework.boot.autoconfigure.AutoConfiguration;

/**
 * App 端 OpenAPI 文档配置
 *
 * <p>继承 {@link BaseOpenApiConfiguration}，针对 App 端定制文档标题与描述。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@AutoConfiguration
public class AppOpenApiConfiguration extends BaseOpenApiConfiguration {

    @Override
    protected String getTitle() {
        return "PMIS App API 文档";
    }

    @Override
    protected String getDescription() {
        return "<div style='font-size:14px;color:#333;'>PMIS 公共框架 - 移动端 App API 文档</div>";
    }
}
