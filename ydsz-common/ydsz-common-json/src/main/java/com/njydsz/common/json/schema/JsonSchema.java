package com.njydsz.common.json.schema;

import java.util.List;
import java.util.Map;

/**
 * JSON Schema 校验器构建器。
 *
 * @deprecated JSON Schema 校验引擎已移除（v1.1.0）。
 * 内部服务无需嵌入式 Draft 07 Schema 校验能力。
 * 如需 JSON Schema 校验，请引入专业的外部库（如 networknt/json-schema-validator）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @deprecated since 1.1.0
 */
@Deprecated
@SuppressWarnings("unused")
public final class JsonSchema {

    private JsonSchema() {
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder object() {
        return new Builder();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder string() {
        return new Builder();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder number() {
        return new Builder();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder integer() {
        return new Builder();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder booleanType() {
        return new Builder();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder array() {
        return new Builder();
    }

    /** @deprecated 已废弃 */
    @Deprecated
    public static Builder nullType() {
        return new Builder();
    }

    /**
     * @deprecated 已废弃
     */
    @Deprecated
    public static final class Builder {
        private Builder() {}

        @Deprecated
        public Builder addProperty(String name, JsonSchema schema) { return this; }

        @Deprecated
        public Builder addRequired(String... fields) { return this; }

        @Deprecated
        public Builder description(String desc) { return this; }

        @Deprecated
        public Builder minLength(int min) { return this; }

        @Deprecated
        public Builder maxLength(int max) { return this; }

        @Deprecated
        public Builder minimum(double min) { return this; }

        @Deprecated
        public Builder maximum(double max) { return this; }

        @Deprecated
        public Builder pattern(String regex) { return this; }

        @Deprecated
        public Builder enumValues(Object... values) { return this; }

        @Deprecated
        public Builder required() { return this; }

        @Deprecated
        public Map<String, Object> build() { return java.util.Collections.emptyMap(); }
    }
}
