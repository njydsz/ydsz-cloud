package com.njydsz.common.app.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;

import com.njydsz.common.base.config.BaseOpenApiConfiguration;

/**
 * App 子模块 OpenAPI 配置。
 *
 * <p>继承 {@link BaseOpenApiConfiguration}，为 ydsz-app 子模块（移动端 API）提供独立的 OpenAPI 分组配置。
 *
 * <p>支持与 base 不同的标题/描述/版本，覆盖移动端的 Swagger/Knife4j 展示。
 *
 * @author ydsz-team
 * @since 1.0.0
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
