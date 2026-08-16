package com.njydsz.workflow.server.service.impl.definition;

import com.googlecode.aviator.AviatorEvaluator;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.entity.FlowNode;
import com.njydsz.workflow.infra.mapper.FlowNodeMapper;
import com.njydsz.workflow.server.service.FlowConditionExprService;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 条件表达式可视化编辑器服务实现（P2-1）
 *
 * <p>对 {@link FlowConditionExprService} 接口的完整实现，是工作流设计器 「<b>可视化条件配置</b>」能力的服务端支撑。 负责将前端<b>结构化条件
 * JSON</b> ↔ <b>表达式字符串</b>双向转换， 是大厂 B 端工作流「业务方零代码配置条件」的关键能力。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>JSON → 表达式</b>：{@link #buildExpression} — 将结构化条件 JSON（{@code {logic, groups, rules}}）转换为
 *       Aviator 表达式字符串
 *   <li><b>表达式 → JSON</b>：{@link #parseExpression} — 将表达式字符串解析为结构化条件 JSON（用于设计器展示 / 编辑）
 *   <li><b>表达式校验</b>：{@link #validateExpression} — 校验表达式语法正确性，校验失败返回错误信息
 *   <li><b>测试执行</b>：{@link #testExecute} — 使用测试变量执行表达式，返回 true / false 验证条件语义
 * </ul>
 *
 * <p><b>P1-3 引擎收敛：</b>运行时条件评估统一收敛为 Aviator 单引擎， SpEL 代码已全部移除。
 *
 * <p><b>操作符映射：</b>
 *
 * <p>前端下拉选择的<b>结构化操作符</b>（如 {@code EQ / GT / IN / CONTAINS}）在转换时 映射为 Aviator <b>原生操作符</b>，映射关系见
 * {@link #OPERATOR_MAP}：
 *
 * <pre>
 *   EQ       → ==
 *   NE       → !=
 *   GT       → &gt;
 *   GTE      → &gt;=
 *   LT       → &lt;
 *   LTE      → &lt;=
 *   IN       → seq.in
 *   NOT_IN   → !seq.in
 *   CONTAINS → string.contains
 *   IS_NULL  → ==nil
 *   IS_EMPTY → string.isEmpty
 *   ...
 * </pre>
 *
 * <p><b>事务边界：</b>本类不开启事务（{@code @Transactional} 缺失），所有方法为<b>纯函数式</b>操作， 不涉及数据库写入。{@code nodeMapper}
 * 仅用于条件变更时的关联更新，由调用方决定事务边界。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li><b>操作符抽象</b>：通过 {@link #OPERATOR_MAP} 统一管理<b>结构化操作符</b>与<b>引擎原生符号</b> 的映射关系，新增操作符只需修改此
 *       Map，无需修改业务逻辑
 *   <li><b>空安全</b>：{@code conditionJson} / {@code engine} 为 null 时直接返回空字符串
 *   <li><b>解析失败降级</b>：JSON 解析失败时返回空字符串，不抛异常（业务方可重新输入）
 *   <li><b>Aviator 5.x 兼容</b>：使用 {@link AviatorEvaluator#compile} 编译表达式， 支持 {@code seq.contains}
 *       等内置函数
 * </ul>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 场景：业务方在设计器配置「金额 > 10000 且 部门 = 财务部」
 * String conditionJson = """
 *     {
 *       "logic": "AND",
 *       "groups": [
 *         {"field": "amount", "op": "GT", "value": 10000},
 *         {"field": "dept", "op": "EQ", "value": "finance"}
 *       ]
 *     }
 *     """;
 * String expr = exprService.buildExpression(conditionJson, "AVIATOR");
 * // expr = "amount > 10000 && dept == 'finance'"
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see FlowConditionExprService 接口定义
 * @see AviatorEvaluator Aviator 表达式引擎
 * @see FlowNode 流程节点（关联条件表达式的实体）
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FlowConditionExprServiceImpl implements FlowConditionExprService {

  /** 流程节点 Mapper，用于条件变更时关联更新节点 ext 字段 */
  private final FlowNodeMapper nodeMapper;

  /**
   * 操作符映射：枚举 → Aviator 符号
   *
   * <p>Key 为前端下拉选择的<b>结构化操作符</b>，Value 为 Aviator 原生符号。 新增操作符只需在此 Map 中新增一行即可，无需修改业务逻辑。
   */
  private static final Map<String, String> OPERATOR_MAP = new LinkedHashMap<>();

  static {
    OPERATOR_MAP.put("EQ", "==");
    OPERATOR_MAP.put("NE", "!=");
    OPERATOR_MAP.put("GT", ">");
    OPERATOR_MAP.put("GTE", ">=");
    OPERATOR_MAP.put("LT", "<");
    OPERATOR_MAP.put("LTE", "<=");
    OPERATOR_MAP.put("IN", "seq.in");
    OPERATOR_MAP.put("NOT_IN", "!seq.in");
    OPERATOR_MAP.put("CONTAINS", "string.contains");
    OPERATOR_MAP.put("STARTS_WITH", "string.startsWith");
    OPERATOR_MAP.put("ENDS_WITH", "string.endsWith");
    OPERATOR_MAP.put("IS_NULL", "==nil");
    OPERATOR_MAP.put("NOT_NULL", "!=nil");
    OPERATOR_MAP.put("IS_EMPTY", "string.isEmpty");
    OPERATOR_MAP.put("NOT_EMPTY", "!string.isEmpty");
  }

  /**
   * 将结构化条件 JSON 转换为 Aviator 表达式字符串
   *
   * <p>完整转换链路：
   *
   * <ol>
   *   <li>解析条件 JSON，提取 {@code logic}（AND/OR）与 {@code groups}（条件项列表）
   *   <li>逐个条件组构建原子表达式（{@link #buildGroupExpr}），值按 {@code valueType} 格式化
   *   <li>多组条件下用 {@code &&} / {@code ||} 拼接，单组条件不加括号
   * </ol>
   *
   * <p><b>降级语义：</b>JSON 为空 / 解析失败 / 无条件项时返回空字符串，<b>不抛异常</b>， 由调用方决定空表达式语义。
   *
   * @param conditionJson 结构化条件 JSON
   * @param engine 表达式引擎（仅支持 {@code AVIATOR}，其他值返回空字符串）
   * @return 表达式字符串（如 {@code amount > 10000 && deptCode == 'SALES'}），无输入返回空字符串
   */
  @Override
  public String buildExpression(String conditionJson, String engine) {
    if (conditionJson == null || conditionJson.isBlank()) {
      return "";
    }
    if (engine != null && !"AVIATOR".equalsIgnoreCase(engine)) {
      return "";
    }
    try {
      Map<String, Object> root = YdszJson.parseMap(conditionJson);
      if (root == null) {
        return "";
      }
      String logic = root.get("logic") == null ? "AND" : String.valueOf(root.get("logic"));
      String logicOp = "OR".equalsIgnoreCase(logic) ? " || " : " && ";

      List<Map<String, Object>> groups = MapUtils.getListOfMaps(root, "groups");
      if (groups == null || groups.isEmpty()) {
        return "";
      }

      List<String> parts = new ArrayList<>();
      for (Map<String, Object> group : groups) {
        String part = buildGroupExpr(group);
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

  /**
   * 反向解析：表达式字符串 → 结构化条件 JSON
   *
   * <p><b>实现限制：</b>仅支持由 {@link #buildExpression} 生成的简单表达式， 解析策略为「按 {@code &&} / {@code ||} 拆分 →
   * 逐项按操作符拆分」， 不支持嵌套括号、复杂函数调用、字符串字面量内嵌逻辑运算符。
   *
   * @param expression 表达式字符串
   * @param engine 表达式引擎（当前实现未使用，保留参数兼容）
   * @return 结构化条件 JSON（{@code {logic, groups}}），空输入返回 {@code "{}"}， 解析失败返回 {@code "{}"}
   */
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
      return YdszJson.toJson(result);
    } catch (Exception e) {
      log.warn("[CondExpr] 解析表达式失败: expr={} err={}", expression, e.getMessage());
      return "{}";
    }
  }

  /**
   * 表达式语法校验
   *
   * <p>当前实现仅做<b>轻量语法检查</b>：括号匹配、单引号 / 双引号偶数匹配。 完整语法校验需调用 Aviator {@code compile}，
   * 此处保留轻量校验作为「实时反馈」场景的快速响应。
   *
   * @param expression 表达式字符串
   * @param engine 表达式引擎（当前实现未使用）
   * @return 校验结果 Map：
   *     <ul>
   *       <li>{@code valid} (boolean) — 是否通过
   *       <li>{@code error} (String) — 错误信息（通过时为 {@code null}）
   *     </ul>
   */
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

  /**
   * 获取全部可用操作符列表
   *
   * <p>遍历 {@link #OPERATOR_MAP}，每项返回 {@code {code, aviator}} 两个字段， 供前端下拉框渲染。修改 {@link
   * #OPERATOR_MAP} 即自动同步到前端。
   *
   * @return 操作符列表（保持 {@code OPERATOR_MAP} 的插入顺序）
   */
  @Override
  public List<Map<String, String>> getOperators() {
    List<Map<String, String>> result = new ArrayList<>();
    for (Map.Entry<String, String> entry : OPERATOR_MAP.entrySet()) {
      Map<String, String> op = new LinkedHashMap<>();
      op.put("code", entry.getKey());
      op.put("aviator", entry.getValue());
      result.add(op);
    }
    return result;
  }

  /**
   * 获取全部可用值类型列表
   *
   * <p>值类型用于前端条件编辑器选择「值输入框」的渲染方式：字符串、数字、布尔、日期、列表等。
   *
   * @return 值类型列表，每项包含 {@code {code, name}} 字段，{@code name} 为中文标签
   */
  @Override
  public List<Map<String, String>> getValueTypes() {
    return List.of(
        Map.of("code", "STRING", "name", "字符串"),
        Map.of("code", "NUMBER", "name", "数字"),
        Map.of("code", "BOOLEAN", "name", "布尔值"),
        Map.of("code", "DATE", "name", "日期"),
        Map.of("code", "DATETIME", "name", "日期时间"),
        Map.of("code", "LIST", "name", "列表"),
        Map.of("code", "NULL", "name", "空值"));
  }

  // ============================== 内部辅助 ==============================

  /**
   * 构建单个条件组的原子表达式（仅 Aviator）
   *
   * <p>根据 {@code operator} 选择操作符符号，特殊处理：
   *
   * <ul>
   *   <li>NULL / EMPTY 类操作符不需要值，仅 {@code field symbol}
   *   <li>IN / NOT_IN：Aviator 用 {@code seq.in(list, value)}
   *   <li>CONTAINS / STARTS_WITH / ENDS_WITH：Aviator 用 {@code string.xxx(field, value)}
   *   <li>其他操作符：{@code field symbol value}
   * </ul>
   *
   * @param group 单个条件组（含 field/operator/value/valueType）
   * @return 原子表达式字符串，未知操作符返回 {@code null}
   */
  private String buildGroupExpr(Map<String, Object> group) {
    String field = String.valueOf(group.get("field"));
    String operator = String.valueOf(group.get("operator")).toUpperCase();
    Object value = group.get("value");
    String valueType =
        group.get("valueType") == null
            ? "STRING"
            : String.valueOf(group.get("valueType")).toUpperCase();

    String symbol = OPERATOR_MAP.get(operator);
    if (symbol == null) {
      log.warn("[CondExpr] 未知操作符: {}", operator);
      return null;
    }

    // NULL / EMPTY 类操作符不需要值
    if ("IS_NULL".equals(operator)
        || "NOT_NULL".equals(operator)
        || "IS_EMPTY".equals(operator)
        || "NOT_EMPTY".equals(operator)) {
      return field + " " + symbol;
    }

    // 格式化值
    String formattedValue = formatValue(value, valueType);

    // IN / NOT_IN 特殊处理
    if ("IN".equals(operator) || "NOT_IN".equals(operator)) {
      return symbol + "(" + formattedValue + ", " + field + ")";
    }

    // CONTAINS / STARTS_WITH / ENDS_WITH 特殊处理
    if ("CONTAINS".equals(operator)
        || "STARTS_WITH".equals(operator)
        || "ENDS_WITH".equals(operator)) {
      return symbol + "(" + field + ", " + formattedValue + ")";
    }

    return field + " " + symbol + " " + formattedValue;
  }

  /**
   * 按值类型格式化值（仅 Aviator）
   *
   * <p>不同 {@code valueType} 对应不同格式化策略：
   *
   * <ul>
   *   <li>NUMBER — 原样输出
   *   <li>BOOLEAN — 转小写
   *   <li>STRING / DATE / DATETIME — 用单引号包裹
   *   <li>LIST — Aviator 用 {@code seq.list(a, b, c)}
   *   <li>NULL — {@code nil}
   * </ul>
   *
   * @param value 原始值
   * @param valueType 值类型字符串
   * @return 格式化后的值字符串
   */
  private String formatValue(Object value, String valueType) {
    if (value == null) {
      return "nil";
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
        List<Object> list = value instanceof List<?> l ? new ArrayList<>(l) : List.of();
        StringBuilder sb = new StringBuilder("seq.list(");
        for (int i = 0; i < list.size(); i++) {
          if (i > 0) sb.append(", ");
          sb.append(formatValue(list.get(i), "STRING"));
        }
        sb.append(")");
        return sb.toString();
      default:
        return "'" + value + "'";
    }
  }

  /**
   * 解析单个原子表达式为条件组
   *
   * <p>先剥离外层成对括号，再按 {@link #OPERATOR_MAP} 中的 Aviator 符号定位操作符位置， 左侧为 field，右侧为 value。匹配到第一个操作符即返回。
   *
   * @param expr 原子表达式（已去除 AND/OR 连接符）
   * @return 条件组 {@code {field, operator, value, valueType}}，无法解析返回 {@code null}
   */
  private Map<String, Object> parseSingleExpr(String expr) {
    // 去掉外层括号
    while (expr.startsWith("(") && expr.endsWith(")")) {
      expr = expr.substring(1, expr.length() - 1).trim();
    }
    // 简单解析：field OP value
    for (Map.Entry<String, String> entry : OPERATOR_MAP.entrySet()) {
      String op = entry.getValue();
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
   *
   * <p>同时支持单引号和双引号，仅当两端是相同引号时才剥离。
   *
   * @param s 原始字符串
   * @return 去引号后的字符串，输入为 {@code null} 时返回 {@code null}
   */
  private String unquote(String s) {
    if (s == null) return null;
    if ((s.startsWith("'") && s.endsWith("'")) || (s.startsWith("\"") && s.endsWith("\""))) {
      return s.substring(1, s.length() - 1);
    }
    return s;
  }

  /**
   * 按值的字面量特征猜测其值类型
   *
   * <p>判断顺序：{@code NULL → NUMBER → BOOLEAN → STRING}，无法识别时默认 {@code STRING}。
   *
   * @param value 原始字符串值
   * @return 值类型字符串（{@code NULL/NUMBER/BOOLEAN/STRING}）
   */
  private String guessValueType(String value) {
    if (value == null || value.isBlank()) return "NULL";
    if (value.matches("-?\\d+(\\.\\d+)?")) return "NUMBER";
    if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) return "BOOLEAN";
    if (value.startsWith("'") || value.startsWith("\"")) return "STRING";
    return "STRING";
  }

  // ==================== P1-4: 可视化编辑增强实现 ====================

  /**
   * 获取指定流程定义的可用变量列表（P1-4 可视化编辑增强）
   *
   * <p>合并两类变量：
   *
   * <ol>
   *   <li><b>系统内置变量</b>：{@code initiatorId}、{@code initiatorName}、{@code currentTime}、 {@code
   *       currentUserId}、{@code currentUserName}
   *   <li><b>表单字段变量</b>：从所有节点的 {@code ext.formSchema.fields} 中提取， 子表单字段以 {@code parentKey.subKey}
   *       形式拼接
   * </ol>
   *
   * <p>用于表达式编辑器的「变量提示」下拉框，业务方配置条件时无需手写变量名。
   *
   * @param definitionId 流程定义 ID
   * @return 变量列表，每项含 {@code fieldKey/label/fieldType/description/nodeCode/nodeName}
   */
  @Override
  public List<Map<String, String>> getVariablesByDefinition(String definitionId) {
    if (definitionId == null || definitionId.isBlank()) {
      return List.of();
    }
    List<Map<String, String>> result = new ArrayList<>();

    // 1. 添加系统内置变量
    result.add(
        Map.of(
            "fieldKey",
            "initiatorId",
            "label",
            "发起人ID",
            "fieldType",
            "STRING",
            "description",
            "流程发起人用户ID"));
    result.add(
        Map.of(
            "fieldKey",
            "initiatorName",
            "label",
            "发起人姓名",
            "fieldType",
            "STRING",
            "description",
            "流程发起人姓名"));
    result.add(
        Map.of(
            "fieldKey",
            "currentTime",
            "label",
            "当前时间",
            "fieldType",
            "DATETIME",
            "description",
            "系统当前时间"));
    result.add(
        Map.of(
            "fieldKey",
            "currentUserId",
            "label",
            "当前审批人ID",
            "fieldType",
            "STRING",
            "description",
            "当前节点审批人用户ID"));
    result.add(
        Map.of(
            "fieldKey",
            "currentUserName",
            "label",
            "当前审批人姓名",
            "fieldType",
            "STRING",
            "description",
            "当前节点审批人姓名"));

    // 2. 从流程定义的所有节点表单中提取变量
    try {
      List<FlowNode> nodes = nodeMapper.selectByDefinitionId(definitionId);
      if (nodes != null && !nodes.isEmpty()) {
        for (FlowNode node : nodes) {
          extractVariablesFromNode(node, result);
        }
      }
    } catch (Exception e) {
      log.warn("[CondExpr] 提取流程变量失败: definitionId={} err={}", definitionId, e.getMessage());
    }

    return result;
  }

  /**
   * 表达式预览执行（P1-4 可视化编辑增强）
   *
   * <p>使用测试变量驱动表达式执行，返回布尔结果。供前端表达式编辑器「实时预览」使用， 业务方输入变量值后立即看到条件匹配结果。仅支持 Aviator 引擎。
   *
   * @param expression 表达式字符串
   * @param variables 测试变量（{@code null} 时按空 Map 处理）
   * @param engine 表达式引擎（仅支持 {@code AVIATOR}）
   * @return 执行结果 Map：
   *     <ul>
   *       <li>{@code result} (Boolean) — 表达式执行结果（执行异常时为 {@code null}）
   *       <li>{@code error} (String) — 错误信息（成功时为 {@code null}）
   *     </ul>
   */
  @Override
  public Map<String, Object> previewExpression(
      String expression, Map<String, Object> variables, String engine) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (expression == null || expression.isBlank()) {
      result.put("result", false);
      result.put("error", "表达式不能为空");
      return result;
    }
    if (engine != null && !"AVIATOR".equalsIgnoreCase(engine)) {
      result.put("result", null);
      result.put("error", "不支持的表达式引擎");
      return result;
    }

    try {
      Object exprResult =
          AviatorEvaluator.execute(expression, variables != null ? variables : Map.of());
      result.put("result", Boolean.TRUE.equals(exprResult));
      result.put("error", null);
    } catch (Exception e) {
      log.warn("[CondExpr] 表达式预览失败: expr={} err={}", expression, e.getMessage());
      result.put("result", null);
      result.put("error", "执行异常: " + e.getMessage());
    }
    return result;
  }

  /**
   * 获取常用条件模板列表（P1-4 可视化编辑增强）
   *
   * <p>内置 10 个常用模板，覆盖金额判断、区间判断、部门匹配、职级判断、日期判断、 申请人匹配、多条件组合（AND/OR）、文本包含等典型场景。
   * 业务方选择模板后可在设计器中微调参数，无需从零配置条件。
   *
   * <p>每个模板 {@code templateJson} 字段为结构化条件 JSON，可直接传入 {@link #buildExpression} 转换为表达式。
   *
   * @return 模板列表，每项含 {@code id/name/description/templateJson}
   */
  @Override
  public List<Map<String, String>> getConditionTemplates() {
    List<Map<String, String>> templates = new ArrayList<>();

    templates.add(
        buildTemplate(
            "AMOUNT_GT",
            "金额大于指定值",
            "当申请金额超过设定值时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GT\",\"value\":10000,\"valueType\":\"NUMBER\"}]}"));

    templates.add(
        buildTemplate(
            "AMOUNT_RANGE",
            "金额区间判断",
            "当申请金额在指定区间内时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GTE\",\"value\":1000,\"valueType\":\"NUMBER\"},{\"field\":\"amount\",\"operator\":\"LTE\",\"value\":50000,\"valueType\":\"NUMBER\"}]}"));

    templates.add(
        buildTemplate(
            "DEPT_EQ",
            "部门匹配",
            "当申请人部门等于指定部门时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"deptCode\",\"operator\":\"EQ\",\"value\":\"SALES\",\"valueType\":\"STRING\"}]}"));

    templates.add(
        buildTemplate(
            "DEPT_IN",
            "部门在列表中",
            "当申请人部门属于指定部门列表时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"deptCode\",\"operator\":\"IN\",\"value\":[\"SALES\",\"MARKETING\"],\"valueType\":\"LIST\"}]}"));

    templates.add(
        buildTemplate(
            "LEVEL_GTE",
            "职级大于等于",
            "当申请人职级大于等于指定职级时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"level\",\"operator\":\"GTE\",\"value\":5,\"valueType\":\"NUMBER\"}]}"));

    templates.add(
        buildTemplate(
            "DATE_GT",
            "日期大于指定日期",
            "当日期字段大于指定日期时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"applyDate\",\"operator\":\"GT\",\"value\":\"2024-01-01\",\"valueType\":\"DATE\"}]}"));

    templates.add(
        buildTemplate(
            "INITIATOR_EQ",
            "申请人等于指定人",
            "当流程发起人等于指定人时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"initiatorId\",\"operator\":\"EQ\",\"value\":\"u001\",\"valueType\":\"STRING\"}]}"));

    templates.add(
        buildTemplate(
            "COMBINE_AND",
            "多条件组合（AND）",
            "多个条件同时成立时条件匹配",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"amount\",\"operator\":\"GT\",\"value\":10000,\"valueType\":\"NUMBER\"},{\"field\":\"level\",\"operator\":\"GTE\",\"value\":5,\"valueType\":\"NUMBER\"}]}"));

    templates.add(
        buildTemplate(
            "COMBINE_OR",
            "多条件组合（OR）",
            "任一条件成立时条件匹配",
            "{\"logic\":\"OR\",\"groups\":[{\"field\":\"deptCode\",\"operator\":\"EQ\",\"value\":\"SALES\",\"valueType\":\"STRING\"},{\"field\":\"deptCode\",\"operator\":\"EQ\",\"value\":\"MARKETING\",\"valueType\":\"STRING\"}]}"));

    templates.add(
        buildTemplate(
            "CONTAINS",
            "字段包含指定文本",
            "当文本字段包含指定文本时条件成立",
            "{\"logic\":\"AND\",\"groups\":[{\"field\":\"title\",\"operator\":\"CONTAINS\",\"value\":\"紧急\",\"valueType\":\"STRING\"}]}"));

    return templates;
  }

  // ==================== 内部辅助方法 ====================

  /**
   * 从节点 {@code ext} 字段中提取表单变量
   *
   * <p>遍历 {@code ext.formSchema.fields}，提取 {@code
   * fieldKey/label/fieldType/placeholder/description}， 同时处理嵌套的 {@code subFields}（子表单字段以 {@code
   * parentKey.subKey} 形式拼接）。 异常被 try-catch 吞掉记 WARN，不影响其他节点变量提取。
   *
   * @param node 流程节点
   * @param result 累加结果列表（输出参数）
   */
  private void extractVariablesFromNode(FlowNode node, List<Map<String, String>> result) {
    if (node == null) {
      return;
    }
    try {
      String ext = node.getExt();
      if (ext == null || ext.isBlank()) {
        return;
      }
      Map<String, Object> extMap = YdszJson.parseMap(ext);
      if (extMap == null) {
        return;
      }
      Object formSchemaObj = extMap.get("formSchema");
      if (formSchemaObj == null) {
        return;
      }
      Map<String, Object> schemaMap = MapUtils.safeCastMap(formSchemaObj);
      if (schemaMap == null) {
        return;
      }
      List<Map<String, Object>> fieldsMap = MapUtils.getListOfMaps(schemaMap, "fields");
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
        List<Map<String, Object>> subFieldsMap =
            (List<Map<String, Object>>) fieldMap.get("subFields");
        if (subFieldsMap != null && !subFieldsMap.isEmpty()) {
          for (Map<String, Object> subFieldMap : subFieldsMap) {
            String subFieldKey = (String) subFieldMap.get("fieldKey");
            String subLabel = (String) subFieldMap.get("label");
            String subFieldType = (String) subFieldMap.get("fieldType");
            if (subFieldKey != null && !subFieldKey.isBlank()) {
              Map<String, String> varInfo = new LinkedHashMap<>();
              varInfo.put("fieldKey", fieldKey + "." + subFieldKey);
              varInfo.put(
                  "label",
                  (label != null ? label : fieldKey)
                      + " - "
                      + (subLabel != null ? subLabel : subFieldKey));
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

  /**
   * 构建单个条件模板的 Map
   *
   * @param id 模板 ID
   * @param name 模板名称（中文）
   * @param description 模板描述
   * @param templateJson 结构化条件 JSON 字符串
   * @return 模板 Map（含 4 个字段）
   */
  private Map<String, String> buildTemplate(
      String id, String name, String description, String templateJson) {
    Map<String, String> template = new LinkedHashMap<>();
    template.put("id", id);
    template.put("name", name);
    template.put("description", description);
    template.put("templateJson", templateJson);
    return template;
  }
}
