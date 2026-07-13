package com.njydsz.pmis.common.json.schema;

import java.util.*;

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
 * YdszJsonSchema schema = new YdszJsonSchema("object");
 * schema.addProperty("name", YdszJsonSchema.string());
 * schema.addRequired("name");
 *
 * ValidationResult result = schema.validate(json);
 * if (!result.isValid()) {
 *     System.err.println(result.getErrors());
 * }
 * </pre>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public final class YdszJsonSchema {

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
    private YdszJsonSchema items;
    private Integer minItems;
    private Integer maxItems;

    /** 对象约束 */
    private Map<String, YdszJsonSchema> properties;
    private List<String> requiredProperties;
    private YdszJsonSchema additionalProperties;

    /**
     * 构造函数
     */
    public YdszJsonSchema(String type) {
        this.type = type;
        this.properties = new LinkedHashMap<>();
        this.requiredProperties = new ArrayList<>();
    }

    // ========== 静态工厂方法 ==========

    /**
     * 字符串类型
     */
    public static YdszJsonSchema string() {
        return new YdszJsonSchema("string");
    }

    /**
     * 数字类型
     */
    public static YdszJsonSchema number() {
        return new YdszJsonSchema("number");
    }

    /**
     * 整数类型
     */
    public static YdszJsonSchema integer() {
        return new YdszJsonSchema("integer");
    }

    /**
     * 布尔类型
     */
    public static YdszJsonSchema booleanType() {
        return new YdszJsonSchema("boolean");
    }

    /**
     * 布尔类型（简化方法名）
     */
    public static YdszJsonSchema boolean_() {
        return new YdszJsonSchema("boolean");
    }

    /**
     * 数组类型
     */
    public static YdszJsonSchema array() {
        return new YdszJsonSchema("array");
    }

    /**
     * 对象类型
     */
    public static YdszJsonSchema object() {
        return new YdszJsonSchema("object");
    }

    // ========== 链式配置方法 ==========

    /**
     * 设置描述
     */
    public YdszJsonSchema description(String description) {
        this.description = description;
        return this;
    }

    /**
     * 设置为必填
     */
    public YdszJsonSchema required() {
        this.required = true;
        return this;
    }

    /**
     * 设置枚举值
     */
    public YdszJsonSchema enumValues(Object... values) {
        this.enumValues = Arrays.asList(values);
        return this;
    }

    /**
     * 添加单个枚举值
     */
    public YdszJsonSchema addEnum(Object value) {
        if (this.enumValues == null) {
            this.enumValues = new ArrayList<>();
        }
        this.enumValues.add(value);
        return this;
    }

    /**
     * 设置默认值
     */
    public YdszJsonSchema defaultValue(Object defaultValue) {
        this.defaultValue = defaultValue;
        return this;
    }

    /**
     * 设置字符串最小长度
     */
    public YdszJsonSchema minLength(int minLength) {
        this.minLength = minLength;
        return this;
    }

    /**
     * 设置字符串最大长度
     */
    public YdszJsonSchema maxLength(int maxLength) {
        this.maxLength = maxLength;
        return this;
    }

    /**
     * 设置字符串正则表达式
     */
    public YdszJsonSchema pattern(String pattern) {
        this.pattern = pattern;
        return this;
    }

    /**
     * 设置数字最小值
     */
    public YdszJsonSchema minimum(double minimum) {
        this.minimum = minimum;
        return this;
    }

    /**
     * 设置数字最大值
     */
    public YdszJsonSchema maximum(double maximum) {
        this.maximum = maximum;
        return this;
    }

    /**
     * 添加属性（对象类型）
     */
    public YdszJsonSchema addProperty(String name, YdszJsonSchema schema) {
        this.properties.put(name, schema);
        return this;
    }

    /**
     * 添加必填属性
     */
    public YdszJsonSchema addRequired(String name) {
        this.requiredProperties.add(name);
        return this;
    }

    /**
     * 设置数组项类型
     */
    public YdszJsonSchema items(YdszJsonSchema items) {
        this.items = items;
        return this;
    }

    /**
     * 设置数组项数最小值
     */
    public YdszJsonSchema minItems(int minItems) {
        this.minItems = minItems;
        return this;
    }

    /**
     * 设置数组项数最大值
     */
    public YdszJsonSchema maxItems(int maxItems) {
        this.maxItems = maxItems;
        return this;
    }

    /**
     * 设置倍数约束
     */
    public YdszJsonSchema multipleOf(double multipleOf) {
        this.multipleOf = multipleOf;
        return this;
    }

    /**
     * 设置排除性最小值
     */
    public YdszJsonSchema exclusiveMinimum(double exclusiveMinimum) {
        this.exclusiveMinimum = exclusiveMinimum;
        return this;
    }

    /**
     * 设置排除性最大值
     */
    public YdszJsonSchema exclusiveMaximum(double exclusiveMaximum) {
        this.exclusiveMaximum = exclusiveMaximum;
        return this;
    }

    /**
     * 设置额外属性 Schema
     */
    public YdszJsonSchema additionalProperties(YdszJsonSchema additionalProperties) {
        this.additionalProperties = additionalProperties;
        return this;
    }

    /**
     * Null 类型
     */
    public static YdszJsonSchema nullType() {
        return new YdszJsonSchema("null");
    }

    /**
     * 添加多个必填属性
     */
    public YdszJsonSchema addRequired(String... names) {
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

    public Map<String, YdszJsonSchema> getProperties() {
        return properties;
    }

    public List<String> getRequiredProperties() {
        return requiredProperties;
    }

    public YdszJsonSchema getItems() {
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

    public YdszJsonSchema getAdditionalProperties() {
        return additionalProperties;
    }

    @Override
    public String toString() {
        return "YdszJsonSchema{type='" + type + "', required=" + required + "}";
    }
}
