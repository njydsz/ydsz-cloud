package com.njydsz.literule.server.engine.liteexpr;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.literule.domain.expression.ExpressionTraceNode;

/**
 * 表达式执行追踪 JSON 序列化工具（P2-F5 结构化表达式 trace）
 *
 * <p>将 {@link ExpressionTraceNode} 树转换为结构化 JSON 字符串，供前端渲染计算树、AI 辅助定
 * 位规则问题、日志系统结构化存储等场景使用。
 *
 * <p>输出示例：
 *
 * <pre>{@code
 * {
 *   "nodeType": "ROOT",
 *   "expression": "amount > 1000 && score > 800",
 *   "result": false,
 *   "evalTimeNs": 156000,
 *   "children": [
 *     {
 *       "nodeType": "LOGICAL",
 *       "operator": "&&",
 *       "result": false,
 *       "children": [
 *         { "nodeType": "COMPARISON", "operator": ">", "result": true, "..." },
 *         { "nodeType": "COMPARISON", "operator": ">", "result": false, "..." }
 *       ]
 *     }
 *   ]
 * }
 * }</pre>
 *
 * <p>空树（根节点为 null）返回 {@code "null"}；序列化异常时返回包含 {@code error} 字段的降级 JSON，保证调用方始终能拿到有效字符串。
 *
 * @since 26.09.23
 * @author ydsz-team
 */
@Slf4j
public final class ExpressionTraceJson {
  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY = 16;

  /** Gson 序列化时 null 值输出开关 */
  private static final boolean GSON_SERIALIZE_NULLS = false;

  private ExpressionTraceJson() {}

  /**
   * 将 {@link ExpressionTraceNode} 树序列化为 JSON 字符串
   *
   * <p>输出字段：
   *
   * <ul>
   *   <li>{@code nodeType} — 节点类型枚举名（ROOT/LOGICAL/COMPARISON/...）
   *   <li>{@code expression} — 节点对应的表达式片段（可为 null）
   *   <li>{@code operator} — 运算符字符串（LOGICAL/COMPARISON/ARITHMETIC 节点）
   *   <li>{@code result} — 节点求值结果（Boolean/Number/String）
   *   <li>{@code variableName}/{@code variableValue} — 变量节点的名称和值
   *   <li>{@code literalValue} — 字面值
   *   <li>{@code isShortCircuited} — 是否短路
   *   <li>{@code evalTimeNs} — 执行耗时（纳秒）（> 0 时输出）
   *   <li>{@code error} — 错误信息（表达式求值异常时输出）
   *   <li>{@code children} — 子节点数组
   * </ul>
   *
   * @param root 追踪树根节点；null 返回 {@code "null"}
   * @return JSON 字符串（保证非 null）
   */
  public static String toJson(ExpressionTraceNode root) {
    if (root == null) {
      return "null";
    }
    try {
      Map<String, Object> map = nodeToMap(root);
      return YdszJson.toJson(map);
    } catch (Exception e) {
      log.warn("[LiteExpr-Trace] JSON 序列化失败: {}", e.getMessage());
      return String.format("{\"error\": \"%s\"}", e.getMessage());
    }
  }

  /**
   * 将追踪树序列化为格式化的 JSON 字符串（带缩进，适用于调试/日志输出）
   *
   * @param root 追踪树根节点
   * @return 格式化 JSON 字符串；null 返回 {@code "null"}
   */
  public static String toPrettyJson(ExpressionTraceNode root) {
    if (root == null) {
      return "null";
    }
    try {
      Map<String, Object> map = nodeToMap(root);
      return YdszJson.format(map);
    } catch (Exception e) {
      log.warn("[LiteExpr-Trace] Pretty JSON 序列化失败: {}", e.getMessage());
      return String.format("{\n  \"error\": \"%s\"\n}", e.getMessage());
    }
  }

  /**
   * 转换单个节点为结构化 Map
   *
   * <p>仅输出非 null 字段（避免大对象影响可读性）。子节点递归展开但空 children 不输出。
   *
   * @param node 追踪节点
   * @return 字段 map
   */
  static Map<String, Object> nodeToMap(ExpressionTraceNode node) {
    Map<String, Object> map = new LinkedHashMap<>(COLLECTION_CAPACITY);
    map.put("nodeType", node.getNodeType() != null ? node.getNodeType().name() : "UNKNOWN");
    if (node.getExpression() != null && !node.getExpression().isEmpty()) {
      map.put("expression", node.getExpression());
    }
    if (node.getOperator() != null && !node.getOperator().isEmpty()) {
      map.put("operator", node.getOperator());
    }
    if (node.getResult() != null) {
      map.put("result", node.getResult());
    }
    if (node.getVariableName() != null) {
      map.put("variableName", node.getVariableName());
    }
    if (node.getVariableValue() != null) {
      map.put("variableValue", node.getVariableValue());
    }
    if (node.getLiteralValue() != null) {
      map.put("literalValue", node.getLiteralValue());
    }
    if (node.isShortCircuited()) {
      map.put("isShortCircuited", true);
    }
    if (node.getElapsedNanos() > 0) {
      map.put("evalTimeNs", node.getElapsedNanos());
    }
    if (node.getError() != null && !node.getError().isEmpty()) {
      map.put("error", node.getError());
    }
    if (node.getChildren() != null && !node.getChildren().isEmpty()) {
      List<Map<String, Object>> childrenMaps = new ArrayList<>(node.getChildren().size());
      for (ExpressionTraceNode child : node.getChildren()) {
        childrenMaps.add(nodeToMap(child));
      }
      map.put("children", childrenMaps);
    }
    return map;
  }
}
