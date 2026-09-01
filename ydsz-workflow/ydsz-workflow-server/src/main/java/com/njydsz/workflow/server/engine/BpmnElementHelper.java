package com.njydsz.workflow.server.engine;

import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.enums.WorkflowExceptionCode;
import com.njydsz.workflow.domain.vo.FlowNodeVO;

/**
 * BPMN 元素工具类
 *
 * <p>提供 BPMN XML 解析过程中通用的 DOM 查找、类型映射、属性读取等工具方法。
 *
 * <p>所有方法均为无状态纯函数，线程安全。作为 Spring Bean 可被注入到各解析器中使用。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
@Slf4j
@Component
public class BpmnElementHelper {

  /** BPMN 扩展属性命名空间 */
  public static final String BPMN_EXT_NS = "http://ydsz.org/bpmn";

  /**
   * 是否为流程节点元素（BPMN 标准节点类型）
   *
   * @param localName 元素本地名称
   * @return true 表示该元素为流程节点
   */
  public boolean isFlowNode(String localName) {
    return "startEvent".equalsIgnoreCase(localName)
        || "endEvent".equalsIgnoreCase(localName)
        || "intermediateThrowEvent".equalsIgnoreCase(localName)
        || "intermediateCatchEvent".equalsIgnoreCase(localName)
        || "boundaryEvent".equalsIgnoreCase(localName)
        || "userTask".equalsIgnoreCase(localName)
        || "serviceTask".equalsIgnoreCase(localName)
        || "scriptTask".equalsIgnoreCase(localName)
        || "manualTask".equalsIgnoreCase(localName)
        || "receiveTask".equalsIgnoreCase(localName)
        || "callActivity".equalsIgnoreCase(localName)
        || "subProcess".equalsIgnoreCase(localName)
        || "exclusiveGateway".equalsIgnoreCase(localName)
        || "parallelGateway".equalsIgnoreCase(localName)
        || "inclusiveGateway".equalsIgnoreCase(localName)
        || "eventBasedGateway".equalsIgnoreCase(localName)
        || "complexGateway".equalsIgnoreCase(localName);
  }

  /**
   * BPMN 元素名 → FlowNodeType 编码
   *
   * @param localName BPMN 元素本地名称
   * @return FlowNodeType 编码值
   * @throws BusinessException 当元素未在 {@link #isFlowNode} 白名单中时（防御性 fail-fast）
   */
  public int mapNodeType(String localName) {
    return switch (localName.toLowerCase()) {
      case "startevent" -> FlowNodeType.START.getCode();
      case "endevent" -> FlowNodeType.END.getCode();
        // P1-4: serviceTask / scriptTask 映射为 SERVICE(8)，自动执行不创建人工任务
      case "servicetask", "scripttask" -> FlowNodeType.SERVICE.getCode();
        // manualTask / receiveTask 确实需要人工处理，保持映射为 APPROVAL(1)
      case "usertask", "manualtask", "receivetask" -> FlowNodeType.APPROVAL.getCode();
      case "callactivity", "subprocess" -> FlowNodeType.SUBPROCESS.getCode();
        // P1-3: eventBasedGateway 映射为 CONDITION，complexGateway 映射为 INCLUSIVE
        // 引擎使用现有网关逻辑处理，ext.gatewayType 标记原始类型供未来扩展
      case "eventbasedgateway" -> FlowNodeType.CONDITION.getCode();
      case "complexgateway" -> FlowNodeType.INCLUSIVE.getCode();
      case "exclusivegateway" -> FlowNodeType.CONDITION.getCode();
      case "parallelgateway" -> FlowNodeType.PARALLEL.getCode();
      case "inclusivegateway" -> FlowNodeType.INCLUSIVE.getCode();
      case "intermediatethrowevent", "intermediatecatchevent", "boundaryevent" ->
          FlowNodeType.CC.getCode();
      default -> {
        // 防御性 fail-fast：isFlowNode 白名单外的元素不应到达此处
        log.warn("[BpmnElementHelper] 未知 BPMN 节点元素，拒绝静默降级: {}", localName);
        throw BusinessException.builder()
            .resultCode(WorkflowExceptionCode.UNSUPPORTED_BPMN_ELEMENT)
            .params(localName)
            .build();
      }
    };
  }

  /**
   * 查找直接子元素（忽略空白文本节点）
   *
   * @param parent 父元素
   * @param localName 目标子元素本地名称（大小写不敏感）
   * @return 匹配的子元素，未找到返回 null
   */
  public Element findChild(Element parent, String localName) {
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n instanceof Element e && localName.equalsIgnoreCase(e.getLocalName())) {
        return e;
      }
    }
    return null;
  }

  /**
   * 通用子元素查找（大小写不敏感、忽略命名空间前缀）
   *
   * @param parent 父元素
   * @param localName 目标子元素本地名称
   * @return 匹配的子元素，未找到返回 null
   */
  public Element findChildByLocalName(Element parent, String localName) {
    if (parent == null) {
      return null;
    }
    NodeList children = parent.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node n = children.item(i);
      if (n instanceof Element e) {
        String local = e.getLocalName();
        if (local == null) {
          local = e.getNodeName();
          // 去掉命名空间前缀（di:waypoint → waypoint）
          int colon = local.indexOf(':');
          if (colon >= 0 && colon + 1 < local.length()) {
            local = local.substring(colon + 1);
          }
        }
        if (localName.equalsIgnoreCase(local)) {
          return e;
        }
      }
    }
    return null;
  }

  /**
   * 读取或初始化节点的 ext JSON 为 Map
   *
   * <p>若节点已有 ext JSON 且非空，则解析为 Map；否则返回空 Map。
   *
   * @param node 流程节点
   * @return ext 属性对应的 Map（非 null）
   */
  public Map<String, Object> readOrInitExt(FlowNodeVO node) {
    Map<String, Object> map = new HashMap<>();
    String ext = node.getExt();
    if (ext != null && !ext.isBlank() && !"{}".equals(ext.trim())) {
      try {
        Map<String, Object> parsed = YdszJson.parseMap(ext);
        if (parsed != null) {
          map.putAll(parsed);
        }
      } catch (Exception ignore) {
        // ignore
      }
    }
    return map;
  }

  /**
   * 安全解析 double，空字符串或 null 返回 0
   *
   * @param value 字符串值
   * @return 解析后的 double 值
   */
  public double parseDouble(String value) {
    if (value == null || value.isBlank()) {
      return 0d;
    }
    return Double.parseDouble(value.trim());
  }
}
