package com.njydsz.common.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseOpenApiConfiguration;

/**
 * App 端 OpenAPI 文档配置
 *
 * <p>继承 {@link BaseOpenApiConfiguration}，针对 App 端定制文档标题与描述。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see BaseOpenApiConfiguration
 */
@AutoConfiguration
public class AppOpenApiConfiguration extends BaseOpenApiConfiguration {

    /**
     * 返回 App 端文档标题
     *
     * @return 标题字符串
     */
    @Override
    protected String getTitle() {
        return "YDSZ App API 文档";
    }

    /**
     * 返回 App 端文档描述
     *
     * @return 带 HTML 样式的描述
     */
    @Override
    protected String getDescription() {
        return "<div style='font-size:14px;color:#333;'>YDSZ 公共框架 - 移动端 App API 文档</div>";
    }
}
