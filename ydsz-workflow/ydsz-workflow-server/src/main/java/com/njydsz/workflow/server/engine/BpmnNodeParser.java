package com.njydsz.workflow.server.engine;

import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.FlowPerformType;
import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * BPMN 节点解析器
 *
 * <p>负责将 BPMN 2.0 XML 中的流程元素（如 startEvent、userTask、exclusiveGateway 等）
 * 解析为 {@link FlowNodeVO} 数据对象。处理包括属性映射、扩展属性读取、
 * 事件定义解析、多实例配置解析等所有与节点相关的逻辑。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmnNodeParser {

  private static final Logger log = LoggerFactory.getLogger(BpmnNodeParser.class);

  private final BpmnElementHelper bpmnElementHelper;

  /**
   * 解析节点：BPMN 元素 → FlowNode
   *
   * @param elem BPMN 元素
   * @param localName 元素本地名称
   * @return 解析后的 FlowNode，解析失败返回 null
   */
  public FlowNodeVO parseNode(Element elem, String localName) {
    FlowNodeVO node = new FlowNodeVO();
    node.setNodeCode(elem.getAttribute("id"));
    node.setNodeName(elem.getAttribute("name"));
    if (node.getNodeName() == null || node.getNodeName().isBlank()) {
      node.setNodeName(node.getNodeCode());
    }
    node.setNodeType(bpmnElementHelper.mapNodeType(localName));

    // P1-3: eventBasedGateway / complexGateway 解析支持
    // 映射为现有网关类型（CONDITION / INCLUSIVE）+ ext 标记 gatewayType，
    // 引擎使用现有逻辑处理，未来可扩展为完整的事件/复杂网关行为
    String normalized = localName == null ? "" : localName.toLowerCase();
    if ("eventbasedgateway".equals(normalized) || "complexgateway".equals(normalized)) {
      Map<String, Object> extMap = bpmnElementHelper.readOrInitExt(node);
      extMap.put(
          "gatewayType", "eventbasedgateway".equals(normalized) ? "EVENT_BASED" : "COMPLEX");
      node.setExt(YdszJson.toJson(extMap));
      log.info(
          "[BpmnNodeParser] {} 映射为 {} 类型 + ext.gatewayType 标记，nodeCode={}",
          localName,
          node.getNodeType() == FlowNodeType.CONDITION.getCode() ? "CONDITION" : "INCLUSIVE",
          node.getNodeCode());
    }

    // P0-2: 网关默认出边 — BPMN 2.0 规范中 exclusiveGateway / inclusiveGateway 的
    // default 属性指向默认 sequenceFlow id（无条件匹配时走的边）。解析阶段捕获，
    // 由 DefaultFlowAdvancer.resolvePassSkips 在所有条件都不匹配时使用。
    String defaultFlowId = elem.getAttribute("default");

    // 解析 BPMN 扩展属性：assignee / candidateUsers / candidateGroups / dueDate 等
    String assignee = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "assignee");
    String candidateUsers =
        elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "candidateUsers");
    String candidateGroups =
        elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "candidateGroups");
    String expression = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "expression");
    String formKey = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "formKey");
    String dueDate = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "dueDate");

    // P0-4: 扩展属性
    String priorityStr = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "priority");
    String async = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "async");
    String assigneeType = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "assigneeType");
    String performType = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "performType");
    String approveCountStr =
        elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "approveCount");
    String approveRateStr =
        elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "approveRate");
    String weightStr = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "weight");
    String timeoutStrategy =
        elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "timeoutStrategy");
    String timeout = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "timeout");
    String escalateUser = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "escalateUser");
    String skipAnyNode = elem.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "skipAnyNode");

    // 优先级：assignee > expression > candidateUsers > candidateGroups
    if (assignee != null && !assignee.isBlank()) {
      // assignee 可以是 ${expression} 或固定 user:1001
      if (assignee.startsWith("${")) {
        node.setPermissionFlag(assignee);
      } else if (assignee.startsWith("user:")
          || assignee.startsWith("role:")
          || assignee.startsWith("dept:")
          // P2-19: 支持 leader:/position: 前缀
          || assignee.startsWith("leader:")
          || assignee.startsWith("position:")
          // P2-38/P2-39: 支持 self_select:/multi_leader: 前缀，原样保留
          || assignee.startsWith("self_select:")
          || assignee.startsWith("multi_leader:")) {
        node.setPermissionFlag(assignee);
      } else {
        node.setPermissionFlag("user:" + assignee);
      }
    } else if (expression != null && !expression.isBlank()) {
      node.setPermissionFlag(expression.startsWith("${") ? expression : "${" + expression + "}");
    } else if (candidateUsers != null && !candidateUsers.isBlank()) {
      // P2-15: 多候选人全部写入 permissionFlag（逗号分隔），由 expandAssignees 展开为多人
      // 例如 candidateUsers="u1,u2,u3" → permissionFlag="user:u1,user:u2,user:u3"
      String[] users = candidateUsers.split(",");
      StringBuilder perm = new StringBuilder();
      for (int i = 0; i < users.length; i++) {
        String u = users[i].trim();
        if (u.isEmpty()) {
          continue;
        }
        if (perm.length() > 0) {
          perm.append(",");
        }
        // 已带前缀则原样保留，否则补 user:
        if (u.startsWith("user:")
            || u.startsWith("role:")
            || u.startsWith("dept:")
            || u.startsWith("leader:")
            || u.startsWith("position:")
            // P2-38/P2-39: 支持 self_select:/multi_leader: 前缀原样保留
            || u.startsWith("self_select:")
            || u.startsWith("multi_leader:")
            || u.startsWith("${")) {
          perm.append(u);
        } else {
          perm.append("user:").append(u);
        }
      }
      node.setPermissionFlag(perm.toString());
      node.setExt(YdszJson.toJson(Map.of("candidateUsers", candidateUsers)));
    } else if (candidateGroups != null && !candidateGroups.isBlank()) {
      // P2-15: 候选组同样支持多组逗号分隔，全部写入 permissionFlag
      String[] groups = candidateGroups.split(",");
      StringBuilder perm = new StringBuilder();
      for (int i = 0; i < groups.length; i++) {
        String g = groups[i].trim();
        if (g.isEmpty()) {
          continue;
        }
        if (perm.length() > 0) {
          perm.append(",");
        }
        if (g.startsWith("role:") || g.startsWith("dept:") || g.startsWith("${")) {
          perm.append(g);
        } else {
          perm.append("role:").append(g);
        }
      }
      node.setPermissionFlag(perm.toString());
      node.setExt(YdszJson.toJson(Map.of("candidateGroups", candidateGroups)));
    }

    // P0-4: 会签类型与扩展字段
    if (performType != null && !performType.isBlank()) {
      try {
        FlowPerformType pt = FlowPerformType.valueOf(performType.trim().toUpperCase());
        // 复用 skipAnyNode 字段存储会签类型（service 上挂 ext 表达）
        if (node.getSkipAnyNode() == null || node.getSkipAnyNode().isBlank()) {
          node.setSkipAnyNode(pt.name());
        }
      } catch (IllegalArgumentException e) {
        // invalid perform type, ignore
        log.debug("[BpmnNodeParser] 无效的会签类型，已跳过: nodeKey={}, value={}",
            node.getNodeCode(), node.getSkipAnyNode());
      }
    }

    // 把所有扩展属性塞入 ext JSON（统一持久化）
    Map<String, Object> ext = bpmnElementHelper.readOrInitExt(node);
    if (formKey != null && !formKey.isBlank()) {
      ext.put("formKey", formKey);
    }
    if (dueDate != null && !dueDate.isBlank()) {
      ext.put("dueDate", dueDate);
    }
    if (async != null && !async.isBlank()) {
      ext.put("async", Boolean.parseBoolean(async.trim()));
    }
    if (assigneeType != null && !assigneeType.isBlank()) {
        ext.put("assigneeType", assigneeType.trim());
    }
    if (performType != null && !performType.isBlank()) {
      ext.put("performType", performType.trim());
    }
    if (approveCountStr != null && !approveCountStr.isBlank()) {
        ext.put("approveCount", approveCountStr.trim());
    }
    if (approveRateStr != null && !approveRateStr.isBlank()) {
        ext.put("approveRate", approveRateStr.trim());
    }
    if (weightStr != null && !weightStr.isBlank()) {
      ext.put("weight", weightStr.trim());
    }
    if (timeoutStrategy != null && !timeoutStrategy.isBlank()) {
        ext.put("timeoutStrategy", timeoutStrategy.trim());
    }
    if (timeout != null && !timeout.isBlank()) {
      ext.put("timeout", timeout.trim());
    }
    if (escalateUser != null && !escalateUser.isBlank()) {
        ext.put("escalateUser", escalateUser.trim());
    }
    if (skipAnyNode != null && !skipAnyNode.isBlank()) {
      ext.put("skipAnyNode", skipAnyNode.trim());
    }

    // P0-2: 网关默认出边 — 存入 node.ext.defaultFlowId 供推进器使用
    if (defaultFlowId != null && !defaultFlowId.isBlank()) {
      ext.put("defaultFlowId", defaultFlowId.trim());
    }

    // P1-1: 解析 priority 写入 ext（任务节点优先级，1-100，待办默认按 priority DESC 排序）
    if (priorityStr != null && !priorityStr.isBlank()) {
      try {
        int p = Integer.parseInt(priorityStr.trim());
        if (p < 1) {
          p = 1;
        }
        if (p > 100) {
          p = 100;
        }
        ext.put("priority", p);
      } catch (NumberFormatException ignore) {
        // ignore invalid priority
      }
    }

    // P0-4: timer / error / signal / message 事件定义
    parseEventDefinitions(elem, ext);

    // P0-1: 标记事件捕获节点 — intermediateCatchEvent / boundaryEvent 为等待态
    if ("intermediateCatchEvent".equalsIgnoreCase(localName)
        || "boundaryEvent".equalsIgnoreCase(localName)) {
      if (ext.containsKey("eventType") || ext.containsKey("timer")) {
        ext.put("eventCatch", true);
      }
      // boundaryEvent 解析 attachedToRef（关联的 userTask ID）
      if ("boundaryEvent".equalsIgnoreCase(localName)) {
        String attachedTo = elem.getAttribute("attachedToRef");
        if (attachedTo != null && !attachedTo.isBlank()) {
          ext.put("attachedToRef", attachedTo);
        }
      }
    }

    // P0-4: 通用 extensionElements（用户自定义键值对）
    parseExtensionElements(elem, ext);

    node.setExt(YdszJson.toJson(ext));

    // 处理 userTask 的多实例特性（会签）
    if ("userTask".equalsIgnoreCase(localName)) {
      parseMultiInstance(elem, node, ext);
      node.setExt(YdszJson.toJson(ext));
    }
    return node;
  }

  /**
   * P0-4: 解析 timer / error / signal / message 事件定义
   *
   * @param elem 父 BPMN 元素
   * @param ext 扩展属性 Map（输出参数）
   */
  private void parseEventDefinitions(Element elem, Map<String, Object> ext) {
    NodeList children = elem.getChildNodes();
    boolean hasTimer = false;
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (!(n instanceof Element e)) {
        continue;
      }
      String local = e.getLocalName();
      if (local == null) {
        local = e.getNodeName();
      }
      switch (local.toLowerCase()) {
        case "timereventdefinition" -> {
          hasTimer = true;
          Map<String, Object> timer = new HashMap<>();
          Element timeCycle = bpmnElementHelper.findChild(e, "timeCycle");
          Element timeDate = bpmnElementHelper.findChild(e, "timeDate");
          Element timeDuration = bpmnElementHelper.findChild(e, "timeDuration");
          if (timeCycle != null) {
            timer.put("cycle", timeCycle.getTextContent().trim());
          }
          if (timeDate != null) {
            timer.put("date", timeDate.getTextContent().trim());
          }
          if (timeDuration != null) {
            timer.put("duration", timeDuration.getTextContent().trim());
          }
          ext.put("timer", timer);
        }
        case "erroreventdefinition" -> {
          String errorRef = e.getAttribute("errorRef");
          if (errorRef != null && !errorRef.isBlank()) {
            ext.put("errorRef", errorRef);
          }
          ext.put("eventType", "ERROR");
        }
        case "signaleventdefinition" -> {
          String signalRef = e.getAttribute("signalRef");
          if (signalRef != null && !signalRef.isBlank()) {
            ext.put("signalRef", signalRef);
          }
          ext.put("eventType", "SIGNAL");
        }
        case "messageeventdefinition" -> {
          String messageRef = e.getAttribute("messageRef");
          if (messageRef != null && !messageRef.isBlank()) {
            ext.put("messageRef", messageRef);
          }
          ext.put("eventType", "MESSAGE");
        }
        case "canceleventdefinition" -> ext.put("cancelEvent", true);
        case "compensateeventdefinition" -> {
          String activityRef = e.getAttribute("activityRef");
          if (activityRef != null && !activityRef.isBlank()) {
            ext.put("compensateActivityRef", activityRef);
          }
        }
        default -> {
          /* ignore */
        }
      }
    }
    if (hasTimer) {
      // 标记此节点为 timer 类型，前端可视化需要区分
      ext.put("nodeFeature", "TIMER");
    }
  }

  /**
   * P0-4: 解析通用 extensionElements
   *
   * @param elem 父 BPMN 元素
   * @param ext 扩展属性 Map（输出参数）
   */
  private void parseExtensionElements(Element elem, Map<String, Object> ext) {
    Element extElems = bpmnElementHelper.findChild(elem, "extensionElements");
    if (extElems == null) {
      return;
    }
    NodeList children = extElems.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (!(n instanceof Element e)) {
        continue;
      }
      String local = e.getLocalName();
      if (local == null) {
        local = e.getNodeName();
      }
      // 收集所有自定义属性为键值对
      Map<String, String> attrs = new HashMap<>();
      if (e.hasAttributes()) {
        var attrMap = e.getAttributes();
        for (int j = 0; j < attrMap.getLength(); j++) {
          Node a = attrMap.item(j);
          attrs.put(a.getNodeName(), a.getNodeValue());
        }
      }
      String text = e.getTextContent();
      if (text != null && !text.isBlank()) {
        attrs.put("_text", text.trim());
      }
      ext.put("ext_" + local, attrs);
    }
  }

  /**
   * 解析 userTask 的多实例（会签）配置
   *
   * @param userTask userTask 元素
   * @param node 流程节点（输出参数）
   * @param ext 扩展属性 Map（输出参数）
   */
  private void parseMultiInstance(Element userTask, FlowNodeVO node, Map<String, Object> ext) {
    NodeList children = userTask.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n instanceof Element e
          && "multiInstanceLoopCharacteristics".equalsIgnoreCase(e.getLocalName())) {
        String performType = "PARALLEL";
        String collection =
            e.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "collection");
        String elementVariable =
            e.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "elementVariable");
        // GAP-P2-10: foreach="true" 标记为 FOREACH 循环节点（独立 task 模式）
        String foreachFlag =
            e.getAttributeNS(BpmnElementHelper.BPMN_EXT_NS, "foreach");
        boolean isForeach = "true".equalsIgnoreCase(foreachFlag);
        NodeList miChildren = e.getChildNodes();
        for (int j = 0; j < miChildren.getLength(); j++) {
          Node mc = miChildren.item(j);
          if (mc instanceof Element me) {
            String ml = me.getLocalName();
            if ("completionCondition".equalsIgnoreCase(ml)) {
              String expr = me.getTextContent();
              if (expr != null && !expr.isBlank()) {
                node.setSkipAnyNode(expr.trim());
              }
            } else if ("loopCardinality".equalsIgnoreCase(ml)) {
              String card = me.getTextContent();
              if (card != null && !card.isBlank()) {
                ext.put("loopCardinality", card.trim());
              }
            } else if ("loopDataInputRef".equalsIgnoreCase(ml)) {
              String data = me.getTextContent();
              if (data != null && !data.isBlank()) {
                ext.put("loopDataInputRef", data.trim());
              }
            }
          }
        }
        // GAP-P2-10: FOREACH 模式 — 覆盖 nodeType 和 performType（精简为 PARALLEL）
        if (isForeach) {
          node.setNodeType(FlowNodeType.FOREACH.getCode());
          performType = FlowPerformType.PARALLEL.name();
          ext.put("multiInstance", "FOREACH");
        } else {
          ext.put("multiInstance", performType);
        }
        // 写入 performType
        if (ext.get("performType") == null) {
          ext.put("performType", performType);
        }
        if (collection != null && !collection.isBlank()) {
          ext.put("collection", collection);
        }
        if (elementVariable != null && !elementVariable.isBlank()) {
          ext.put("elementVariable", elementVariable);
        }
        return;
      }
    }
  }
}
