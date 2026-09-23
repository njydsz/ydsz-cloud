package com.njydsz.literule.server.dsl;

import java.util.ArrayList;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;

/**
 * Decision Table 导出 DMN 1.4 XML 工具（P2-F6）
 *
 * <p>将 {@link RuleDefinitionDTO} 中标记为 decision_table 的规则导出为符合 OMG DMN 1.4 标准的 XML 文档，
 * 实现与主流规则引擎（Drools、Camunda、Flowable）的 Decision Table 互操作。
 *
 * <p>输出 XML 结构：
 *
 * <pre>{@code
 * <?xml version="1.0" encoding="UTF-8"?>
 * <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
 *              id="literule-export"
 *              name="LiteRule Decision Tables"
 *              namespace="https://njydsz.com/literule/dmn">
 *   <decision id="d_RISK_001" name="风险评分">
 *     <decisionTable id="dt_RISK_001" hitPolicy="FIRST">
 *       <input id="input_age" label="Age">
 *         <inputExpression id="inputExpr_age" typeRef="integer">
 *           <text>age</text>
 *         </inputExpression>
 *       </input>
 *       <output id="output_severity" label="Severity" typeRef="string"/>
 *       <rule id="row_1">
 *         <inputEntry id="row_1_age"><text>> 18</text></inputEntry>
 *         <outputEntry id="row_1_severity"><text>"HIGH"</text></outputEntry>
 *       </rule>
 *     </decisionTable>
 *   </decision>
 * </definitions>
 * }</pre>
 *
 * <p>当前实现仅生成 XML 骨架（decision 元数据 + input/output 声明）。规则体（rule 行）的精确还原
 * 需要决策表的行数据存储在扩展字段中；{@code rowExpressions} 由调用方通过 {@link
 * RuleDefinitionDTO#getDecisionTable() #getRows()} 填充。
 *
 * @since 26.09.23
 * @author ydsz-team
 */
@Slf4j
public final class DecisionTableDmnExporter {
  /** XML 输出编码 */
  private static final String XML_ENCODING = "UTF-8";
  /** DMN 1.4 命名空间 */
  private static final String DMN_NAMESPACE = "https://www.omg.org/spec/DMN/20191111/MODEL/";
  /** LiteRule 自定义命名空间 */
  private static final String LITERULE_NAMESPACE = "https://njydsz.com/literule/dmn";

  private DecisionTableDmnExporter() {}

  /**
   * 导出规则列表中为 decision_table 的条目的 DMN 1.4 XML
   *
   * <p>仅导出 {@link RuleDefinitionDTO#getType()} 为 {@code "decision_table"} 的规则。
   * 若列表为空或无 decision_table 规则，仍返回包含 {@code <definitions>} 根的合法空 XML。
   *
   * @param rules 规则定义列表（可含混合类型）
   * @param definitionName 导出定义集名称（写入 definitions/@name）
   * @return DMN 1.4 XML 字符串
   */
  public static String exportDmnXml(List<RuleDefinitionDTO> rules, String definitionName) {
    if (rules == null || rules.isEmpty()) {
      return emptyDefinitions(definitionName);
    }
    List<RuleDefinitionDTO> dtRules = new ArrayList<>(rules.size());
    for (RuleDefinitionDTO r : rules) {
      if (r != null && "decision_table".equals(r.getType())) {
        dtRules.add(r);
      }
    }
    if (dtRules.isEmpty()) {
      return emptyDefinitions(definitionName);
    }

    try {
      javax.xml.parsers.DocumentBuilderFactory factory =
          javax.xml.parsers.DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      Document doc = factory.newDocumentBuilder().newDocument();

      // definitions 根
      Element definitions = doc.createElementNS(DMN_NAMESPACE, "definitions");
      definitions.setAttribute("id", "literule-export");
      definitions.setAttribute("name",
          definitionName != null ? definitionName : "LiteRule Decision Tables");
      definitions.setAttribute("namespace", LITERULE_NAMESPACE);
      doc.appendChild(definitions);

      // 每个 decision_table 规则
      for (RuleDefinitionDTO dtDef : dtRules) {
        Element decision = createDecisionElement(doc, dtDef);
        if (decision != null) {
          definitions.appendChild(decision);
        }
      }

      return serializeXml(doc);
    } catch (Exception e) {
      log.warn("[LiteRule-DMN] DMN XML 生成失败，返回空 definitions: {}", e.getMessage());
      return emptyDefinitions(definitionName);
    }
  }

  /**
   * 根据 RuleDefinitionDTO 创建 decision 元素
   *
   * <p>input 声明从 {@link RuleDefinitionDTO#getConditionExpression()} 推断（简化：将条件中引用的变量视为 input）；
   * output 声明根据 {@code defaultSeverity} 生成。
   */
  private static Element createDecisionElement(Document doc, RuleDefinitionDTO dtDef) {
    String code = dtDef.getCode();
    if (code == null || code.isBlank()) {
      return null;
    }
    Element decision = doc.createElementNS(DMN_NAMESPACE, "decision");
    decision.setAttribute("id", "d_" + code);
    decision.setAttribute("name", dtDef.getName() != null ? dtDef.getName() : code);

    Element dt = doc.createElementNS(DMN_NAMESPACE, "decisionTable");
    dt.setAttribute("id", "dt_" + code);
    String hitPolicy = mapHitPolicy(dtDef.getHitPolicy());
    dt.setAttribute("hitPolicy", hitPolicy);

    // input：从条件表达式提取变量名
    if (dtDef.getConditionExpression() != null && !dtDef.getConditionExpression().isBlank()) {
      String[] variables = extractVariables(dtDef.getConditionExpression());
      for (int i = 0; i < variables.length; i++) {
        Element input = doc.createElementNS(DMN_NAMESPACE, "input");
        input.setAttribute("id", "input_" + i + "_" + variables[i]);
        input.setAttribute("label", variables[i]);
        Element inputExpr = doc.createElementNS(DMN_NAMESPACE, "inputExpression");
        inputExpr.setAttribute("id", "inputExpr_" + i + "_" + variables[i]);
        inputExpr.setAttribute("typeRef", "string");
        Element text = doc.createElementNS(DMN_NAMESPACE, "text");
        text.setTextContent(variables[i]);
        inputExpr.appendChild(text);
        input.appendChild(inputExpr);
        dt.appendChild(input);
      }
    }

    // output
    Element output = doc.createElementNS(DMN_NAMESPACE, "output");
    output.setAttribute("id", "output_" + code);
    output.setAttribute("label", "result");
    output.setAttribute("typeRef", "string");
    dt.appendChild(output);

    decision.appendChild(dt);
    return decision;
  }

  /** 映射内部 hitPolicy 到 DMN 标准值 */
  private static String mapHitPolicy(String internalHitPolicy) {
    if (internalHitPolicy == null) {
      return "FIRST";
    }
    return switch (internalHitPolicy.toUpperCase()) {
      case "UNIQUE" -> "UNIQUE";
      case "PRIORITY" -> "PRIORITY";
      case "COLLECT" -> "COLLECT";
      case "ANY" -> "ANY";
      case "RULE_ORDER" -> "RULE_ORDER";
      default -> "FIRST";
    };
  }

  /** 从条件表达式中简单提取标识符（简化版：取 && || 分隔的每个子句左侧） */
  private static String[] extractVariables(String expression) {
    if (expression == null || expression.isBlank()) {
      return new String[0];
    }
    String[] clauses = expression.split("&&|\\|\\|");
    List<String> vars = new ArrayList<>();
    for (String clause : clauses) {
      String trimmed = clause.trim().replaceAll("[()]", "").trim();
      if (trimmed.isEmpty()) {
        continue;
      }
      // 取第一个操作数
      String[] tokens = trimmed.split("[<>=!]+\\s*");
      if (tokens.length > 0 && !tokens[0].isBlank()) {
        String var = tokens[0].trim();
        if (var.matches("[a-zA-Z_][a-zA-Z0-9_.]*") && !vars.contains(var)) {
          vars.add(var);
        }
      }
    }
    return vars.toArray(new String[0]);
  }

  /**
   * 序列化 DOM 为 XML 字符串
   *
   * @param doc DOM 文档
   * @return XML 字符串（带 XML 声明）
   */
  private static String serializeXml(Document doc) {
    try {
      javax.xml.transform.TransformerFactory factory =
          javax.xml.transform.TransformerFactory.newInstance();
      javax.xml.transform.Transformer transformer = factory.newTransformer();
      transformer.setOutputProperty(javax.xml.transform.OutputKeys.ENCODING, XML_ENCODING);
      transformer.setOutputProperty(javax.xml.transform.OutputKeys.INDENT, "yes");
      transformer.setOutputProperty(
          "{http://xml.apache.org/xslt}indent-amount", "2");
      java.io.StringWriter writer = new java.io.StringWriter();
      transformer.transform(new javax.xml.transform.dom.DOMSource(doc),
          new javax.xml.transform.stream.StreamResult(writer));
      return writer.toString();
    } catch (Exception e) {
      log.error("[LiteRule-DMN] XML 序列化异常: {}", e.getMessage());
      return "<?xml version=\"1.0\" encoding=\"UTF-8\"?><definitions/>";
    }
  }

  /** 生成空 definitions 兜底 XML */
  private static String emptyDefinitions(String definitionName) {
    String name =
        definitionName != null ? escapeXml(definitionName) : "LiteRule Decision Tables";
    return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>"
        + "<definitions xmlns=\"" + DMN_NAMESPACE + "\""
        + " id=\"literule-export\""
        + " name=\"" + name + "\""
        + " namespace=\"" + LITERULE_NAMESPACE + "\"/>";
  }

  /** 转义 XML 特殊字符 */
  private static String escapeXml(String input) {
    if (input == null) {
      return "";
    }
    return input.replace("&", "&amp;")
        .replace("<", "&lt;")
        .replace(">", "&gt;")
        .replace("\"", "&quot;");
  }
}
