package com.njydsz.workflow.server.engine;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import com.njydsz.common.core.code.BaseResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.infra.entity.FlowNodeDO;
import com.njydsz.workflow.infra.entity.FlowSkipDO;

/**
 * BPMN 2.0 解析器（门面类）
 *
 * <p>基于 JDK 内置 DOM 解析器，将标准 BPMN 2.0 XML 转换为 ydsz_flow_node / ydsz_flow_skip 等价模型。
 *
 * <p>支持元素：
 *
 * <ul>
 *   <li>{@code <process id name isExecutable>} - 流程根
 *   <li>{@code <startEvent>}、{@code <endEvent>} - 开始/结束
 *   <li>{@code <userTask>}、{@code <serviceTask>} - 任务节点
 *   <li>{@code <exclusiveGateway>}、{@code <parallelGateway>}、{@code <inclusiveGateway>} - 网关
 *   <li>{@code <sequenceFlow id sourceRef targetRef>} - 跳转边
 *   <li>{@code <conditionExpression xsi:type="tFormalExpression">${...}</conditionExpression>} - 条件
 *   <li>{@code flowable:assignee}、{@code flowable:candidateUsers}、{@code flowable:candidateGroups}
 *       - 办理人（兼容 BPMN 扩展命名空间）
 * </ul>
 *
 * <p>P0-4: 扩展属性完善，新增：
 *
 * <ul>
 *   <li>flowable:priority - 任务优先级（1-100）
 *   <li>flowable:async - 是否异步执行（true/false）
 *   <li>flowable:assigneeType - 办理人类型（SELF_SELECT/MULTI_LEADER/...）
 *   <li>flowable:performType - 会签类型（OR/PARALLEL）
 *   <li>flowable:approveCount - 会签通过人数
 *   <li>flowable:approveRate - 通过率（0-100）
 *   <li>flowable:weight - 加权值
 *   <li>flowable:timeoutStrategy - 超时策略（PASS/REJECT/NOTIFY/ESCALATE）
 *   <li>flowable:timeout - 超时时长（如 24h/2d）
 *   <li>flowable:escalateUser - 升级办理人（EscalateUser）
 *   <li>flowable:skipAnyNode - OR 会签条件
 *   <li>timerEventDefinition / timerCycle - 定时器节点与边界定时
 *   <li>errorEventDefinition - 错误事件
 *   <li>signalEventDefinition/messageEventDefinition - 信号/消息事件
 *   <li>extensionElements - 任意自定义扩展（写入 ext JSON）
 * </ul>
 *
 * <p>不依赖任何第三方 BPMN 库，零外部依赖。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BpmnXmlParser {

  private final BpmnElementHelper bpmnElementHelper;
  private final BpmnNodeParser bpmnNodeParser;
  private final BpmnSkipParser bpmnSkipParser;
  private final BpmnDiagramParser bpmnDiagramParser;

  /**
   * 解析 BPMN 2.0 XML
   *
   * @param bpmnXml BPMN XML 字符串
   * @return 解析后的 BpmnModel
   */
  public BpmnModel parse(String bpmnXml) {
    if (bpmnXml == null || bpmnXml.isBlank()) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_30c8dc03")
          .build();
    }
    Document doc = parseDocument(bpmnXml);
    Element root = doc.getDocumentElement();
    if (!"definitions".equalsIgnoreCase(root.getLocalName())) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_a2ed268d")
          .params(root.getLocalName())
          .build();
    }

    // 找 <process> 节点
    Element process = bpmnElementHelper.findChild(root, "process");
    if (process == null) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_d7f0848f")
          .build();
    }

    BpmnModel model = new BpmnModel();
    model.setProcessId(process.getAttribute("id"));
    model.setProcessName(process.getAttribute("name"));
    if (model.getProcessName() == null || model.getProcessName().isBlank()) {
      model.setProcessName(model.getProcessId());
    }

    // 解析所有 BPMN 节点元素
    List<FlowNodeDO> nodes = new ArrayList<>();
    List<FlowSkipDO> skips = new ArrayList<>();
    NodeList children = process.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node node = children.item(i);
      if (!(node instanceof Element elem)) {
        continue;
      }
      String local = elem.getLocalName();
      if (local == null) {
        local = elem.getNodeName();
      }
      if (bpmnElementHelper.isFlowNode(local)) {
        FlowNodeDO nodeDo = bpmnNodeParser.parseNode(elem, local);
        if (nodeDo != null) {
          nodes.add(nodeDo);
        }
      } else if ("sequenceFlow".equalsIgnoreCase(local)) {
        FlowSkipDO skip = bpmnSkipParser.parseSkip(elem);
        if (skip != null) {
          skips.add(skip);
        }
      }
    }

    // 补全 skip.nextNodeType
    Map<String, FlowNodeDO> nodeByCode = new HashMap<>();
    for (FlowNodeDO n : nodes) {
      nodeByCode.put(n.getNodeCode(), n);
    }
    for (FlowSkipDO s : skips) {
      FlowNodeDO target = nodeByCode.get(s.getNextNodeCode());
      if (target != null) {
        s.setNextNodeType(target.getNodeType());
      }
    }

    // 校验：节点编码唯一
    if (nodeByCode.size() != nodes.size()) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_d60cd229")
          .build();
    }
    // 校验：必须含开始节点
    boolean hasStart =
        nodes.stream().anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
    if (!hasStart) {
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .message("error.workflow.msg_a2f0efff")
          .build();
    }

    model.setNodes(nodes);
    model.setSkips(skips);

    // P3-1: 解析 BPMN 2.0 BPMNDI 段，提取节点/边的可视化坐标
    // （用于驱动流程图回放与 SVG 可视化高亮）
    Map<String, BpmnModel.NodeCoordinate> nodeCoords = new HashMap<>();
    Map<String, List<BpmnModel.NodeCoordinate>> skipCoords = new HashMap<>();
    bpmnDiagramParser.parseBpmnDiagram(root, nodeCoords, skipCoords);
    model.setNodeCoordinates(nodeCoords);
    model.setSkipCoordinates(skipCoords);

    log.info(
        "[BpmnParser] 解析完成: processId={} nodes={} skips={} withCoords={} edgeCoords={}",
        model.getProcessId(),
        nodes.size(),
        skips.size(),
        nodeCoords.size(),
        skipCoords.size());
    return model;
  }

  /**
   * 将 BPMN XML 字符串解析为 DOM Document
   *
   * @param xml XML 字符串
   * @return DOM Document 对象
   */
  private Document parseDocument(String xml) {
    try {
      // 安全：禁止外部实体注入（XXE）
      DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
      factory.setNamespaceAware(true);
      factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
      factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
      factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
      factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
      factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
      DocumentBuilder builder = factory.newDocumentBuilder();
      return builder.parse(new InputSource(new StringReader(xml)));
    } catch (SysException e) {
      throw e;
    } catch (SAXException e) {
      // XML 格式错误：不可重试（需修改 XML 内容）
      log.error("[BpmnParser] XML 格式错误: {}", e.getMessage());
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_3db1015b")
          .params("XML 格式错误: " + e.getMessage())
          .build();
    } catch (ParserConfigurationException e) {
      // 解析器配置错误：不可重试（JVM 环境问题）
      log.error("[BpmnParser] 解析器配置异常: {}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(BaseResultCode.SYSTEM_ERROR)
          .key("error.workflow.msg_3db1015b")
          .params("解析器配置异常: " + e.getMessage())
          .build();
    } catch (IOException e) {
      // IO 异常：理论上 StringReader 不会触发，作为兜底
      log.error("[BpmnParser] IO 异常: {}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(BaseResultCode.SYSTEM_ERROR)
          .key("error.workflow.msg_3db1015b")
          .params("读取异常: " + e.getMessage())
          .build();
    } catch (Exception e) {
      // 兜底：未知异常
      log.error("[BpmnParser] 解析失败(未知): {}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(BaseResultCode.BAD_REQUEST)
          .key("error.workflow.msg_3db1015b")
          .params(e.getMessage())
          .build();
    }
  }
}
