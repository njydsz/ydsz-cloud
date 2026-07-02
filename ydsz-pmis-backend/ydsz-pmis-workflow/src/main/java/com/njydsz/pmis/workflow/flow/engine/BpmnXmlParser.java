package com.njydsz.pmis.workflow.flow.engine;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowSkipType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN 2.0 解析器
 *
 * <p>基于 JDK 内置 DOM 解析器，将标准 BPMN 2.0 XML 转换为 pmis_flow_node / pmis_flow_skip 等价模型。
 *
 * <p>支持元素：
 * <ul>
 *   <li>{@code <process id name isExecutable>} - 流程根</li>
 *   <li>{@code <startEvent>}、{@code <endEvent>} - 开始/结束</li>
 *   <li>{@code <userTask>}、{@code <serviceTask>} - 任务节点</li>
 *   <li>{@code <exclusiveGateway>}、{@code <parallelGateway>}、{@code <inclusiveGateway>} - 网关</li>
 *   <li>{@code <sequenceFlow id sourceRef targetRef>} - 跳转边</li>
 *   <li>{@code <conditionExpression xsi:type="tFormalExpression">${...}</conditionExpression>} - 条件</li>
 *   <li>{@code flowable:assignee}、{@code flowable:candidateUsers}、{@code flowable:candidateGroups} - 办理人（兼容 BPMN 扩展命名空间）</li>
 * </ul>
 *
 * <p>不依赖任何第三方 BPMN 库，零外部依赖。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class BpmnXmlParser {

    /** BPMN 默认命名空间（保留供未来扩展） */
    @SuppressWarnings("unused")
    private static final String BPMN_NS = "http://www.omg.org/spec/BPMN/20100524/MODEL";
    /** BPMNDI 命名空间（用于坐标，可选解析，保留供未来扩展） */
    @SuppressWarnings("unused")
    private static final String BPMNDI_NS = "http://www.omg.org/spec/BPMN/20100524/DI";
    /** BPMN 扩展属性命名空间（兼容 flowable/camunda/activiti 约定） */
    private static final String BPMN_EXT_NS = "http://flowable.org/bpmn";
    /** xsi 命名空间（用于 conditionExpression type，保留供未来扩展） */
    @SuppressWarnings("unused")
    private static final String XSI_NS = "http://www.w3.org/2001/XMLSchema-instance";

    /**
     * 解析 BPMN 2.0 XML
     *
     * @param bpmnXml BPMN XML 字符串
     * @return 解析后的 BpmnModel
     */
    public BpmnModel parse(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "BPMN XML 不能为空");
        }
        Document doc = parseDocument(bpmnXml);
        Element root = doc.getDocumentElement();
        if (!"definitions".equalsIgnoreCase(root.getLocalName())) {
            throw new BizException(BizErrorCode.BAD_REQUEST,
                    "BPMN 根节点必须是 <definitions>，当前: " + root.getLocalName());
        }

        // 找 <process> 节点
        Element process = findChild(root, "process");
        if (process == null) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "BPMN 中未找到 <process> 节点");
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
            if (isFlowNode(local)) {
                FlowNodeDO nodeDo = parseNode(elem, local);
                if (nodeDo != null) {
                    nodes.add(nodeDo);
                }
            } else if ("sequenceFlow".equalsIgnoreCase(local)) {
                FlowSkipDO skip = parseSkip(elem);
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
            throw new BizException(BizErrorCode.BAD_REQUEST, "BPMN 节点 id 必须唯一");
        }
        // 校验：必须含开始节点
        boolean hasStart = nodes.stream()
                .anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
        if (!hasStart) {
            throw new BizException(BizErrorCode.BAD_REQUEST, "BPMN 流程必须包含 <startEvent> 开始节点");
        }

        model.setNodes(nodes);
        model.setSkips(skips);
        log.info("[BpmnParser] 解析完成: processId={} nodes={} skips={}",
                model.getProcessId(), nodes.size(), skips.size());
        return model;
    }

    // ============== 内部解析 ==============

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
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BpmnParser] 解析失败: {}", e.getMessage());
            throw new BizException(BizErrorCode.BAD_REQUEST, "BPMN XML 解析失败: " + e.getMessage());
        }
    }

    /**
     * 是否为流程节点元素（BPMN 标准节点类型）
     */
    private boolean isFlowNode(String localName) {
        return "startEvent".equalsIgnoreCase(localName)
                || "endEvent".equalsIgnoreCase(localName)
                || "intermediateThrowEvent".equalsIgnoreCase(localName)
                || "intermediateCatchEvent".equalsIgnoreCase(localName)
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
     * 解析节点：BPMN 元素 → FlowNodeDO
     */
    private FlowNodeDO parseNode(Element elem, String localName) {
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode(elem.getAttribute("id"));
        node.setNodeName(elem.getAttribute("name"));
        if (node.getNodeName() == null || node.getNodeName().isBlank()) {
            node.setNodeName(node.getNodeCode());
        }
        node.setNodeType(mapNodeType(localName));

        // 解析 BPMN 扩展属性：assignee / candidateUsers / candidateGroups / dueDate 等
        String assignee = elem.getAttributeNS(BPMN_EXT_NS, "assignee");
        String candidateUsers = elem.getAttributeNS(BPMN_EXT_NS, "candidateUsers");
        String candidateGroups = elem.getAttributeNS(BPMN_EXT_NS, "candidateGroups");
        String expression = elem.getAttributeNS(BPMN_EXT_NS, "expression");
        String formKey = elem.getAttributeNS(BPMN_EXT_NS, "formKey");
        String dueDate = elem.getAttributeNS(BPMN_EXT_NS, "dueDate");

        // 优先级：assignee > expression > candidateUsers > candidateGroups
        if (assignee != null && !assignee.isBlank()) {
            // assignee 可以是 ${expression} 或固定 user:1001
            if (assignee.startsWith("${")) {
                node.setPermissionFlag(assignee);
            } else if (assignee.startsWith("user:") || assignee.startsWith("role:")
                    || assignee.startsWith("dept:")
                    // P2-19: 支持 leader:/position: 前缀
                    || assignee.startsWith("leader:") || assignee.startsWith("position:")
                    // P2-38/P2-39: 支持 self_select:/multi_leader: 前缀，原样保留
                    || assignee.startsWith("self_select:") || assignee.startsWith("multi_leader:")) {
                node.setPermissionFlag(assignee);
            } else {
                node.setPermissionFlag("user:" + assignee);
            }
        } else if (expression != null && !expression.isBlank()) {
            node.setPermissionFlag(expression.startsWith("${")
                    ? expression : "${" + expression + "}");
        } else if (candidateUsers != null && !candidateUsers.isBlank()) {
            // P2-15: 多候选人全部写入 permissionFlag（逗号分隔），由 expandAssignees 展开为多人
            // 例如 candidateUsers="u1,u2,u3" → permissionFlag="user:u1,user:u2,user:u3"
            String[] users = candidateUsers.split(",");
            StringBuilder perm = new StringBuilder();
            for (int i = 0; i < users.length; i++) {
                String u = users[i].trim();
                if (u.isEmpty()) continue;
                if (perm.length() > 0) perm.append(",");
                // 已带前缀则原样保留，否则补 user:
                if (u.startsWith("user:") || u.startsWith("role:")
                        || u.startsWith("dept:") || u.startsWith("leader:")
                        || u.startsWith("position:")
                        // P2-38/P2-39: 支持 self_select:/multi_leader: 前缀原样保留
                        || u.startsWith("self_select:") || u.startsWith("multi_leader:")
                        || u.startsWith("${")) {
                    perm.append(u);
                } else {
                    perm.append("user:").append(u);
                }
            }
            node.setPermissionFlag(perm.toString());
            node.setExt("{\"candidateUsers\":\"" + candidateUsers + "\"}");
        } else if (candidateGroups != null && !candidateGroups.isBlank()) {
            // P2-15: 候选组同样支持多组逗号分隔，全部写入 permissionFlag
            String[] groups = candidateGroups.split(",");
            StringBuilder perm = new StringBuilder();
            for (int i = 0; i < groups.length; i++) {
                String g = groups[i].trim();
                if (g.isEmpty()) continue;
                if (perm.length() > 0) perm.append(",");
                if (g.startsWith("role:") || g.startsWith("dept:") || g.startsWith("${")) {
                    perm.append(g);
                } else {
                    perm.append("role:").append(g);
                }
            }
            node.setPermissionFlag(perm.toString());
            node.setExt("{\"candidateGroups\":\"" + candidateGroups + "\"}");
        }

        // formKey / dueDate 写入 ext
        if (formKey != null && !formKey.isBlank()) {
            String existing = node.getExt() == null ? "{}" : node.getExt();
            node.setExt(existing.replace("}", ",\"formKey\":\"" + formKey + "\"}"));
        }
        if (dueDate != null && !dueDate.isBlank()) {
            String existing = node.getExt() == null ? "{}" : node.getExt();
            node.setExt(existing.replace("}", ",\"dueDate\":\"" + dueDate + "\"}"));
        }

        // 处理 userTask 的多实例特性（会签）
        if ("userTask".equalsIgnoreCase(localName)) {
            parseMultiInstance(elem, node);
        }
        return node;
    }

    /**
     * 解析 userTask 的多实例（会签）配置
     */
    private void parseMultiInstance(Element userTask, FlowNodeDO node) {
        NodeList children = userTask.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element e && "multiInstanceLoopCharacteristics".equalsIgnoreCase(e.getLocalName())) {
                String performType = "PARALLEL";
                NodeList miChildren = e.getChildNodes();
                for (int j = 0; j < miChildren.getLength(); j++) {
                    Node mc = miChildren.item(j);
                    if (mc instanceof Element me && "completionCondition".equalsIgnoreCase(me.getLocalName())) {
                        String expr = me.getTextContent();
                        if (expr != null && !expr.isBlank()) {
                            node.setSkipAnyNode(expr);
                        }
                    }
                }
                // 写入 ext
                String existing = node.getExt() == null ? "{}" : node.getExt();
                node.setExt(existing.replace("}", ",\"multiInstance\":\"" + performType + "\"}"));
                return;
            }
        }
    }

    /**
     * 解析 sequenceFlow：BPMN 边 → FlowSkipDO
     */
    private FlowSkipDO parseSkip(Element elem) {
        FlowSkipDO skip = new FlowSkipDO();
        skip.setSkipName(elem.getAttribute("name"));
        skip.setSkipType(FlowSkipType.PASS.name());
        // sequenceFlow 自身 id 作为 skip 唯一标识
        String sourceRef = elem.getAttribute("sourceRef");
        String targetRef = elem.getAttribute("targetRef");
        // sourceRef / targetRef 临时借用 skipName + ext 传递
        skip.setExt("{\"sourceRef\":\"" + sourceRef + "\",\"targetRef\":\""
                + targetRef + "\",\"sequenceFlowId\":\"" + elem.getAttribute("id") + "\"}");
        // nextNodeCode 暂存 targetRef，定义模型转换时会再赋
        skip.setNextNodeCode(targetRef);
        // 解析条件表达式
        Element condExpr = findChild(elem, "conditionExpression");
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

    /**
     * 查找直接子元素（忽略空白文本节点）
     */
    private Element findChild(Element parent, String localName) {
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
     * BPMN 元素名 → FlowNodeType 编码
     */
    private int mapNodeType(String localName) {
        return switch (localName.toLowerCase()) {
            case "startevent" -> FlowNodeType.START.getCode();
            case "endevent" -> FlowNodeType.END.getCode();
            case "usertask", "servicetask", "scripttask", "manualtask",
                 "receivetask", "callactivity", "subprocess" -> FlowNodeType.APPROVAL.getCode();
            case "exclusivegateway", "eventbasedgateway", "complexgateway" -> FlowNodeType.CONDITION.getCode();
            case "parallelgateway" -> FlowNodeType.PARALLEL.getCode();
            case "inclusivegateway" -> FlowNodeType.INCLUSIVE.getCode();
            case "intermediatethrowevent", "intermediatecatchevent" -> FlowNodeType.CC.getCode();
            default -> FlowNodeType.APPROVAL.getCode();
        };
    }
}
