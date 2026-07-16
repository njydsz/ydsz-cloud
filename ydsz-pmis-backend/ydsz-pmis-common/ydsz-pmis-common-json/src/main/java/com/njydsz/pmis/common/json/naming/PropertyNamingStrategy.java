package com.njydsz.pmis.common.json.naming;

import java.io.Serializable;

/**
 * 命名策略接口（参考 Jackson 的 PropertyNamingStrategy）
 * 
 * <p>用于转换 Java 属性名到 JSON 字段名。</p>
 * 
 * <p><b>内置策略：</b></p>
 * <ul>
 *   <li>LOWER_CAMEL_CASE - 小驼峰（默认）</li>
 *   <li>LOWER_CASE - 全小写</li>
 *   <li>LOWER_CASE_WITH_UNDERSCORES - 下划线分隔</li>
 *   <li>LOWER_CASE_WITH_DASHES - 短横线分隔</li>
 *   <li>UPPER_CAMEL_CASE - 大驼峰</li>
 * </ul>
 * 
 * <p><b>使用示例：</b></p>
 * <pre>
 * // 使用下划线命名策略
 * JsonConfig.getInstance().setNamingStrategy(
 *     PropertyNamingStrategy.SNAKE_CASE
 * );
 * 
 * // 序列化结果：{"user_name":"John"}
 * User user = new User();
 * user.setUserName("John");
 * String json = Json.toJson(user);
 * </pre>
 * 
 * @since 1.0.0
 */
public interface PropertyNamingStrategy extends Serializable {
    
    /**
     * 转换属性名为 JSON 字段名
     * 
     * @param propertyName 属性名
     * @return JSON 字段名
     */
    String translate(String propertyName);
    
    /**
     * 小驼峰命名（默认）
     */
    PropertyNamingStrategy LOWER_CAMEL_CASE = propertyName -> propertyName;
    
    /**
     * 全小写命名
     */
    PropertyNamingStrategy LOWER_CASE = propertyName -> propertyName.toLowerCase();
    
    /**
     * 下划线命名（SNAKE_CASE）
     */
    PropertyNamingStrategy SNAKE_CASE = propertyName -> {
        if (propertyName == null || propertyName.isEmpty()) {
            return propertyName;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('_');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    };
    
    /**
     * 短横线命名（KEBAB-CASE）
     */
    PropertyNamingStrategy KEBAB_CASE = propertyName -> {
        if (propertyName == null || propertyName.isEmpty()) {
            return propertyName;
        }
        
        StringBuilder result = new StringBuilder();
        for (int i = 0; i < propertyName.length(); i++) {
            char c = propertyName.charAt(i);
            if (Character.isUpperCase(c)) {
                if (i > 0) {
                    result.append('-');
                }
                result.append(Character.toLowerCase(c));
            } else {
                result.append(c);
            }
        }
        return result.toString();
    };
    
    /**
     * 大驼峰命名（PascalCase）
     */
    PropertyNamingStrategy UPPER_CAMEL_CASE = propertyName -> {
        if (propertyName == null || propertyName.isEmpty()) {
            return propertyName;
        }
        return Character.toUpperCase(propertyName.charAt(0)) + propertyName.substring(1);
    };
}
