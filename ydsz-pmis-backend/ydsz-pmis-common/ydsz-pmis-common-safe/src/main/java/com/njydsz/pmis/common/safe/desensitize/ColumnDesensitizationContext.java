package com.njydsz.pmis.common.safe.desensitize;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import lombok.Getter;

/**
 * 脱敏上下文。
 *
 * <p>存储和管理字段脱敏规则配置。
 *
 * <p><b>数据结构：</b>
 * <ul>
 *   <li>desensitizeRulesByTable：表名 → (字段名 → 脱敏规则) 的映射</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * ColumnDesensitizationContext context = new ColumnDesensitizationContext();
 * context.addRule("sys_user", "phone", ColumnDesensitizationRule.PHONE);
 * context.addRule("sys_user", "email", ColumnDesensitizationRule.EMAIL);
 *
 * ColumnDesensitizationRule rule = context.getRule("sys_user", "phone");
 * String masked = rule.desensitize("13812345678"); // "138****5678"
 * }</pre>
 *
 * @since 1.0.0
 * 
 * @see ColumnDesensitizationRule
 */
@Getter
public class ColumnDesensitizationContext {

    private final Map<String, Map<String, DesensitizationRuleConfig>> desensitizeRulesByTable = new ConcurrentHashMap<>();

    public ColumnDesensitizationContext() {
    }

    public void addRule(String tableName, String columnName, ColumnDesensitizationRule rule) {
        addRule(tableName, columnName, rule, null, null);
    }

    public void addRule(String tableName, String columnName, ColumnDesensitizationRule rule,
                       String customPattern, String customReplacement) {
        if (tableName == null || columnName == null) {
            return;
        }
        String table = normalize(tableName);
        String column = normalize(columnName);

        DesensitizationRuleConfig config;
        if (rule == ColumnDesensitizationRule.CUSTOM) {
            config = new DesensitizationRuleConfig(rule, customPattern, customReplacement);
        } else {
            config = new DesensitizationRuleConfig(rule);
        }

        desensitizeRulesByTable.computeIfAbsent(table, k -> new ConcurrentHashMap<>())
                .put(column, config);
    }

    public void addRule(String tableName, String columnName, DesensitizationRuleConfig config) {
        if (tableName == null || columnName == null || config == null) {
            return;
        }
        String table = normalize(tableName);
        String column = normalize(columnName);
        desensitizeRulesByTable.computeIfAbsent(table, k -> new ConcurrentHashMap<>())
                .put(column, config);
    }

    public DesensitizationRuleConfig getRule(String tableName, String columnName) {
        if (tableName == null || columnName == null) {
            return null;
        }
        String table = normalize(tableName);
        String column = normalize(columnName);
        Map<String, DesensitizationRuleConfig> tableRules = desensitizeRulesByTable.get(table);
        if (tableRules == null) {
            return null;
        }
        return tableRules.get(column);
    }

    public boolean hasRule(String tableName, String columnName) {
        return getRule(tableName, columnName) != null;
    }

    public Set<String> getAllTables() {
        return desensitizeRulesByTable.keySet();
    }

    public Set<String> getColumns(String tableName) {
        if (tableName == null) {
            return Collections.emptySet();
        }
        String table = normalize(tableName);
        Map<String, DesensitizationRuleConfig> tableRules = desensitizeRulesByTable.get(table);
        if (tableRules == null) {
            return Collections.emptySet();
        }
        return tableRules.keySet();
    }

    public boolean isEmpty() {
        return desensitizeRulesByTable.isEmpty();
    }

    public void clear() {
        desensitizeRulesByTable.clear();
    }

    public void removeTable(String tableName) {
        if (tableName != null) {
            desensitizeRulesByTable.remove(normalize(tableName));
        }
    }

    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        return value.trim().toLowerCase();
    }

    public static ColumnDesensitizationContext empty() {
        return new ColumnDesensitizationContext();
    }

    @Getter
    public static class DesensitizationRuleConfig {
        private final ColumnDesensitizationRule rule;
        private final String customPattern;
        private final String customReplacement;

        public DesensitizationRuleConfig(ColumnDesensitizationRule rule) {
            this(rule, null, null);
        }

        public DesensitizationRuleConfig(ColumnDesensitizationRule rule, String customPattern, String customReplacement) {
            this.rule = rule;
            this.customPattern = customPattern;
            this.customReplacement = customReplacement;
        }

        public boolean isCustom() {
            return rule == ColumnDesensitizationRule.CUSTOM;
        }

        public String getPattern() {
            return isCustom() ? customPattern : rule.getPattern();
        }

        public String getReplacement() {
            return isCustom() ? customReplacement : rule.getReplacement();
        }

        @Override
        public String toString() {
            return "DesensitizationRuleConfig{" +
                    "rule=" + rule +
                    ", customPattern='" + customPattern + '\'' +
                    ", customReplacement='" + customReplacement + '\'' +
                    '}';
        }
    }
}
