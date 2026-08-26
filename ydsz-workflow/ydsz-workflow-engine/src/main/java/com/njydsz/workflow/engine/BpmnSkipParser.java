package com.njydsz.workflow.engine;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.enums.FlowSkipType;
import com.njydsz.workflow.domain.vo.FlowSkipVO;

/**
 * BPMN 跳转边解析器
 *
 * <p>负责将 BPMN 2.0 XML 中的 {@code <sequenceFlow>} 元素解析为 {@link FlowSkipVO} 数据对象。
 * 处理源/目标节点引用、条件表达式、扩展属性等。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmnSkipParser {

  private final BpmnElementHelper bpmnElementHelper;

  /**
   * 解析 sequenceFlow：BPMN 边 → FlowSkip
   *
   * @param elem sequenceFlow 元素
   * @return 解析后的 FlowSkip
   */
  public FlowSkipVO parseSkip(Element elem) {
    FlowSkipVO skip = new FlowSkipVO();
    skip.setSkipName(elem.getAttribute("name"));
    skip.setSkipType(FlowSkipType.PASS.name());
    // sequenceFlow 自身 id 作为 skip 唯一标识
    String sourceRef = elem.getAttribute("sourceRef");
    String targetRef = elem.getAttribute("targetRef");
    // A9: sourceNodeCode 独立列存储源节点编码（替代 ext JSON 中的 sourceRef）
    skip.setSourceNodeCode(sourceRef);
    Map<String, Object> ext = new HashMap<>();
    ext.put("sourceRef", sourceRef);
    ext.put("targetRef", targetRef);
    ext.put("sequenceFlowId", elem.getAttribute("id"));
    // P0-4: 边上的 skipExpression（条件）
    String skipExpr =
        elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "skipExpression");
    if (skipExpr != null && !skipExpr.isBlank()) {
      ext.put("skipExpression", skipExpr);
    }
    // 边的优先级（多出口时排序依据）
    String priority = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "priority");
    if (priority != null && !priority.isBlank()) {
      ext.put("priority", priority.trim());
    }
    skip.setExt(YdszJson.toJson(ext));
    // nextNodeCode 暂存 targetRef，定义模型转换时会再赋
    skip.setNextNodeCode(targetRef);
    // 解析条件表达式
    Element condExpr = bpmnElementHelper.findChild(elem, "conditionExpression");
    if (condExpr != null) {
      String expr = condExpr.getTextContent();
      if (expr != null) {
        expr = expr.trim();
        // 兼容 ${var} 和 var 裸表达式
        if (!expr.startsWith("${") && !expr.startsWith("#{")) {
          expr = "${" + expr + "}";
        }
        skip.setSkipCondition(expr);
      }
    }
    return skip;
  }
}
