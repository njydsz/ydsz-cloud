package com.njydsz.common.json.schema;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Schema（Draft 07 核心子集）定义类。
 *
 * <p>采用 Builder 模式构建约束规则，供 {@link JsonSchemaValidator} 执行校验。
 * 覆盖最常用的约束类型：字符串长度/格式/正则、数值范围、数组长度、对象必填字段、枚举值。
 *
 * <p><b>使用示例：</b></p>
 * <pre>{@code
 * // 定义字符串邮箱字段
 * JsonSchema emailSchema = JsonSchema.string()
 *     .minLength(5)
 *     .maxLength(255)
 *     .format("email");
 *
 * // 定义整数范围
 * JsonSchema portSchema = JsonSchema.integer()
 *     .minimum(1)
 *     .maximum(65535);
 *
 * // 定义对象
 * JsonSchema objectSchema = JsonSchema.object()
 *     .required("host", "port")
 *     .property("host", JsonSchema.string().minLength(1))
 *     .property("port", JsonSchema.integer().minimum(1))
 *     .additionalProperties(false);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see JsonSchemaValidator
 */
@Experimental("JSON Schema Draft 07，API 可能随版本调整")
public final class JsonSchema {

    // -------- 静态工厂方法 --------

    /** 创建字符串 Schema。 */
    public static Builder string() {
        return new Builder("string");
    }

    /** 创建整数 Schema。 */
    public static Builder integer() {
        return new Builder("integer");
    }

    /** 创建数值 Schema（整数或浮点数）。 */
    public static Builder number() {
        return new Builder("number");
    }

    /** 创建布尔 Schema。 */
    public static Builder booleanType() {
        return new Builder("boolean");
    }

    /** 创建数组 Schema。 */
    public static Builder array() {
        return new Builder("array");
    }

    /** 创建对象 Schema。 */
    public static Builder object() {
        return new Builder("object");
    }

    /** 创建 null Schema。 */
    public static Builder nullType() {
        return new Builder("null");
    }

    // -------- Builder --------

    /**
     * Schema 构造器（Builder 模式）。
     */
    public static final class Builder {
        private final String type;

        // 字符串约束
        private Integer minLength;
        private Integer maxLength;
        private Pattern pattern;
        private String format;

        // 数值约束
        private Number minimum;
        private Number maximum;
        private Number multipleOf;
        private Boolean exclusiveMinimum;
        private Boolean exclusiveMaximum;

        // 数组约束
        private Integer minItems;
        private Integer maxItems;
        private Boolean uniqueItems;
        private JsonSchema items;

        // 对象约束
        private Integer minProperties;
        private Integer maxProperties;
        private List<String> required;
        private Map<String, JsonSchema> properties;
        private Map<String, JsonSchema> patternProperties;
        private Boolean additionalProperties;
        private JsonSchema additionalPropertiesSchema;
        private Map<String, List<String>> dependencies;

        // 通用
        private List<Object> enumValues;
        private Object constValue;
        private List<JsonSchema> allOf;
        private List<JsonSchema> anyOf;
        private List<JsonSchema> oneOf;
        private JsonSchema not;

        private Builder(String type) {
            this.type = type;
        }

        /**
         * 构建最终的 JsonSchema 实例。
         *
         * @return 不可变的 Schema 实例
         */
        public JsonSchema build() {
            return new JsonSchema(this);
        }

        // --- 字符串约束 ---

        public Builder minLength(int minLength) {
            this.minLength = minLength;
            return this;
        }

        public Builder maxLength(int maxLength) {
            this.maxLength = maxLength;
            return this;
        }

        public Builder pattern(String regex) {
            this.pattern = Pattern.compile(regex);
            return this;
        }

        /** 格式约束（支持: date-time, email, uri, uuid, ipv4, hostname） */
        public Builder format(String format) {
            this.format = format;
            return this;
        }

        // --- 数值约束 ---

        public Builder minimum(Number minimum) {
            this.minimum = minimum;
            return this;
        }

        public Builder maximum(Number maximum) {
            this.maximum = maximum;
            return this;
        }

        public Builder exclusiveMinimum(boolean exclusive) {
            this.exclusiveMinimum = exclusive;
            return this;
        }

        public Builder exclusiveMaximum(boolean exclusive) {
            this.exclusiveMaximum = exclusive;
            return this;
        }

        public Builder multipleOf(Number multipleOf) {
            this.multipleOf = multipleOf;
            return this;
        }

        // --- 数组约束 ---

        public Builder minItems(int minItems) {
            this.minItems = minItems;
            return this;
        }

        public Builder maxItems(int maxItems) {
            this.maxItems = maxItems;
            return this;
        }

        public Builder uniqueItems(boolean uniqueItems) {
            this.uniqueItems = uniqueItems;
            return this;
        }

        public Builder items(JsonSchema items) {
            this.items = items;
            return this;
        }

        // --- 对象约束 ---

        public Builder minProperties(int minProperties) {
            this.minProperties = minProperties;
            return this;
        }

        public Builder maxProperties(int maxProperties) {
            this.maxProperties = maxProperties;
            return this;
        }

        public Builder required(String... fields) {
            if (this.required == null) {
                this.required = new ArrayList<>();
            }
            for (String field : fields) {
                this.required.add(field);
            }
            return this;
        }

        public Builder property(String name, JsonSchema schema) {
            if (this.properties == null) {
                this.properties = new LinkedHashMap<>();
            }
            this.properties.put(name, schema);
            return this;
        }

        public Builder patternProperty(String regex, JsonSchema schema) {
            if (this.patternProperties == null) {
                this.patternProperties = new LinkedHashMap<>();
            }
            this.patternProperties.put(regex, schema);
            return this;
        }

        public Builder additionalProperties(boolean allow) {
            this.additionalProperties = allow;
            return this;
        }

        public Builder additionalProperties(JsonSchema schema) {
            this.additionalProperties = true;
            this.additionalPropertiesSchema = schema;
            return this;
        }

        // --- 通用约束 ---

        public Builder enumValues(Object... values) {
            this.enumValues = List.of(values);
            return this;
        }

        public Builder constValue(Object value) {
            this.constValue = value;
            return this;
        }

        public Builder allOf(JsonSchema... schemas) {
            this.allOf = List.of(schemas);
            return this;
        }

        public Builder anyOf(JsonSchema... schemas) {
            this.anyOf = List.of(schemas);
            return this;
        }

        public Builder oneOf(JsonSchema... schemas) {
            this.oneOf = List.of(schemas);
            return this;
        }

        public Builder not(JsonSchema schema) {
            this.not = schema;
            return this;
        }
    }

    // -------- 字段 --------

    private final String type;
    private final Integer minLength;
    private final Integer maxLength;
    private final Pattern pattern;
    private final String format;
    private final Number minimum;
    private final Number maximum;
    private final Number multipleOf;
    private final Boolean exclusiveMinimum;
    private final Boolean exclusiveMaximum;
    private final Integer minItems;
    private final Integer maxItems;
    private final Boolean uniqueItems;
    private final JsonSchema items;
    private final Integer minProperties;
    private final Integer maxProperties;
    private final List<String> required;
    private final Map<String, JsonSchema> properties;
    private final Map<String, JsonSchema> patternProperties;
    private final Boolean additionalProperties;
    private final JsonSchema additionalPropertiesSchema;
    private final List<Object> enumValues;
    private final Object constValue;
    private final List<JsonSchema> allOf;
    private final List<JsonSchema> anyOf;
    private final List<JsonSchema> oneOf;
    private final JsonSchema not;

    private JsonSchema(Builder b) {
        this.type = b.type;
        this.minLength = b.minLength;
        this.maxLength = b.maxLength;
        this.pattern = b.pattern;
        this.format = b.format;
        this.minimum = b.minimum;
        this.maximum = b.maximum;
        this.multipleOf = b.multipleOf;
        this.exclusiveMinimum = b.exclusiveMinimum;
        this.exclusiveMaximum = b.exclusiveMaximum;
        this.minItems = b.minItems;
        this.maxItems = b.maxItems;
        this.uniqueItems = b.uniqueItems;
        this.items = b.items;
        this.minProperties = b.minProperties;
        this.maxProperties = b.maxProperties;
        this.required = b.required != null ? List.copyOf(b.required) : null;
        this.properties = b.properties != null ? Map.copyOf(b.properties) : null;
        this.patternProperties = b.patternProperties != null ? Map.copyOf(b.patternProperties) : null;
        this.additionalProperties = b.additionalProperties;
        this.additionalPropertiesSchema = b.additionalPropertiesSchema;
        this.enumValues = b.enumValues != null ? List.copyOf(b.enumValues) : null;
        this.constValue = b.constValue;
        this.allOf = b.allOf != null ? List.copyOf(b.allOf) : null;
        this.anyOf = b.anyOf != null ? List.copyOf(b.anyOf) : null;
        this.oneOf = b.oneOf != null ? List.copyOf(b.oneOf) : null;
        this.not = b.not;
    }

    // -------- Getters --------

    public String getType() {
        return type;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getFormat() {
        return format;
    }

    public Number getMinimum() {
        return minimum;
    }

    public Number getMaximum() {
        return maximum;
    }

    public Number getMultipleOf() {
        return multipleOf;
    }

    public Boolean getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Boolean getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public Boolean getUniqueItems() {
        return uniqueItems;
    }

    public JsonSchema getItems() {
        return items;
    }

    public Integer getMinProperties() {
        return minProperties;
    }

    public Integer getMaxProperties() {
        return maxProperties;
    }

    public List<String> getRequired() {
        return required;
    }

    public Map<String, JsonSchema> getProperties() {
        return properties;
    }

    public Boolean getAdditionalProperties() {
        return additionalProperties;
    }

    public JsonSchema getAdditionalPropertiesSchema() {
        return additionalPropertiesSchema;
    }

    public List<Object> getEnumValues() {
        return enumValues;
    }

    public Object getConstValue() {
        return constValue;
    }

    public List<JsonSchema> getAllOf() {
        return allOf;
    }

    public List<JsonSchema> getAnyOf() {
        return anyOf;
    }

    public List<JsonSchema> getOneOf() {
        return oneOf;
    }

    public JsonSchema getNot() {
        return not;
    }
}
