package com.njydsz.pmis.workflow.server.engine;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import com.njydsz.pmis.common.core.response.BaseResultCode;
import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.domain.enums.FlowNodeType;
import com.njydsz.pmis.workflow.domain.enums.FlowPerformType;
import com.njydsz.pmis.workflow.domain.enums.FlowSkipType;

import lombok.extern.slf4j.Slf4j;

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
 * @since 1.0.0
 */
@Slf4j
@Component
public class BpmnXmlParser {

    /** BPMN 扩展属性命名空间（兼容 flowable/camunda/activiti 约定） */
    private static final String BPMN_EXT_NS = "http://flowable.org/bpmn";

    /**
     * 解析 BPMN 2.0 XML
     *
     * @param bpmnXml BPMN XML 字符串
     * @return 解析后的 BpmnModel
     */
    public BpmnModel parse(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_30c8dc03");
        }
        Document doc = parseDocument(bpmnXml);
        Element root = doc.getDocumentElement();
        if (!"definitions".equalsIgnoreCase(root.getLocalName())) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_a2ed268d", root.getLocalName());
        }

        // 找 <process> 节点
        Element process = findChild(root, "process");
        if (process == null) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d7f0848f");
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
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_d60cd229");
        }
        // 校验：必须含开始节点
        boolean hasStart = nodes.stream()
                .anyMatch(n -> FlowNodeType.START.getCode() == n.getNodeType());
        if (!hasStart) {
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_a2f0efff");
        }

        model.setNodes(nodes);
        model.setSkips(skips);

        // P3-1: 解析 BPMN 2.0 BPMNDI 段，提取节点/边的可视化坐标
        // （用于驱动流程图回放与 SVG 可视化高亮）
        Map<String, BpmnModel.NodeCoordinate> nodeCoords = new HashMap<>();
        Map<String, List<BpmnModel.NodeCoordinate>> skipCoords = new HashMap<>();
        parseBpmnDiagram(root, nodeCoords, skipCoords);
        model.setNodeCoordinates(nodeCoords);
        model.setSkipCoordinates(skipCoords);

        log.info("[BpmnParser] 解析完成: processId={} nodes={} skips={} withCoords={} edgeCoords={}",
                model.getProcessId(), nodes.size(), skips.size(),
                nodeCoords.size(), skipCoords.size());
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
        } catch (SysException e) {
            throw e;
        } catch (Exception e) {
            log.error("[BpmnParser] 解析失败: {}", e.getMessage());
            throw new SysException(BaseResultCode.BAD_REQUEST, "error.workflow.msg_3db1015b", e.getMessage());
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
        // P0-3: 暂未实现 eventBasedGateway / complexGateway 的行为语义
        // 历史问题：mapNodeType 静默降级为 CONDITION（互斥网关），导致流程运行行为
        // 与设计图不一致（事件网关应等待事件触发，复杂网关应基于复杂条件聚合）
        // 解析阶段直接拒绝，强制用户改用 exclusiveGateway / parallelGateway / inclusiveGateway
        String normalized = localName == null ? "" : localName.toLowerCase();
        if ("eventbasedgateway".equals(normalized) || "complexgateway".equals(normalized)) {
            throw new SysException(BaseResultCode.BAD_REQUEST,
                    "error.workflow.msg_b1a3f7c2", localName);
        }
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode(elem.getAttribute("id"));
        node.setNodeName(elem.getAttribute("name"));
        if (node.getNodeName() == null || node.getNodeName().isBlank()) {
            node.setNodeName(node.getNodeCode());
        }
        node.setNodeType(mapNodeType(localName));

        // P0-2: 网关默认出边 — BPMN 2.0 规范中 exclusiveGateway / inclusiveGateway 的
        // default 属性指向默认 sequenceFlow id（无条件匹配时走的边）。解析阶段捕获，
        // 由 DefaultFlowAdvancer.resolvePassSkips 在所有条件都不匹配时使用。
        String defaultFlowId = elem.getAttribute("default");

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

        // P0-2: 网关默认出边 — 存入 node.ext.defaultFlowId 供推进器使用
        if (defaultFlowId != null && !defaultFlowId.isBlank()) {
            ext.put("defaultFlowId", defaultFlowId.trim());
        }

        // P1-1: 解析 priority 写入 ext（任务节点优先级，1-100，待办默认按 priority DESC 排序）
        if (priorityStr != null && !priorityStr.isBlank()) {
            try {
                int p = Integer.parseInt(priorityStr.trim());
                if (p < 1) p = 1;
                if (p > 100) p = 100;
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
                // GAP-P2-10: flowable:foreach="true" 标记为 FOREACH 循环节点（独立 task 模式）
                String foreachFlag = e.getAttributeNS(BPMN_EXT_NS, "foreach");
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
                // GAP-P2-10: FOREACH 模式 — 覆盖 nodeType 和 performType
                if (isForeach) {
                    node.setNodeType(FlowNodeType.FOREACH.getCode());
                    performType = "FOREACH_PARALLEL";
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
            // P1-4: serviceTask / scriptTask 映射为 SERVICE(8)，自动执行不创建人工任务
            case "servicetask", "scripttask" -> FlowNodeType.SERVICE.getCode();
            // manualTask / receiveTask 确实需要人工处理，保持映射为 APPROVAL(1)
            case "usertask", "manualtask", "receivetask" -> FlowNodeType.APPROVAL.getCode();
            case "callactivity", "subprocess" -> FlowNodeType.SUBPROCESS.getCode();
            // P0-3: eventBasedGateway / complexGateway 在 parseNode 入口已拒绝，此处不再映射
            case "exclusivegateway" -> FlowNodeType.CONDITION.getCode();
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

    // ============== P3-1: BPMNDI 坐标解析 ==============

    /**
     * P3-1: 解析 BPMN 2.0 BPMNDI 段（Diagram Interchange），提取节点和边的可视化坐标。
     *
     * <p>BPMN XML 顶层结构（节选）：
     * <pre>
     * &lt;definitions ...&gt;
     *   &lt;process id="..."&gt;...&lt;/process&gt;
     *   &lt;BPMNDiagram id="..."&gt;
     *     &lt;BPMNPlane bpmnElement="..."&gt;
     *       &lt;BPMNShape id="..." bpmnElement="node1"&gt;
     *         &lt;Bounds x="100" y="80" width="100" height="60"/&gt;
     *       &lt;/BPMNShape&gt;
     *       &lt;BPMNEdge id="..." bpmnElement="flow1"&gt;
     *         &lt;waypoint x="200" y="110"/&gt;
     *         &lt;waypoint x="300" y="110"/&gt;
     *       &lt;/BPMNEdge&gt;
     *     &lt;/BPMNPlane&gt;
     *   &lt;/BPMNDiagram&gt;
     * &lt;/definitions&gt;
     * </pre>
     *
     * <p>解析过程：
     * <ol>
     *   <li>遍历根 &lt;definitions&gt; 找 &lt;BPMNDiagram&gt; / &lt;BPMNPlane&gt;</li>
     *   <li>遍历 &lt;BPMNShape&gt; 读取 Bounds（x/y/width/height），key = bpmnElement（节点 id）</li>
     *   <li>遍历 &lt;BPMNEdge&gt; 读取所有 waypoint（折线拐点），key = bpmnElement（边 id）</li>
     * </ol>
     *
     * <p>无 BPMNDI 段时（手写或简化 BPMN）跳过，map 保持为空，调用方需降级到自动布局。
     *
     * @param root          &lt;definitions&gt; 根节点
     * @param nodeCoords    输出：节点坐标映射（key = nodeCode）
     * @param skipCoords    输出：边坐标映射（key = sequenceFlowId）
     */
    private void parseBpmnDiagram(Element root,
                                  Map<String, BpmnModel.NodeCoordinate> nodeCoords,
                                  Map<String, List<BpmnModel.NodeCoordinate>> skipCoords) {
        if (root == null) {
            return;
        }
        // 找 <BPMNDiagram>
        Element bpmnDiagram = findChildByLocalName(root, "BPMNDiagram");
        if (bpmnDiagram == null) {
            bpmnDiagram = findChildByLocalName(root, "bpmndiagram");
        }
        if (bpmnDiagram == null) {
            log.debug("[BpmnParser] BPMN XML 未包含 <BPMNDiagram> 段，跳过坐标解析");
            return;
        }
        // 找 <BPMNPlane>
        Element bpmnPlane = findChildByLocalName(bpmnDiagram, "BPMNPlane");
        if (bpmnPlane == null) {
            bpmnPlane = findChildByLocalName(bpmnDiagram, "bpmnplane");
        }
        if (bpmnPlane == null) {
            return;
        }
        // 遍历 BPMNShape（节点）和 BPMNEdge（边）
        NodeList children = bpmnPlane.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element child)) {
                continue;
            }
            String local = child.getLocalName();
            if (local == null) {
                local = child.getNodeName();
            }
            if ("BPMNShape".equalsIgnoreCase(local) || "bpmnshape".equals(local)) {
                parseBpmnShape(child, nodeCoords);
            } else if ("BPMNEdge".equalsIgnoreCase(local) || "bpmnedge".equals(local)) {
                parseBpmnEdge(child, skipCoords);
            }
        }
    }

    /**
     * 解析 BPMNShape：提取 Bounds，key = bpmnElement（节点 id）
     */
    private void parseBpmnShape(Element shape, Map<String, BpmnModel.NodeCoordinate> nodeCoords) {
        String bpmnElement = shape.getAttribute("bpmnElement");
        if (bpmnElement == null || bpmnElement.isBlank()) {
            return;
        }
        // 找 <Bounds x y width height>
        Element bounds = findChildByLocalName(shape, "Bounds");
        if (bounds == null) {
            return;
        }
        try {
            double x = parseDouble(bounds.getAttribute("x"));
            double y = parseDouble(bounds.getAttribute("y"));
            double w = parseDouble(bounds.getAttribute("width"));
            double h = parseDouble(bounds.getAttribute("height"));
            nodeCoords.put(bpmnElement, new BpmnModel.NodeCoordinate(x, y, w, h));
        } catch (NumberFormatException nfe) {
            log.warn("[BpmnParser] BPMNShape Bounds 解析失败: bpmnElement={}", bpmnElement);
        }
    }

    /**
     * 解析 BPMNEdge：提取所有 waypoint，key = bpmnElement（边 id）
     */
    private void parseBpmnEdge(Element edge, Map<String, List<BpmnModel.NodeCoordinate>> skipCoords) {
        String bpmnElement = edge.getAttribute("bpmnElement");
        if (bpmnElement == null || bpmnElement.isBlank()) {
            return;
        }
        List<BpmnModel.NodeCoordinate> waypoints = new ArrayList<>();
        NodeList children = edge.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node n = children.item(i);
            if (!(n instanceof Element wp)) {
                continue;
            }
            String local = wp.getLocalName();
            if (local == null) {
                local = wp.getNodeName();
            }
            if ("waypoint".equalsIgnoreCase(local) || "di:waypoint".equalsIgnoreCase(local)) {
                try {
                    double x = parseDouble(wp.getAttribute("x"));
                    double y = parseDouble(wp.getAttribute("y"));
                    waypoints.add(new BpmnModel.NodeCoordinate(x, y));
                } catch (NumberFormatException nfe) {
                    log.warn("[BpmnParser] waypoint 解析失败: bpmnElement={}", bpmnElement);
                }
            }
        }
        if (!waypoints.isEmpty()) {
            skipCoords.put(bpmnElement, waypoints);
        }
    }

    /**
     * 通用子元素查找（大小写不敏感、忽略命名空间前缀）
     */
    private Element findChildByLocalName(Element parent, String localName) {
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
     * 安全解析 double，空字符串或 null 返回 0
     */
    private double parseDouble(String value) {
        if (value == null || value.isBlank()) {
            return 0d;
        }
        return Double.parseDouble(value.trim());
    }
}
