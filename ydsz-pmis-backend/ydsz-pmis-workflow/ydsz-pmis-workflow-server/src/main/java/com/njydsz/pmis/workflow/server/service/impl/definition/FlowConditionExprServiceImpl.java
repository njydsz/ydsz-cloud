package com.njydsz.pmis.workflow.server.service.impl.definition;

import com.njydsz.pmis.common.util.json.JsonUtils;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.infra.mapper.definition.FlowNodeMapper;
import com.njydsz.pmis.workflow.server.service.FlowConditionExprService;
import com.googlecode.aviator.AviatorEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 条件表达式可视化编辑器服务实现（P2-1）。
 *
 * <p>将前端结构化条件 JSON ↔ 表达式字符串双向转换。
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowConditionExprServiceImpl implements FlowConditionExprService {

    private final FlowNodeMapper nodeMapper;

    /** 操作符映射：枚举 → Aviator / SpEL 符号 */
    private static final Map<String, String[]> OPERATOR_MAP = new LinkedHashMap<>();

    static {
        OPERATOR_MAP.put("EQ",   new String[]{"==", "=="});
        OPERATOR_MAP.put("NE",   new String[]{"!=", "!="});
        OPERATOR_MAP.put("GT",   new String[]{">",  ">"});
        OPERATOR_MAP.put("GTE",  new String[]{">=", ">="});
        OPERATOR_MAP.put("LT",   new String[]{"<",  "<"});
        OPERATOR_MAP.put("LTE",  new String[]{"<=", "<="});
        OPERATOR_MAP.put("IN",   new String[]{"seq.in", "T(String).valueOf(#field).matches"});
        OPERATOR_MAP.put("NOT_IN", new String[]{"!seq.in", "!"});
        OPERATOR_MAP.put("CONTAINS", new String[]{"string.contains", "contains"});
        OPERATOR_MAP.put("STARTS_WITH", new String[]{"string.startsWith", "startsWith"});
        OPERATOR_MAP.put("ENDS_WITH", new String[]{"string.endsWith", "endsWith"});
        OPERATOR_MAP.put("IS_NULL", new String[]{"==nil", "== null"});
        OPERATOR_MAP.put("NOT_NULL", new String[]{"!=nil", "!= null"});
        OPERATOR_MAP.put("IS_EMPTY", new String[]{"string.isEmpty", "empty"});
        OPERATOR_MAP.put("NOT_EMPTY", new String[]{"!string.isEmpty", "!empty"});
    }

    @Override
    public String buildExpression(String conditionJson, String engine) {
        if (conditionJson == null || conditionJson.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> root = JsonUtils.parseMap(conditionJson);
            if (root == null) {
                return "";
            }
            String logic = root.get("logic") == null ? "AND" : String.valueOf(root.get("logic"));
            String logicOp = "OR".equalsIgnoreCase(logic) ? " || " : " && ";
            int engineIdx = "SPEL".equalsIgnoreCase(engine) ? 1 : 0;

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> groups = (List<Map<String, Object>>) root.get("groups");
            if (groups == null || groups.isEmpty()) {
                return "";
            }

            List<String> parts = new ArrayList<>();
            for (Map<String, Object> group : groups) {
                String part = buildGroupExpr(group, engineIdx);
                if (part != null) {
                    parts.add(part);
                }
            }
            if (parts.isEmpty()) {
                return "";
            }
            if (parts.size() == 1) {
                return parts.get(0);
            }
            return String.join(logicOp, parts.stream().map(p -> "(" + p + ")").toList());
        } catch (Exception e) {
            log.warn("[CondExpr] 构建表达式失败: json={} err={}", conditionJson, e.getMessage());
            return "";
        }
    }

    @Override
    public String parseExpression(String expression, String engine) {
        // 反向解析：简单实现，仅支持 AND/OR 连接的基本比较表达式
        if (expression == null || expression.isBlank()) {
            return "{}";
        }
        try {
            Map<String, Object> result = new LinkedHashMap<>();
            String logic = "AND";
            String expr = expression.trim();

            // 判断逻辑运算符
            if (expr.contains("&&")) {
                logic = "AND";
            } else if (expr.contains("||")) {
                logic = "OR";
            }
            result.put("logic", logic);

            // 拆分条件项
            String separator = "AND".equals(logic) ? "&&" : "\\|\\|";
            String[] parts = expr.split(separator);
            List<Map<String, Object>> groups = new ArrayList<>();

            for (String part : parts) {
                Map<String, Object> group = parseSingleExpr(part.trim());
                if (group != null) {
                    groups.add(group);
                }
            }
            result.put("groups", groups);
            return JsonUtils.toJson(result);
        } catch (Exception e) {
            log.warn("[CondExpr] 解析表达式失败: expr={} err={}", expression, e.getMessage());
            return "{}";
        }
    }

    @Override
    public Map<String, Object> validateExpression(String expression, String engine) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (expression == null || expression.isBlank()) {
            result.put("valid", false);
            result.put("error", "表达式不能为空");
            return result;
        }
        try {
            // 简单语法校验：括号匹配
            int parenCount = 0;
            for (char c : expression.toCharArray()) {
                if (c == '(') parenCount++;
                if (c == ')') parenCount--;
                if (parenCount < 0) {
                    result.put("valid", false);
                    result.put("error", "括号不匹配");
                    return result;
                }
            }
            if (parenCount != 0) {
                result.put("valid", false);
                result.put("error", "括号不匹配");
                return result;
            }
            // 引号匹配
            long singleQuotes = expression.chars().filter(c -> c == '\'').count();
            long doubleQuotes = expression.chars().filter(c -> c == '"').count();
            if (singleQuotes % 2 != 0 || doubleQuotes % 2 != 0) {
                result.put("valid", false);
                result.put("error", "引号不匹配");
                return result;
            }
            result.put("valid", true);
            return result;
        } catch (Exception e) {
            result.put("valid", false);
            result.put("error", "校验异常: " + e.getMessage());
            return result;
        }
    }

    @Override
    public List<Map<String, String>> getOperators() {
        List<Map<String, String>> result = new ArrayList<>();
        for (Map.Entry<String, String[]> entry : OPERATOR_MAP.entrySet()) {
            Map<String, String> op = new LinkedHashMap<>();
            op.put("code", entry.getKey());
            op.put("aviator", entry.getValue()[0]);
            op.put("spel", entry.getValue()[1]);
            result.add(op);
        }
        return result;
    }

    @Override
    public List<Map<String, String>> getValueTypes() {
        return List.of(
                Map.of("code", "STRING", "name", "字符串"),
                Map.of("code", "NUMBER", "name", "数字"),
                Map.of("code", "BOOLEAN", "name", "布尔值"),
                Map.of("code", "DATE", "name", "日期"),
                Map.of("code", "DATETIME", "name", "日期时间"),
                Map.of("code", "LIST", "name", "列表"),
                Map.of("code", "NULL", "name", "空值")
        );
    }

    // ============================== 内部辅助 ==============================

    /**
     * 构建单个条件组的表达式
     */
    private String buildGroupExpr(Map<String, Object> group, int engineIdx) {
        String field = String.valueOf(group.get("field"));
        String operator = String.valueOf(group.get("operator")).toUpperCase();
        Object value = group.get("value");
        String valueType = group.get("valueType") == null ? "STRING" : String.valueOf(group.get("valueType")).toUpperCase();

        String[] symbols = OPERATOR_MAP.get(operator);
        if (symbols == null) {
            log.warn("[CondExpr] 未知操作符: {}", operator);
            return null;
        }
        String symbol = symbols[engineIdx];

        // NULL / EMPTY 类操作符不需要值
        if ("IS_NULL".equals(operator) || "NOT_NULL".equals(operator)
                || "IS_EMPTY".equals(operator) || "NOT_EMPTY".equals(operator)) {
            return field + " " + symbol;
        }

        // 格式化值
        String formattedValue = formatValue(value, valueType, engineIdx);

        // IN / NOT_IN 特殊处理
        if ("IN".equals(operator) || "NOT_IN".equals(operator)) {
            if (engineIdx == 0) {
                // Aviator: seq.in(list, value)
                return symbol + "(" + formattedValue + ", " + field + ")";
            } else {
                // SpEL: 简化处理
                return field + " " + ("IN".equals(operator) ? "in" : "not in") + " " + formattedValue;
            }
        }

        // CONTAINS / STARTS_WITH / ENDS_WITH 特殊处理
        if ("CONTAINS".equals(operator) || "STARTS_WITH".equals(operator) || "ENDS_WITH".equals(operator)) {
            if (engineIdx == 0) {
                // Aviator: string.contains(str, substr)
                return symbol + "(" + field + ", " + formattedValue + ")";
            } else {
                return field + "." + symbol + "(" + formattedValue + ")";
            }
        }

        return field + " " + symbol + " " + formattedValue;
    }

    /**
     * 格式化值
     */
    private String formatValue(Object value, String valueType, int engineIdx) {
        if (value == null) {
            return engineIdx == 0 ? "nil" : "null";
        }
        switch (valueType) {
            case "NUMBER":
                return String.valueOf(value);
            case "BOOLEAN":
                return String.valueOf(value).toLowerCase();
            case "STRING":
            case "DATE":
            case "DATETIME":
                return "'" + value + "'";
            case "LIST":
                @SuppressWarnings("unchecked")
                List<Object> list = (List<Object>) value;
                if (engineIdx == 0) {
                    // Aviator: seq.list(a, b, c)
                    StringBuilder sb = new StringBuilder("seq.list(");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(formatValue(list.get(i), "STRING", engineIdx));
                    }
                    sb.append(")");
                    return sb.toString();
                } else {
                    // SpEL: {a, b, c}
                    StringBuilder sb = new StringBuilder("{");
                    for (int i = 0; i < list.size(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(formatValue(list.get(i), "STRING", engineIdx));
                    }
                    sb.append("}");
                    return sb.toString();
                }
            default:
                return "'" + value + "'";
        }
    }

    /**
     * 解析单个表达式为条件组
     */
    private Map<String, Object> parseSingleExpr(String expr) {
        // 去掉外层括号
        while (expr.startsWith("(") && expr.endsWith(")")) {
            expr = expr.substring(1, expr.length() - 1).trim();
        }
        // 简单解析：field OP value
        for (Map.Entry<String, String[]> entry : OPERATOR_MAP.entrySet()) {
            String op = entry.getValue()[0]; // Aviator 符号
            int idx = expr.indexOf(" " + op + " ");
            if (idx > 0) {
                String field = expr.substring(0, idx).trim();
                String value = expr.substring(idx + op.length() + 2).trim();
                Map<String, Object> group = new LinkedHashMap<>();
                group.put("field", field);
                group.put("operator", entry.getKey());
                group.put("value", unquote(value));
                group.put("valueType", guessValueType(value));
                return group;
            }
        }
        return null;
    }

    /**
     * 去除字符串两端的引号
     */
    private String unquote(String s) {
        if (s == null) return null;
        if ((s.startsWith("'") && s.endsWith("'"))
                || (s.startsWith("\"") && s.endsWith("\""))) {
            return s.substring(1, s.length() - 1);
        }
        return s;
    }

    /**
     * 猜测值类型
     */
    private String guessValueType(String value) {
        if (value == null || value.isBlank()) return "NULL";
        if (value.matches("-?\\d+(\\.\\d+)?")) return "NUMBER";
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return "BOOLEAN";
        if (value.startsWith("'") || value.startsWith("\"")) return "STRING";
        return "STRING";
    }

    // ==================== P1-4: 可视化编辑增强实现 ====================

    @Override
    public List<Map<String, String>> getVariablesByDefinition(String definitionId) {
        if (definitionId == null || definitionId.isBlank()) {
            return List.of();
        }
        List<Map<String, String>> result = new ArrayList<>();

        // 1. 添加系统内置变量
        result.add(Map.of("fieldKey", "initiatorId", "label", "发起人ID", "fieldType", "STRING", "description", "流程发起人用户ID"));
        result.add(Map.of("fieldKey", "initiatorName", "label", "发起人姓名", "fieldType", "STRING", "description", "流程发起人姓名"));
        result.add(Map.of("fieldKey", "currentTime", "label", "当前时间", "fieldType", "DATETIME", "description", "系统当前时间"));
        result.add(Map.of("fieldKey", "currentUserId", "label", "当前审批人ID", "fieldType", "STRING", "description", "当前节点审批人用户ID"));
        result.add(Map.of("fieldKey", "currentUserName", "label", "当前审批人姓名", "fieldType", "STRING", "description", "当前节点审批人姓名"));

        // 2. 从流程定义的所有节点表单中提取变量
        try {
            List<FlowNodeDO> nodes = nodeMapper.selectByDefinitionId(definitionId);
            if (nodes != null && !nodes.isEmpty()) {
                for (FlowNodeDO node : nodes) {
                    extractVariablesFromNode(node, result);
                }
            }
        } catch (Exception e) {
            log.warn("[CondExpr] 提取流程变量失败: definitionId={} err={}", definitionId, e.getMessage());
        }

        return result;
    }

    @Override
    public Map<String, Object> previewExpression(String expression, Map<String, Object> variables, String engine) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (expression == null || expression.isBlank()) {
            result.put("result", false);
            result.put("error", "表达式不能为空");
            return result;
        }
        String eng = engine == null || engine.isBlank() ? "AVIATOR" : engine.toUpperCase();

        try {
            if ("AVIATOR".equals(eng)) {
                Object exprResult = AviatorEvaluator.execute(expression, variables != null ? variables : Map.of());
                result.put("result", Boolean.TRUE.equals(exprResult));
                result.put("error", null);
            } else if ("SPEL".equals(eng)) {
                result.put("result", null);
                result.put("error", "SpEL 预览功能暂未实现，请使用 Aviator 引擎");
            } else {
                result.put("result", null);
                result.put("error", "不支持的表达式引擎: " + eng);
            }
        } catch (Exception e) {
            log.warn("[CondExpr] 表达式预览失败: expr={} err={}", expression, e.getMessage());
            result.put("result", null);
            result.put("error", "执行异常: " + e.getMessage());
        }
        return result;
    }

    @Override
    public List<Map<String, String>> getConditionTemplates() {
        List<Map<String, String>> templates = new ArrayList<>();

        templates.add(buildTemplate("AMOUNT_GT", "金额大于指定值",
                "当申请金额超过设定值时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GT\",\"value\":10000,\"valueType\":\"NUMBER\"}]}"));

        templates.add(buildTemplate("AMOUNT_RANGE", "金额区间判断",
                "当申请金额在指定区间内时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GTE\",\"value\":1000,\"valueType\":\"NUMBER\"},{\"field\":\"amount\",\"operator\":\"LTE\",\"value\":50000,\"valueType\":\"NUMBER\"}]}"));

        templates.add(buildTemplate("DEPT_EQ", "部门匹配",
                "当申请人部门等于指定部门时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"deptCode\",\"operator\":\"EQ\",\"value\":\"SALES\",\"valueType\":\"STRING\"}]}"));

        templates.add(buildTemplate("DEPT_IN", "部门在列表中",
                "当申请人部门属于指定部门列表时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"deptCode\",\"operator\":\"IN\",\"value\":[\"SALES\",\"MARKETING\"],\"valueType\":\"LIST\"}]}"));

        templates.add(buildTemplate("LEVEL_GTE", "职级大于等于",
                "当申请人职级大于等于指定职级时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"level\",\"operator\":\"GTE\",\"value\":5,\"valueType\":\"NUMBER\"}]}"));

        templates.add(buildTemplate("DATE_GT", "日期大于指定日期",
                "当日期字段大于指定日期时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"applyDate\",\"operator\":\"GT\",\"value\":\"2024-01-01\",\"valueType\":\"DATE\"}]}"));

        templates.add(buildTemplate("INITIATOR_EQ", "申请人等于指定人",
                "当流程发起人等于指定人时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"initiatorId\",\"operator\":\"EQ\",\"value\":\"u001\",\"valueType\":\"STRING\"}]}"));

        templates.add(buildTemplate("COMBINE_AND", "多条件组合（AND）",
                "多个条件同时成立时条件匹配",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GT\",\"value\":10000,\"valueType\":\"NUMBER\"},{\"field\":\"level\",\"operator\":\"GTE\",\"value\":5,\"valueType\":\"NUMBER\"}]}"));

        templates.add(buildTemplate("COMBINE_OR", "多条件组合（OR）",
                "任一条件成立时条件匹配",
                "{\"logic\":\"OR\",\"groups\":[{\"field\":\"deptCode\",\"operator\":\"EQ\",\"value\":\"SALES\",\"valueType\":\"STRING\"},{\"field\":\"deptCode\",\"operator\":\"EQ\",\"value\":\"MARKETING\",\"valueType\":\"STRING\"}]}"));

        templates.add(buildTemplate("CONTAINS", "字段包含指定文本",
                "当文本字段包含指定文本时条件成立",
                "{\"logic\":\"AND\",\"groups\":[{\"field\":\"title\",\"operator\":\"CONTAINS\",\"value\":\"紧急\",\"valueType\":\"STRING\"}]}"));

        return templates;
    }

    // ==================== 内部辅助方法 ====================

    private void extractVariablesFromNode(FlowNodeDO node, List<Map<String, String>> result) {
        if (node == null) {
            return;
        }
        try {
            String ext = node.getExt();
            if (ext == null || ext.isBlank()) {
                return;
            }
            Map<String, Object> extMap = JsonUtils.parseMap(ext);
            if (extMap == null) {
                return;
            }
            Object formSchemaObj = extMap.get("formSchema");
            if (formSchemaObj == null) {
                return;
            }
            @SuppressWarnings("unchecked")
            Map<String, Object> schemaMap = (Map<String, Object>) formSchemaObj;
            List<Map<String, Object>> fieldsMap = (List<Map<String, Object>>) schemaMap.get("fields");
            if (fieldsMap == null || fieldsMap.isEmpty()) {
                return;
            }
            for (Map<String, Object> fieldMap : fieldsMap) {
                String fieldKey = (String) fieldMap.get("fieldKey");
                String label = (String) fieldMap.get("label");
                String fieldType = (String) fieldMap.get("fieldType");
                String placeholder = (String) fieldMap.get("placeholder");

                if (fieldKey != null && !fieldKey.isBlank()) {
                    Map<String, String> varInfo = new LinkedHashMap<>();
                    varInfo.put("fieldKey", fieldKey);
                    varInfo.put("label", label != null ? label : fieldKey);
                    varInfo.put("fieldType", fieldType != null ? fieldType : "STRING");
                    varInfo.put("description", placeholder != null ? placeholder : "");
                    varInfo.put("nodeCode", node.getNodeCode() != null ? node.getNodeCode() : "");
                    varInfo.put("nodeName", node.getNodeName() != null ? node.getNodeName() : "");
                    result.add(varInfo);
                }
                // 处理子表单字段
                List<Map<String, Object>> subFieldsMap = (List<Map<String, Object>>) fieldMap.get("subFields");
                if (subFieldsMap != null && !subFieldsMap.isEmpty()) {
                    for (Map<String, Object> subFieldMap : subFieldsMap) {
                        String subFieldKey = (String) subFieldMap.get("fieldKey");
                        String subLabel = (String) subFieldMap.get("label");
                        String subFieldType = (String) subFieldMap.get("fieldType");
                        if (subFieldKey != null && !subFieldKey.isBlank()) {
                            Map<String, String> varInfo = new LinkedHashMap<>();
                            varInfo.put("fieldKey", fieldKey + "." + subFieldKey);
                            varInfo.put("label", (label != null ? label : fieldKey) + " - " + (subLabel != null ? subLabel : subFieldKey));
                            varInfo.put("fieldType", subFieldType != null ? subFieldType : "STRING");
                            varInfo.put("description", "");
                            varInfo.put("nodeCode", node.getNodeCode() != null ? node.getNodeCode() : "");
                            varInfo.put("nodeName", node.getNodeName() != null ? node.getNodeName() : "");
                            result.add(varInfo);
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.warn("[CondExpr] 提取节点变量失败: nodeCode={} err={}", node.getNodeCode(), e.getMessage());
        }
    }

    private Map<String, String> buildTemplate(String id, String name, String description, String templateJson) {
        Map<String, String> template = new LinkedHashMap<>();
        template.put("id", id);
        template.put("name", name);
        template.put("description", description);
        template.put("templateJson", templateJson);
        return template;
    }
}
