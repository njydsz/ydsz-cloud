package com.njydsz.workflow.server.engine;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
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

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.workflow.domain.enums.FlowNodeType;
import com.njydsz.workflow.domain.vo.FlowNodeVO;
import com.njydsz.workflow.domain.vo.FlowSkipVO;

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
 *   <li>{@code conditionExpression xsi:type="tFormalExpression"} - 条件
 *   <li>{@code ydsz:assignee}、{@code ydsz:candidateUsers}、{@code ydsz:candidateGroups}
 *       - 办理人（兼容 BPMN 扩展命名空间）
 * </ul>
 *
 * <p>P0-4: 扩展属性完善，新增：
 *
 * <ul>
 *   <li>ydsz:priority - 任务优先级（1-100）
 *   <li>ydsz:async - 是否异步执行（true/false）
 *   <li>ydsz:assigneeType - 办理人类型（SELF_SELECT/MULTI_LEADER/...）
 *   <li>ydsz:performType - 会签类型（OR/PARALLEL）
 *   <li>ydsz:approveCount - 会签通过人数
 *   <li>ydsz:approveRate - 通过率（0-100）
 *   <li>ydsz:weight - 加权值
 *   <li>ydsz:timeoutStrategy - 超时策略（PASS/REJECT/NOTIFY/ESCALATE）
 *   <li>ydsz:timeout - 超时时长（如 24h/2d）
 *   <li>ydsz:escalateUser - 升级办理人（EscalateUser）
 *   <li>ydsz:skipAnyNode - OR 会签条件
 *   <li>timerEventDefinition / timerCycle - 定时器节点与边界定时
 *   <li>errorEventDefinition - 错误事件
 *   <li>signalEventDefinition/messageEventDefinition - 信号/消息事件
 *   <li>extensionElements - 任意自定义扩展（写入 ext JSON）
 * </ul>
 *
 * <p>不依赖任何第三方 BPMN 库，零外部兼容。
 *
 * @since 26.09.01
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
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.bpmn.xml.empty")
          .build();
    }
    Document doc = parseDocument(bpmnXml);
    Element root = validateRootElement(doc);
    Element process = findProcessOrThrow(root);

    BpmnModel model = buildBpmnModel(process);
    // 解析所有 BPMN 节点和跳转元素
    NodeList children = process.getChildNodes();
    parseProcessChildren(children, model);

    // 补全校验
    validateParseResult(model);

    // P3-1: 解析 BPMN 2.0 BPMNDI 段，提取节点/边的可视化坐标
    parseDiagramCoordinates(root, model);

    log.info("[BpmnParser] 解析完成: processId={} nodes={} skips={} withCoords={} edgeCoords={}",
        model.getProcessId(), model.getNodes().size(), model.getSkips().size(),
        model.getNodeCoordinates() != null ? model.getNodeCoordinates().size() : 0,
        model.getSkipCoordinates() != null ? model.getSkipCoordinates().size() : 0);
    return model;
  }

  /**
   * 校验根元素为 definitions。
   *
   * @param doc BPMN XML DOM 文档
   * @return definitions 根元素
   */
  private Element validateRootElement(Document doc) {
    Element root = doc.getDocumentElement();
    if (!"definitions".equalsIgnoreCase(root.getLocalName())) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.bpmn.root.invalid")
          .params(root.getLocalName())
          .build();
    }
    return root;
  }

  /**
   * 查找 process 元素，不存在则抛出异常。
   *
   * @param root definitions 根元素
   * @return process 元素
   */
  private Element findProcessOrThrow(Element root) {
    Element process = bpmnElementHelper.findChild(root, "process");
    if (process == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.bpmn.process.not.found")
          .build();
    }
    return process;
  }

  /**
   * 根据 process 元素构建 BpmnModel（processId / processName）。
   *
   * @param process process 元素
   * @return 构建的 BpmnModel（含 processId/processName）
   */
  private BpmnModel buildBpmnModel(Element process) {
    BpmnModel model = new BpmnModel();
    model.setProcessId(process.getAttribute("id"));
    model.setProcessName(process.getAttribute("name"));
    if (model.getProcessName() == null || model.getProcessName().isBlank()) {
      model.setProcessName(model.getProcessId());
    }
    return model;
  }

  /**
   * 引擎不支持但具备流程语义的 BPMN 元素（部署时 fail-fast，禁止静默忽略）。
   *
   * <p>这些元素一旦出现在流程定义中，意味着流程无法按设计执行，静默丢弃会造成
   * "定义与运行时行为不一致"的线上事故。解析阶段直接拒绝并提示原因。
   */
  private static final Set<String> UNSUPPORTED_SEMANTIC_ELEMENTS = Set.of(
      // 任务类
      "businessruletask", "sendtask", "task", "transaction", "adhocsubprocess",
      // 事件类（当前运行时未执行，仅解析）
      "terminateeventdefinition", "linkeventdefinition", "conditionalEventDefinition");

  /**
   * 合法但可安全忽略的容器/文档类元素（不影响执行语义）。
   *
   * <p>如协作、泳道、数据对象、注释、图元等，BPMN 工具导出时普遍存在，解析时跳过。
   */
  private static final Set<String> IGNORABLE_ELEMENTS = Set.of(
      "collaboration", "participant", "messages", "signals", "errors", "interfaces",
      "laneSet", "lane", "dataObject", "dataObjectReference", "dataStoreReference",
      "textAnnotation", "association", "group", "documentation", "extensionElements",
      "import", "relationship", "category", "rootElement");

  /**
   * 解析 process 的子元素，填充 nodes 和 skips。
   *
   * @param children process 的全部子节点列表
   * @param model 待填充的 BpmnModel
   */
  private void parseProcessChildren(NodeList children, BpmnModel model) {
    List<FlowNodeVO> nodes = new ArrayList<>(children.getLength());
    List<FlowSkipVO> skips = new ArrayList<>(children.getLength());
    model.setNodes(nodes);
    model.setSkips(skips);

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
        FlowNodeVO nodeDo = bpmnNodeParser.parseNode(elem, local);
        if (nodeDo != null) {
          nodes.add(nodeDo);
        }
      } else if ("sequenceFlow".equalsIgnoreCase(local)) {
        FlowSkipVO skip = bpmnSkipParser.parseSkip(elem);
        if (skip != null) {
          skips.add(skip);
        }
      } else {
        // fail-fast：拒绝"具备流程语义但引擎不支持"的元素，防止静默丢弃
        String normalized = local.toLowerCase();
        if (UNSUPPORTED_SEMANTIC_ELEMENTS.contains(normalized)) {
          log.warn("[BpmnParser] 流程定义包含不支持的 BPMN 元素，拒绝部署: <{}>", local);
          throw SysException.builder()
              .resultCode(YdszResultCode.BAD_REQUEST)
              .key("error.workflow.msg_unsupported_bpmn_element")
              .params(local)
              .build();
        }
        if (IGNORABLE_ELEMENTS.contains(normalized)) {
          // 合法容器/文档元素，静默跳过
          continue;
        }
        // 其他未知元素：记录 debug，不阻断（兼容工具导出的扩展元素）
        log.debug("[BpmnParser] 忽略未知 process 子元素: <{}>", local);
      }
    }
    fillSkipNextNodeType(nodes, skips);
  }

  /**
   * 补全 skip.nextNodeType 字段。
   *
   * @param nodes 节点列表
   * @param skips 跳转列表（将被补全 nextNodeType）
   */
  private void fillSkipNextNodeType(List<FlowNodeVO> nodes, List<FlowSkipVO> skips) {
    Map<String, FlowNodeVO> nodeByCode = new HashMap<>(nodes.size());
    for (FlowNodeVO n : nodes) {
      nodeByCode.put(n.getNodeCode(), n);
    }
    for (FlowSkipVO s : skips) {
      FlowNodeVO target = nodeByCode.get(s.getNextNodeCode());
      if (target != null) {
        s.setNextNodeType(target.getNodeType());
      }
    }
  }

  /**
   * 校验解析结果：节点编码唯一且必须含开始节点。
   *
   * @param model 解析生成的 BpmnModel
   */
  private void validateParseResult(BpmnModel model) {
    List<FlowNodeVO> nodes = model.getNodes();
    Set<String> uniqueCodes = nodes.stream().map(FlowNodeVO::getNodeCode).collect(Collectors.toSet());
    if (uniqueCodes.size() != nodes.size()) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.bpmn.node.duplicate")
          .build();
    }
    boolean hasStart = nodes.stream().anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
    if (!hasStart) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.bpmn.start.missing")
          .build();
    }
  }

  /**
   * 解析 BPMNDI 段，提取节点坐标信息（用于流程图可视化高亮）。
   *
   * @param root definitions 根元素
   * @param model 待填充坐标的 BpmnModel
   */
  private void parseDiagramCoordinates(Element root, BpmnModel model) {
    Map<String, BpmnModel.NodeCoordinate> nodeCoords = new HashMap<>(model.getNodes().size());
    Map<String, List<BpmnModel.NodeCoordinate>> skipCoords = new HashMap<>(model.getSkips().size());
    bpmnDiagramParser.parseBpmnDiagram(root, nodeCoords, skipCoords);
    model.setNodeCoordinates(nodeCoords);
    model.setSkipCoordinates(skipCoords);
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
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.bpmn.parse.failed")
          .params("XML 格式错误: " + e.getMessage())
          .build();
    } catch (ParserConfigurationException e) {
      // 解析器配置错误：不可重试（JVM 环境问题）
      log.error("[BpmnParser] 解析器配置异常: {}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.bpmn.parse.failed")
          .params("解析器配置异常: " + e.getMessage())
          .build();
    } catch (IOException e) {
      // IO 异常：理论上 StringReader 不会触发，作为兜底
      log.error("[BpmnParser] IO 异常: {}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.INTERNAL_ERROR)
          .key("error.workflow.bpmn.parse.failed")
          .params("读取异常: " + e.getMessage())
          .build();
    } catch (Exception e) {
      // 兜底：未知异常
      log.error("[BpmnParser] 解析失败(未知): {}", e.getMessage(), e);
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .key("error.workflow.bpmn.parse.failed")
          .params(e.getMessage())
          .build();
    }
  }
}
