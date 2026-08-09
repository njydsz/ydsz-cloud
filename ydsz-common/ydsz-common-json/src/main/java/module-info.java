/**
 * YdszJson - 零依赖 JSON 引擎模块。
 *
 * <p>提供高性能 JSON 序列化/反序列化能力，纯 Java 实现，无需第三方 JSON 库依赖。</p>
 *
 * <p><b>JPMS 说明：</b></p>
 * <ul>
 *   <li>核心引擎（com.njydsz.common.json 及其子包）零外部运行时依赖</li>
 *   <li>Spring / SLF4J / Jakarta / Jackson-Annotations 均为 optional 依赖，
 *       通过 {@code requires static} 声明以支持可选的 Spring Boot 集成</li>
 * </ul>
 *
 * @since 1.2.0
 */
module com.njydsz.common.json {
    // 核心 API
    exports com.njydsz.common.json;

    // 注解（供用户使用）
    exports com.njydsz.common.json.annotation;

    // 树模型
    exports com.njydsz.common.json.tree;

    // 命名策略
    exports com.njydsz.common.json.naming;

    // 模块 SPI
    exports com.njydsz.common.json.module;

    // 异常
    exports com.njydsz.common.json.exception;

    // 类型工厂
    exports com.njydsz.common.json.type;

    // 序列化器接口
    exports com.njydsz.common.json.serializer;
    exports com.njydsz.common.json.deserializer;

    // ===== optional 依赖（Spring Boot 集成 / 日志 / 校验 / Jackson 注解兼容） =====
    requires static org.slf4j;
    requires static spring.web;
    requires static spring.core;
    requires static spring.context;
    requires static spring.beans;
    requires static spring.boot;
    requires static spring.boot.autoconfigure;
    requires static jakarta.annotation;
    requires static jakarta.validation;
    requires static com.fasterxml.jackson.annotation;
    // JDK 模块（java.sql: Timestamp/Date 序列化支持）
    requires static java.sql;
}
