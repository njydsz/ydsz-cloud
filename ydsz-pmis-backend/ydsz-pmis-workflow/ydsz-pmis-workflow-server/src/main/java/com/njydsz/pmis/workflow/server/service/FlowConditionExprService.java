package com.njydsz.pmis.workflow.server.service.definition;

import java.util.List;
import java.util.Map;

/**
 * 条件表达式可视化编辑器服务（P2-1）。
 *
 * <p>对标钉钉/飞书审批的"条件规则可视化编辑"能力，
 * 将前端结构化的条件组 JSON 转换为表达式引擎可执行的字符串（Aviator/SpEL），
 * 并提供反向解析和校验能力。
 *
 * <p>结构化条件 JSON 格式：
 * <pre>
 * {
 *   "logic": "AND",  // AND | OR
 *   "groups": [
 *     {
 *       "field": "amount",
 *       "operator": "GT",
 *       "value": 10000,
 *       "valueType": "NUMBER"
 *     },
 *     {
 *       "field": "deptCode",
 *       "operator": "EQ",
 *       "value": "SALES",
 *       "valueType": "STRING"
 *     }
 *   ]
 * }
 * </pre>
 *
 * @author ydsz-pmis-team
 * @since 1.8.0
 */
public interface FlowConditionExprService {

    /**
     * 将结构化条件 JSON 转换为表达式字符串。
     *
     * <p>支持 Aviator 和 SpEL 两种表达式引擎。
     *
     * @param conditionJson 结构化条件 JSON
     * @param engine        表达式引擎：AVIATOR / SPEL
     * @return 表达式字符串（如 {@code amount > 10000 && deptCode == 'SALES'}）
     */
    String buildExpression(String conditionJson, String engine);

    /**
     * 解析表达式字符串为结构化条件 JSON（反向转换）。
     *
     * <p>仅支持由 {@link #buildExpression} 生成的简单表达式，
     * 不支持嵌套括号或复杂函数调用。
     *
     * @param expression 表达式字符串
     * @param engine     表达式引擎
     * @return 结构化条件 JSON
     */
    String parseExpression(String expression, String engine);

    /**
     * 校验表达式语法是否正确。
     *
     * @param expression 表达式字符串
     * @param engine     表达式引擎
     * @return 校验结果 Map：{valid: true/false, error: "错误信息"}
     */
    Map<String, Object> validateExpression(String expression, String engine);

    /**
     * 获取可用的操作符列表。
     *
     * @return 操作符列表
     */
    List<Map<String, String>> getOperators();

    /**
     * 获取可用的值类型列表。
     *
     * @return 值类型列表
     */
    List<Map<String, String>> getValueTypes();

    // ==================== P1-4: 可视化编辑增强 ====================

    /**
     * 获取指定流程定义的可用变量列表（用于表达式编辑器的变量提示）。
     *
     * <p>P1-4: 从流程定义的所有节点表单中提取变量，包括表单字段、
     * 系统内置变量（如 initiatorId, currentTime 等）。
     *
     * @param definitionId 流程定义 ID
     * @return 变量列表，每项包含 fieldKey/label/fieldType/description
     */
    List<Map<String, String>> getVariablesByDefinition(String definitionId);

    /**
     * 预览/测试表达式执行结果。
     *
     * <p>P1-4: 给定表达式和示例变量，返回表达式执行结果（true/false），
     * 用于前端实时预览条件匹配效果。
     *
     * @param expression 表达式字符串
     * @param variables  示例变量 Map
     * @param engine     表达式引擎（默认 AVIATOR）
     * @return 执行结果：{result: true/false, error: null/"错误信息"}
     */
    Map<String, Object> previewExpression(String expression, Map<String, Object> variables, String engine);

    /**
     * 获取条件模板列表。
     *
     * <p>P1-4: 提供常用条件模板（如金额判断、部门判断、时间判断等），
     * 用户选择模板后可快速编辑自定义条件。
     *
     * @return 模板列表，每项包含 id/name/description/templateJson
     */
    List<Map<String, String>> getConditionTemplates();
}
