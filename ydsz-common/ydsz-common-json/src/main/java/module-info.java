/**
 * YdszJson - 零依赖 JSON 引擎模块。
 *
 * <p>提供高性能 JSON 序列化/反序列化能力，纯 Java 实现，无需第三方依赖。</p>
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

    // 安全白名单引擎
    exports com.njydsz.common.json.autotype;

    // Schema 校验（@Experimental）
    exports com.njydsz.common.json.schema;
}
