package com.njydsz.common.json.schema;

import java.util.*;

import com.njydsz.common.json.annotation.Experimental;

/**
 * JSON Schema 定义（参考 JSON Schema 规范）
 *
 * <p>用于定义 JSON 数据的结构和约束条件。</p>
 *
 * <p><b>支持的类型：</b></p>
 * <ul>
 *   <li>string - 字符串</li>
 *   <li>number - 数字</li>
 *   <li>integer - 整数</li>
 *   <li>boolean - 布尔</li>
 *   <li>array - 数组</li>
 *   <li>object - 对象</li>
 *   <li>null - 空值</li>
 * </ul>
 *
 * <p><b>使用示例：</b></p>
 * <pre>
 * JsonSchema schema = new JsonSchema("object");
 * schema.addProperty("name", JsonSchema.string());
 * schema.addRequired("name");
 *
 * ValidationResult result = schema.validate(json);
 * if (!result.isValid()) {
 *     System.err.println(result.getErrors());
 * }
 * </pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Experimental("JSON Schema 定义属于非核心 RFC 扩展，后续可能独立为单独模块")
public final class JsonSchema {

    /** Schema 类型 */
    private final String type;

    /** 描述 */
    private String description;

    /** 是否必填 */
    private boolean required = false;

    /** 枚举值 */
    private List<Object> enumValues;

    /** 默认值 */
    private Object defaultValue;

    /** 字符串约束 */
    private Integer minLength;
    private Integer maxLength;
    private String pattern;

    /** 数字约束 */
    private Double minimum;
    private Double maximum;
    private Double exclusiveMinimum;
    private Double exclusiveMaximum;
    private Double multipleOf;

    /** 数组约束 */
    private JsonSchema items;
    private Integer minItems;
    private Integer maxItems;

    /** 对象约束 */
    private Map<String, JsonSchema> properties;
    private List<String> requiredProperties;
    private JsonSchema additionalProperties;

    /** 组合关键字（JSON Schema Draft 07 allOf/anyOf/oneOf） */
    private List<JsonSchema> allOf;
    private List<JsonSchema> anyOf;
    private List<JsonSchema> oneOf;

    /** not 关键字：数据不能匹配此 Schema */
    private JsonSchema not;

    /** const 关键字：数据必须等于此固定值 */
    private Object constValue;

    /** if/then/else 条件关键字（JSON Schema Draft 07） */
    private JsonSchema ifSchema;
    private JsonSchema thenSchema;
    private JsonSchema elseSchema;

    /**
     * 构造函数
     */
    public JsonSchema(String type) {
        this.type = type;
        this.properties = new LinkedHashMap<>();
        this.requiredProperties = new ArrayList<>();
    }

    // ========== 静态工厂方法 ==========

    /**
     * 字符串类型
     */
    public static JsonSchema string() {
        return new JsonSchema("string");
    }

    /**
     * 数字类型
     */
    public static JsonSchema number() {
        return new JsonSchema("number");
    }

    /**
     * 整数类型
     */
    public static JsonSchema integer() {
        return new JsonSchema("integer");
    }

    /**
     * 布尔类型
     */
    public static JsonSchema booleanType() {
        return new JsonSchema("boolean");
    }

    /**
     * 布尔类型（简化方法名）
     */
    public static JsonSchema boolean_() {
        return new JsonSchema("boolean");
    }

    /**
     * 数组类型
     */
    public static JsonSchema array() {
        return new JsonSchema("array");
    }

    /**
     * 对象类型
     */
    public static JsonSchema object() {
        return new JsonSchema("object");
    }

    // ========== 链式配置方法 ==========

    /**
     * 设置描述
     */
    public JsonSchema description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 设置为必填
     */
    public JsonSchema required() {
        this.required = true;
        return this;
    }

    /**
     * 设置枚举值
     */
    public JsonSchema enumValues(Object... values) {
        this.enumValues = Arrays.asList(values);
        return this;
    }

    /**
     * 添加单个枚举值
     */
    public JsonSchema addEnum(Object value) {
        if (this.enumValues == null) {
            this.enumValues = new ArrayList<>();
        }
        this.enumValues.add(value);
        return this;
    }

    /**
     * 设置默认值
     */
    public JsonSchema defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * 设置字符串最小长度
     */
    public JsonSchema minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    /**
     * 设置字符串最大长度
     */
    public JsonSchema maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /**
     * 设置字符串正则表达式
     */
    public JsonSchema pattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * 设置数字最小值
     */
    public JsonSchema minimum(double minimum) {
        this.minimum = minimum;
        return this;
    }

    /**
     * 设置数字最大值
     */
    public JsonSchema maximum(double maximum) {
        this.maximum = maximum;
        return this;
    }

    /**
     * 添加属性（对象类型）
     */
    public JsonSchema addProperty(String name, JsonSchema schema) {
        this.properties.put(name, schema);
        return this;
    }

    /**
     * 添加必填属性
     */
    public JsonSchema addRequired(String name) {
        this.requiredProperties.add(name);
        return this;
    }

    /**
     * 设置数组项类型
     */
    public JsonSchema items(JsonSchema items) {
        this.items = items;
        return this;
    }

    /**
     * 设置数组项数最小值
     */
    public JsonSchema minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    /**
     * 设置数组项数最大值
     */
    public JsonSchema maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    /**
     * 设置倍数约束
     */
    public JsonSchema multipleOf(double multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    /**
     * 设置排除性最小值
     */
    public JsonSchema exclusiveMinimum(double exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    /**
     * 设置排除性最大值
     */
    public JsonSchema exclusiveMaximum(double exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

    /**
     * 设置额外属性 Schema
     */
    public JsonSchema additionalProperties(JsonSchema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    // ========== 组合关键字（allOf / anyOf / oneOf） ==========

    /**
     * 添加 allOf 约束（所有 Schema 都必须验证通过）。
     *
     * @param schemas 需要全部满足的 Schema 列表
     * @return 当前 Schema（链式调用）
     * @since 1.4.0
     */
    public JsonSchema allOf(JsonSchema... schemas) {
        if (this.allOf == null) {
            this.allOf = new ArrayList<>();
        }
        this.allOf.addAll(Arrays.asList(schemas));
        return this;
    }

    /**
     * 添加 anyOf 约束（至少一个 Schema 验证通过）。
     *
     * @param schemas 需要满足任一条件的 Schema 列表
     * @return 当前 Schema（链式调用）
     * @since 1.4.0
     */
    public JsonSchema anyOf(JsonSchema... schemas) {
        if (this.anyOf == null) {
            this.anyOf = new ArrayList<>();
        }
        this.anyOf.addAll(Arrays.asList(schemas));
        return this;
    }

    /**
     * 添加 oneOf 约束（恰好一个 Schema 验证通过）。
     *
     * @param schemas 需要满足唯一条件的 Schema 列表
     * @return 当前 Schema（链式调用）
     * @since 1.4.0
     */
    public JsonSchema oneOf(JsonSchema... schemas) {
        if (this.oneOf == null) {
            this.oneOf = new ArrayList<>();
        }
        this.oneOf.addAll(Arrays.asList(schemas));
        return this;
    }

    /**
     * Null 类型
     */
    public static JsonSchema nullType() {
        return new JsonSchema("null");
    }

    /**
     * 设置 not 约束（数据不能匹配此 Schema）。
     *
     * @param not 不允许匹配的 Schema
     * @return 当前 Schema（链式调用）
     * @since 1.4.0
     */
    public JsonSchema not(JsonSchema not) {
        this.not = not;
        return this;
    }

    /**
     * 设置 const 约束（数据必须等于此固定值）。
     *
     * @param constValue 固定值
     * @return 当前 Schema（链式调用）
     * @since 1.4.0
     */
    public JsonSchema constValue(Object constValue) {
        this.constValue = constValue;
        return this;
    }

    /**
     * 设置 if/then/else 条件约束。
     *
     * <p>如果数据匹配 if Schema，则必须匹配 then Schema；
     * 如果不匹配 if Schema，则必须匹配 else Schema。</p>
     *
     * @param ifSchema 条件 Schema
     * @param thenSchema 满足条件时必须匹配的 Schema
     * @param elseSchema 不满足条件时必须匹配的 Schema（可为 null）
     * @return 当前 Schema（链式调用）
     * @since 1.4.0
     */
    public JsonSchema ifThenElse(JsonSchema ifSchema, JsonSchema thenSchema, JsonSchema elseSchema) {
        this.ifSchema = ifSchema;
        this.thenSchema = thenSchema;
        this.elseSchema = elseSchema;
        return this;
    }

    /**
     * 添加多个必填属性
     */
    public JsonSchema addRequired(String... names) {
        for (String name : names) {
            this.requiredProperties.add(name);
        }
        return this;
    }

    // ========== Getter 方法 ==========

    public String getType() {
        return type;
    }

    public String getDescription() {
        return description;
    }

    public boolean isRequired() {
        return required;
    }

    public List<Object> getEnumValues() {
        return enumValues;
    }

    public Object getDefaultValue() {
        return defaultValue;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public String getPattern() {
        return pattern;
    }

    public Double getMinimum() {
        return minimum;
    }

    public Double getMaximum() {
        return maximum;
    }

    public Map<String, JsonSchema> getProperties() {
        return properties;
    }

    public List<String> getRequiredProperties() {
        return requiredProperties;
    }

    public JsonSchema getItems() {
        return items;
    }

    public Integer getMinItems() {
        return minItems;
    }

    public Integer getMaxItems() {
        return maxItems;
    }

    public Double getMultipleOf() {
        return multipleOf;
    }

    public Double getExclusiveMinimum() {
        return exclusiveMinimum;
    }

    public Double getExclusiveMaximum() {
        return exclusiveMaximum;
    }

    public JsonSchema getAdditionalProperties() {
        return additionalProperties;
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

    public Object getConstValue() {
        return constValue;
    }

    public JsonSchema getIfSchema() {
        return ifSchema;
    }

    public JsonSchema getThenSchema() {
        return thenSchema;
    }

    public JsonSchema getElseSchema() {
        return elseSchema;
    }

    @Override
    public String toString() {
        return "JsonSchema{type='" + type + "', required=" + required + "}";
    }
}
