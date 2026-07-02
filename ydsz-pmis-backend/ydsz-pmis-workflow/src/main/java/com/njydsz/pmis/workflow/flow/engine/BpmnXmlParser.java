package com.njydsz.pmis.workflow.flow.engine;

import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.flow.enums.FlowPerformType;
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
 * <p>P0-4: 扩展属性完善，新增：
 * <ul>
 *   <li>flowable:priority - 任务优先级（1-100）</li>
 *   <li>flowable:async - 是否异步执行（true/false）</li>
 *   <li>flowable:assigneeType - 办理人类型（SELF_SELECT/MULTI_LEADER/...）</li>
 *   <li>flowable:performType - 会签类型（OR/SEQUENTIAL/PARALLEL/VOTE）</li>
 *   <li>flowable:approveCount - 会签通过人数/票数</li>
 *   <li>flowable:approveRate - VOTE 通过率（0-100）</li>
 *   <li>flowable:weight - 加权值</li>
 *   <li>flowable:timeoutStrategy - 超时策略（PASS/REJECT/NOTIFY/ESCALATE）</li>
 *   <li>flowable:timeout - 超时时长（如 24h/2d）</li>
 *   <li>flowable:escalateUser - 升级办理人（EscalateUser）</li>
 *   <li>flowable:skipAnyNode - OR 会签条件</li>
 *   <li>timerEventDefinition / timerCycle - 定时器节点与边界定时</li>
 *   <li>errorEventDefinition - 错误事件</li>
 *   <li>signalEventDefinition/messageEventDefinition - 信号/消息事件</li>
 *   <li>extensionElements - 任意自定义扩展（写入 ext JSON）</li>
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

        // P0-4: 扩展属性
        String priorityStr = elem.getAttributeNS(BPMN_EXT_NS, "priority");
        String async = elem.getAttributeNS(BPMN_EXT_NS, "async");
        String assigneeType = elem.getAttributeNS(BPMN_EXT_NS, "assigneeType");
        String performType = elem.getAttributeNS(BPMN_EXT_NS, "performType");
        String approveCountStr = elem.getAttributeNS(BPMN_EXT_NS, "approveCount");
        String approveRateStr = elem.getAttributeNS(BPMN_EXT_NS, "approveRate");
        String weightStr = elem.getAttributeNS(BPMN_EXT_NS, "weight");
        String timeoutStrategy = elem.getAttributeNS(BPMN_EXT_NS, "timeoutStrategy");
        String timeout = elem.getAttributeNS(BPMN_EXT_NS, "timeout");
        String escalateUser = elem.getAttributeNS(BPMN_EXT_NS, "escalateUser");
        String skipAnyNode = elem.getAttributeNS(BPMN_EXT_NS, "skipAnyNode");

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

        // P0-4: 解析 priority（存入 ext，见第 350 行处理 approveCount）
        if (priorityStr != null && !priorityStr.isBlank()) {
            try {
                int p = Integer.parseInt(priorityStr.trim());
                if (p < 1) p = 1;
                if (p > 100) p = 100;
                // priority 作为 approveCount 的备选值，存入 ext
            } catch (NumberFormatException ignore) {
                // ignore invalid priority
            }
        }

        // P0-4: 会签类型与扩展字段
        if (performType != null && !performType.isBlank()) {
            try {
                FlowPerformType pt = FlowPerformType.valueOf(performType.trim().toUpperCase());
                // 复用 skipAnyNode 字段存储会签类型（service 上挂 ext 表达）
                if (node.getSkipAnyNode() == null || node.getSkipAnyNode().isBlank()) {
                    node.setSkipAnyNode(pt.name());
                }
            } catch (IllegalArgumentException ignore) {
                // invalid perform type, ignore
            }
        }
        // approveCount 已在第 350 行存入 ext JSON，此处无需额外处理

        // 把所有扩展属性塞入 ext JSON（统一持久化）
        Map<String, Object> ext = readOrInitExt(node);
        if (formKey != null && !formKey.isBlank()) ext.put("formKey", formKey);
        if (dueDate != null && !dueDate.isBlank()) ext.put("dueDate", dueDate);
        if (async != null && !async.isBlank()) ext.put("async", Boolean.parseBoolean(async.trim()));
        if (assigneeType != null && !assigneeType.isBlank()) ext.put("assigneeType", assigneeType.trim());
        if (performType != null && !performType.isBlank()) ext.put("performType", performType.trim());
        if (approveCountStr != null && !approveCountStr.isBlank()) ext.put("approveCount", approveCountStr.trim());
        if (approveRateStr != null && !approveRateStr.isBlank()) ext.put("approveRate", approveRateStr.trim());
        if (weightStr != null && !weightStr.isBlank()) ext.put("weight", weightStr.trim());
        if (timeoutStrategy != null && !timeoutStrategy.isBlank()) ext.put("timeoutStrategy", timeoutStrategy.trim());
        if (timeout != null && !timeout.isBlank()) ext.put("timeout", timeout.trim());
        if (escalateUser != null && !escalateUser.isBlank()) ext.put("escalateUser", escalateUser.trim());
        if (skipAnyNode != null && !skipAnyNode.isBlank()) ext.put("skipAnyNode", skipAnyNode.trim());

        // P0-4: timer / error / signal / message 事件定义
        parseEventDefinitions(elem, ext);

        // P0-4: 通用 extensionElements（用户自定义键值对）
        parseExtensionElements(elem, ext);

        node.setExt(JsonHelper.toJson(ext));

        // 处理 userTask 的多实例特性（会签）
        if ("userTask".equalsIgnoreCase(localName)) {
            parseMultiInstance(elem, node, ext);
            node.setExt(JsonHelper.toJson(ext));
        }
        return node;
    }

    /**
     * P0-4: 解析 timer / error / signal / message 事件定义
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
            if (local == null) local = e.getNodeName();
            switch (local.toLowerCase()) {
                case "timereventdefinition" -> {
                    hasTimer = true;
                    Map<String, Object> timer = new HashMap<>();
                    Element timeCycle = findChild(e, "timeCycle");
                    Element timeDate = findChild(e, "timeDate");
                    Element timeDuration = findChild(e, "timeDuration");
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
                }
                case "signaleventdefinition" -> {
                    String signalRef = e.getAttribute("signalRef");
                    if (signalRef != null && !signalRef.isBlank()) {
                        ext.put("signalRef", signalRef);
                    }
                }
                case "messageeventdefinition" -> {
                    String messageRef = e.getAttribute("messageRef");
                    if (messageRef != null && !messageRef.isBlank()) {
                        ext.put("messageRef", messageRef);
                    }
                }
                case "canceleventdefinition" -> ext.put("cancelEvent", true);
                case "compensateeventdefinition" -> {
                    String activityRef = e.getAttribute("activityRef");
                    if (activityRef != null && !activityRef.isBlank()) {
                        ext.put("compensateActivityRef", activityRef);
                    }
                }
                default -> { /* ignore */ }
            }
        }
        if (hasTimer) {
            // 标记此节点为 timer 类型，前端可视化需要区分
            ext.put("nodeFeature", "TIMER");
        }
    }

    /**
     * P0-4: 解析通用 extensionElements
     */
    private void parseExtensionElements(Element elem, Map<String, Object> ext) {
        Element extElems = findChild(elem, "extensionElements");
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
            if (local == null) local = e.getNodeName();
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
     */
    private void parseMultiInstance(Element userTask, FlowNodeDO node, Map<String, Object> ext) {
        NodeList children = userTask.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (n instanceof Element e && "multiInstanceLoopCharacteristics".equalsIgnoreCase(e.getLocalName())) {
                String performType = "PARALLEL";
                String collection = e.getAttributeNS(BPMN_EXT_NS, "collection");
                String elementVariable = e.getAttributeNS(BPMN_EXT_NS, "elementVariable");
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
                ext.put("multiInstance", performType);
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
        Map<String, Object> ext = new HashMap<>();
        ext.put("sourceRef", sourceRef);
        ext.put("targetRef", targetRef);
        ext.put("sequenceFlowId", elem.getAttribute("id"));
        // P0-4: 边上的 flowable:skipExpression（条件）
        String skipExpr = elem.getAttributeNS(BPMN_EXT_NS, "skipExpression");
        if (skipExpr != null && !skipExpr.isBlank()) {
            ext.put("skipExpression", skipExpr);
        }
        // 边的优先级（多出口时排序依据）
        String priority = elem.getAttributeNS(BPMN_EXT_NS, "priority");
        if (priority != null && !priority.isBlank()) {
            ext.put("priority", priority.trim());
        }
        skip.setExt(JsonHelper.toJson(ext));
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
                 "receivetask" -> FlowNodeType.APPROVAL.getCode();
            case "callactivity", "subprocess" -> FlowNodeType.SUBPROCESS.getCode();
            case "exclusivegateway", "eventbasedgateway", "complexgateway" -> FlowNodeType.CONDITION.getCode();
            case "parallelgateway" -> FlowNodeType.PARALLEL.getCode();
            case "inclusivegateway" -> FlowNodeType.INCLUSIVE.getCode();
            case "intermediatethrowevent", "intermediatecatchevent", "boundaryevent" -> FlowNodeType.CC.getCode();
            default -> FlowNodeType.APPROVAL.getCode();
        };
    }

    // ============== 工具方法 ==============

    private Map<String, Object> readOrInitExt(FlowNodeDO node) {
        Map<String, Object> map = new HashMap<>();
        String ext = node.getExt();
        if (ext != null && !ext.isBlank() && !"{}".equals(ext.trim())) {
            try {
                Map<String, Object> parsed = JsonHelper.fromJson(ext);
                if (parsed != null) {
                    map.putAll(parsed);
                }
            } catch (Exception ignore) {
                // ignore
            }
        }
        return map;
    }
}
