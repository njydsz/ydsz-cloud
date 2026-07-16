package com.njydsz.pmis.common.notify.template;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import com.njydsz.pmis.common.notify.exception.NotifyException;

/**
 * 模板变量校验器（P3-2）
 *
 * <p>校验模板渲染时传入的变量是否满足模板定义的要求：
 * <ul>
 *   <li>必填变量是否存在</li>
 *   <li>变量值类型是否匹配</li>
 *   <li>变量值格式是否合法（正则校验）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public class TemplateVariableValidator {

    /** 变量类型枚举 */
    public enum VariableType {
        STRING, INTEGER, DECIMAL, BOOLEAN, EMAIL, PHONE, URL, DATETIME
    }

    /**
     * 模板变量定义
     */
    public static class VariableDefinition {

        private final String name;
        private final VariableType type;
        private final boolean required;
        private final String regex;
        private final String description;

        /**
         * 构造变量定义
         *
         * @param name        变量名
         * @param type        变量类型
         * @param required    是否必填
         * @param regex       正则校验（可为 null）
         * @param description 变量描述
         */
        public VariableDefinition(String name, VariableType type, boolean required,
                                  String regex, String description) {
            this.name = name;
            this.type = type != null ? type : VariableType.STRING;
            this.required = required;
            this.regex = regex;
            this.description = description;
        }

        public String getName() { return name; }
        public VariableType getType() { return type; }
        public boolean isRequired() { return required; }
        public String getRegex() { return regex; }
        public String getDescription() { return description; }
    }

    /**
     * 校验模板参数
     *
     * @param template 模板定义
     * @param params   模板参数
     * @throws NotifyException 校验失败时抛出
     */
    public void validate(NotifyTemplate template, Map<String, Object> params) {
        if (template == null) {
            return;
        }

        Map<String, String> variableDefs = template.getVariables();
        if (variableDefs == null || variableDefs.isEmpty()) {
            return;
        }

        Set<String> checkedVars = new HashSet<>();

        for (Map.Entry<String, String> entry : variableDefs.entrySet()) {
            String varName = entry.getKey();
            checkedVars.add(varName);

            Object value = params != null ? params.get(varName) : null;

            if (value == null) {
                // 检查模板内容中是否引用了此变量
                if (isVariableReferenced(template.getContent(), varName)) {
                    throw new NotifyException("模板变量[" + varName + "]未提供值，模板: " + template.getTemplateId());
                }
                continue;
            }

            String strValue = String.valueOf(value);
            if (strValue.isEmpty() && isVariableReferenced(template.getContent(), varName)) {
                throw new NotifyException("模板变量[" + varName + "]值为空，模板: " + template.getTemplateId());
            }
        }
    }

    /**
     * 判断模板内容中是否引用了指定变量
     *
     * @param content 模板内容
     * @param varName 变量名
     * @return true 表示模板内容中引用了此变量
     */
    private boolean isVariableReferenced(String content, String varName) {
        if (content == null || content.isEmpty() || varName == null) {
            return false;
        }
        // 检查 #{varName} 格式（SpEL）
        if (content.contains("#{" + varName + "}")) {
            return true;
        }
        // 检查 ${varName} 格式（Velocity）
        if (content.contains("${" + varName + "}")) {
            return true;
        }
        // 检查 {varName} 格式（MessageFormat）
        if (content.contains("{" + varName + "}")) {
            return true;
        }
        return false;
    }

    /**
     * 校验变量值类型
     *
     * @param varName 变量名
     * @param value   变量值
     * @param type    期望类型
     * @throws NotifyException 类型不匹配时抛出
     */
    public void validateType(String varName, Object value, VariableType type) {
        if (value == null || type == VariableType.STRING) {
            return;
        }

        String strValue = String.valueOf(value);

        switch (type) {
            case INTEGER:
                try {
                    Integer.parseInt(strValue);
                } catch (NumberFormatException e) {
                    throw new NotifyException("变量[" + varName + "]期望整数类型，实际值: " + strValue);
                }
                break;
            case DECIMAL:
                try {
                    Double.parseDouble(strValue);
                } catch (NumberFormatException e) {
                    throw new NotifyException("变量[" + varName + "]期望数值类型，实际值: " + strValue);
                }
                break;
            case BOOLEAN:
                if (!"true".equalsIgnoreCase(strValue) && !"false".equalsIgnoreCase(strValue)) {
                    throw new NotifyException("变量[" + varName + "]期望布尔类型，实际值: " + strValue);
                }
                break;
            case EMAIL:
                if (!Pattern.matches("^[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}$", strValue)) {
                    throw new NotifyException("变量[" + varName + "]期望邮箱格式，实际值: " + strValue);
                }
                break;
            case PHONE:
                if (!Pattern.matches("^1[3-9]\\d{9}$", strValue)) {
                    throw new NotifyException("变量[" + varName + "]期望手机号格式，实际值: " + strValue);
                }
                break;
            case URL:
                if (!strValue.startsWith("http://") && !strValue.startsWith("https://")) {
                    throw new NotifyException("变量[" + varName + "]期望 URL 格式，实际值: " + strValue);
                }
                break;
            default:
                break;
        }
    }
}
