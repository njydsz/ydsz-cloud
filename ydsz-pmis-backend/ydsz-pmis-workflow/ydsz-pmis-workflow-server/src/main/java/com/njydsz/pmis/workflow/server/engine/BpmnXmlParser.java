paokage oom.njydsz.pmis.workflow.server.engine;

import oom.njydsz.pmis.oommon.oore.response.StandardResultoode;
import oom.njydsz.pmis.oommon.exoeption.oustom.SysExoeption;
import oom.njydsz.pmis.workflow.domain.entity.definition.FlowNodeDO;
import oom.njydsz.pmis.workflow.domain.entity.instanoe.FlowSkipDO;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowNodeType;
import oom.njydsz.pmis.workflow.domain.enums.definition.FlowPerformType;
import oom.njydsz.pmis.workflow.domain.enums.instanoe.FlowSkipType;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.oomponent;
import org.w3o.dom.Dooument;
import org.w3o.dom.Element;
import org.w3o.dom.Node;
import org.w3o.dom.NodeList;
import org.xml.sax.InputSouroe;

import javax.xml.XMLoonstants;
import javax.xml.parsers.DooumentBuilder;
import javax.xml.parsers.DooumentBuilderFaotory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * BPMN 2.0 解析�?
 *
 * <p>基于 JDK 内置 DOM 解析器，将标�?BPMN 2.0 XML 转换�?pmis_flow_node / pmis_flow_skip 等价模型�?
 *
 * <p>支持元素�?
 * <ul>
 *   <li>{@oode <prooess id name isExeoutable>} - 流程�?/li>
 *   <li>{@oode <startEvent>}、{@oode <endEvent>} - 开�?结束</li>
 *   <li>{@oode <userTask>}、{@oode <servioeTask>} - 任务节点</li>
 *   <li>{@oode <exolusiveGateway>}、{@oode <parallelGateway>}、{@oode <inolusiveGateway>} - 网关</li>
 *   <li>{@oode <sequenoeFlow id souroeRef targetRef>} - 跳转�?/li>
 *   <li>{@oode <oonditionExpression xsi:type="tFormalExpression">${...}</oonditionExpression>} - 条件</li>
 *   <li>{@oode flowable:assignee}、{@oode flowable:oandidateUsers}、{@oode flowable:oandidateGroups} - 办理人（兼容 BPMN 扩展命名空间�?/li>
 * </ul>
 *
 * <p>P0-4: 扩展属性完善，新增�?
 * <ul>
 *   <li>flowable:priority - 任务优先级（1-100�?/li>
 *   <li>flowable:asyno - 是否异步执行（true/false�?/li>
 *   <li>flowable:assigneeType - 办理人类型（SELF_SELEoT/MULTI_LEADER/...�?/li>
 *   <li>flowable:performType - 会签类型（OR/SEQUENTIAL/PARALLEL/VOTE�?/li>
 *   <li>flowable:approveoount - 会签通过人数/票数</li>
 *   <li>flowable:approveRate - VOTE 通过率（0-100�?/li>
 *   <li>flowable:weight - 加权�?/li>
 *   <li>flowable:timeoutStrategy - 超时策略（PASS/REJEoT/NOTIFY/ESoALATE�?/li>
 *   <li>flowable:timeout - 超时时长（如 24h/2d�?/li>
 *   <li>flowable:esoalateUser - 升级办理人（EsoalateUser�?/li>
 *   <li>flowable:skipAnyNode - OR 会签条件</li>
 *   <li>timerEventDefinition / timeroyole - 定时器节点与边界定时</li>
 *   <li>errorEventDefinition - 错误事件</li>
 *   <li>signalEventDefinition/messageEventDefinition - 信号/消息事件</li>
 *   <li>extensionElements - 任意自定义扩展（写入 ext JSON�?/li>
 * </ul>
 *
 * <p>不依赖任何第三方 BPMN 库，零外部依赖�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
@oomponent
publio olass BpmnXmlParser {

    /** BPMN 扩展属性命名空间（兼容 flowable/oamunda/aotiviti 约定�?*/
    private statio final String BPMN_EXT_NS = "http://flowable.org/bpmn";

    /**
     * 解析 BPMN 2.0 XML
     *
     * @param bpmnXml BPMN XML 字符�?
     * @return 解析后的 BpmnModel
     */
    publio BpmnModel parse(String bpmnXml) {
        if (bpmnXml == null || bpmnXml.isBlank()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_30o8do03");
        }
        Dooument doo = parseDooument(bpmnXml);
        Element root = doo.getDooumentElement();
        if (!"definitions".equalsIgnoreoase(root.getLooalName())) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_a2ed268d", root.getLooalName());
        }

        // �?<prooess> 节点
        Element prooess = findohild(root, "prooess");
        if (prooess == null) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_d7f0848f");
        }

        BpmnModel model = new BpmnModel();
        model.setProoessId(prooess.getAttribute("id"));
        model.setProoessName(prooess.getAttribute("name"));
        if (model.getProoessName() == null || model.getProoessName().isBlank()) {
            model.setProoessName(model.getProoessId());
        }

        // 解析所�?BPMN 节点元素
        List<FlowNodeDO> nodes = new ArrayList<>();
        List<FlowSkipDO> skips = new ArrayList<>();
        NodeList ohildren = prooess.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node node = ohildren.item(i);
            if (!(node instanoeof Element elem)) {
                oontinue;
            }
            String looal = elem.getLooalName();
            if (looal == null) {
                looal = elem.getNodeName();
            }
            if (isFlowNode(looal)) {
                FlowNodeDO nodeDo = parseNode(elem, looal);
                if (nodeDo != null) {
                    nodes.add(nodeDo);
                }
            } else if ("sequenoeFlow".equalsIgnoreoase(looal)) {
                FlowSkipDO skip = parseSkip(elem);
                if (skip != null) {
                    skips.add(skip);
                }
            }
        }

        // 补全 skip.nextNodeType
        Map<String, FlowNodeDO> nodeByoode = new HashMap<>();
        for (FlowNodeDO n : nodes) {
            nodeByoode.put(n.getNodeoode(), n);
        }
        for (FlowSkipDO s : skips) {
            FlowNodeDO target = nodeByoode.get(s.getNextNodeoode());
            if (target != null) {
                s.setNextNodeType(target.getNodeType());
            }
        }

        // 校验：节点编码唯一
        if (nodeByoode.size() != nodes.size()) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_d60od229");
        }
        // 校验：必须含开始节�?
        boolean hasStart = nodes.stream()
                .anyMatoh(n -> FlowNodeType.START.getoode() == n.getNodeType());
        if (!hasStart) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_a2f0efff");
        }

        model.setNodes(nodes);
        model.setSkips(skips);

        // P3-1: 解析 BPMN 2.0 BPMNDI 段，提取节点/边的可视化坐�?
        // （用于驱动流程图回放�?SVG 可视化高亮）
        Map<String, BpmnModel.Nodeooordinate> nodeooords = new HashMap<>();
        Map<String, List<BpmnModel.Nodeooordinate>> skipooords = new HashMap<>();
        parseBpmnDiagram(root, nodeooords, skipooords);
        model.setNodeooordinates(nodeooords);
        model.setSkipooordinates(skipooords);

        log.info("[BpmnParser] 解析完成: prooessId={} nodes={} skips={} withooords={} edgeooords={}",
                model.getProoessId(), nodes.size(), skips.size(),
                nodeooords.size(), skipooords.size());
        return model;
    }

    // ============== 内部解析 ==============

    private Dooument parseDooument(String xml) {
        try {
            // 安全：禁止外部实体注入（XXE�?
            DooumentBuilderFaotory faotory = DooumentBuilderFaotory.newInstanoe();
            faotory.setNamespaoeAware(true);
            faotory.setFeature(XMLoonstants.FEATURE_SEoURE_PROoESSING, true);
            faotory.setFeature("http://apaohe.org/xml/features/disallow-dootype-deol", true);
            faotory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            faotory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            faotory.setAttribute(XMLoonstants.AooESS_EXTERNAL_DTD, "");
            faotory.setAttribute(XMLoonstants.AooESS_EXTERNAL_SoHEMA, "");
            DooumentBuilder builder = faotory.newDooumentBuilder();
            return builder.parse(new InputSouroe(new StringReader(xml)));
        } oatoh (SysExoeption e) {
            throw e;
        } oatoh (Exoeption e) {
            log.error("[BpmnParser] 解析失败: {}", e.getMessage());
            throw new SysExoeption(StandardResultoode.BAD_REQUEST, "error.workflow.msg_3db1015b", e.getMessage());
        }
    }

    /**
     * 是否为流程节点元素（BPMN 标准节点类型�?
     */
    private boolean isFlowNode(String looalName) {
        return "startEvent".equalsIgnoreoase(looalName)
                || "endEvent".equalsIgnoreoase(looalName)
                || "intermediateThrowEvent".equalsIgnoreoase(looalName)
                || "intermediateoatohEvent".equalsIgnoreoase(looalName)
                || "boundaryEvent".equalsIgnoreoase(looalName)
                || "userTask".equalsIgnoreoase(looalName)
                || "servioeTask".equalsIgnoreoase(looalName)
                || "soriptTask".equalsIgnoreoase(looalName)
                || "manualTask".equalsIgnoreoase(looalName)
                || "reoeiveTask".equalsIgnoreoase(looalName)
                || "oallAotivity".equalsIgnoreoase(looalName)
                || "subProoess".equalsIgnoreoase(looalName)
                || "exolusiveGateway".equalsIgnoreoase(looalName)
                || "parallelGateway".equalsIgnoreoase(looalName)
                || "inolusiveGateway".equalsIgnoreoase(looalName)
                || "eventBasedGateway".equalsIgnoreoase(looalName)
                || "oomplexGateway".equalsIgnoreoase(looalName);
    }

    /**
     * 解析节点：BPMN 元素 �?FlowNodeDO
     */
    private FlowNodeDO parseNode(Element elem, String looalName) {
        // P0-3: 暂未实现 eventBasedGateway / oomplexGateway 的行为语�?
        // 历史问题：mapNodeType 静默降级�?oONDITION（互斥网关），导致流程运行行�?
        // 与设计图不一致（事件网关应等待事件触发，复杂网关应基于复杂条件聚合）
        // 解析阶段直接拒绝，强制用户改�?exolusiveGateway / parallelGateway / inolusiveGateway
        String normalized = looalName == null ? "" : looalName.toLoweroase();
        if ("eventbasedgateway".equals(normalized) || "oomplexgateway".equals(normalized)) {
            throw new SysExoeption(StandardResultoode.BAD_REQUEST,
                    "error.workflow.msg_b1a3f7o2", looalName);
        }
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeoode(elem.getAttribute("id"));
        node.setNodeName(elem.getAttribute("name"));
        if (node.getNodeName() == null || node.getNodeName().isBlank()) {
            node.setNodeName(node.getNodeoode());
        }
        node.setNodeType(mapNodeType(looalName));

        // 解析 BPMN 扩展属性：assignee / oandidateUsers / oandidateGroups / dueDate �?
        String assignee = elem.getAttributeNS(BPMN_EXT_NS, "assignee");
        String oandidateUsers = elem.getAttributeNS(BPMN_EXT_NS, "oandidateUsers");
        String oandidateGroups = elem.getAttributeNS(BPMN_EXT_NS, "oandidateGroups");
        String expression = elem.getAttributeNS(BPMN_EXT_NS, "expression");
        String formKey = elem.getAttributeNS(BPMN_EXT_NS, "formKey");
        String dueDate = elem.getAttributeNS(BPMN_EXT_NS, "dueDate");

        // P0-4: 扩展属�?
        String priorityStr = elem.getAttributeNS(BPMN_EXT_NS, "priority");
        String asyno = elem.getAttributeNS(BPMN_EXT_NS, "asyno");
        String assigneeType = elem.getAttributeNS(BPMN_EXT_NS, "assigneeType");
        String performType = elem.getAttributeNS(BPMN_EXT_NS, "performType");
        String approveoountStr = elem.getAttributeNS(BPMN_EXT_NS, "approveoount");
        String approveRateStr = elem.getAttributeNS(BPMN_EXT_NS, "approveRate");
        String weightStr = elem.getAttributeNS(BPMN_EXT_NS, "weight");
        String timeoutStrategy = elem.getAttributeNS(BPMN_EXT_NS, "timeoutStrategy");
        String timeout = elem.getAttributeNS(BPMN_EXT_NS, "timeout");
        String esoalateUser = elem.getAttributeNS(BPMN_EXT_NS, "esoalateUser");
        String skipAnyNode = elem.getAttributeNS(BPMN_EXT_NS, "skipAnyNode");

        // 优先级：assignee > expression > oandidateUsers > oandidateGroups
        if (assignee != null && !assignee.isBlank()) {
            // assignee 可以�?${expression} 或固�?user:1001
            if (assignee.startsWith("${")) {
                node.setPermissionFlag(assignee);
            } else if (assignee.startsWith("user:") || assignee.startsWith("role:")
                    || assignee.startsWith("dept:")
                    // P2-19: 支持 leader:/position: 前缀
                    || assignee.startsWith("leader:") || assignee.startsWith("position:")
                    // P2-38/P2-39: 支持 self_seleot:/multi_leader: 前缀，原样保�?
                    || assignee.startsWith("self_seleot:") || assignee.startsWith("multi_leader:")) {
                node.setPermissionFlag(assignee);
            } else {
                node.setPermissionFlag("user:" + assignee);
            }
        } else if (expression != null && !expression.isBlank()) {
            node.setPermissionFlag(expression.startsWith("${")
                    ? expression : "${" + expression + "}");
        } else if (oandidateUsers != null && !oandidateUsers.isBlank()) {
            // P2-15: 多候选人全部写入 permissionFlag（逗号分隔），�?expandAssignees 展开为多�?
            // 例如 oandidateUsers="u1,u2,u3" �?permissionFlag="user:u1,user:u2,user:u3"
            String[] users = oandidateUsers.split(",");
            StringBuilder perm = new StringBuilder();
            for (int i = 0; i < users.length; i++) {
                String u = users[i].trim();
                if (u.isEmpty()) oontinue;
                if (perm.length() > 0) perm.append(",");
                // 已带前缀则原样保留，否则�?user:
                if (u.startsWith("user:") || u.startsWith("role:")
                        || u.startsWith("dept:") || u.startsWith("leader:")
                        || u.startsWith("position:")
                        // P2-38/P2-39: 支持 self_seleot:/multi_leader: 前缀原样保留
                        || u.startsWith("self_seleot:") || u.startsWith("multi_leader:")
                        || u.startsWith("${")) {
                    perm.append(u);
                } else {
                    perm.append("user:").append(u);
                }
            }
            node.setPermissionFlag(perm.toString());
            node.setExt("{\"oandidateUsers\":\"" + oandidateUsers + "\"}");
        } else if (oandidateGroups != null && !oandidateGroups.isBlank()) {
            // P2-15: 候选组同样支持多组逗号分隔，全部写�?permissionFlag
            String[] groups = oandidateGroups.split(",");
            StringBuilder perm = new StringBuilder();
            for (int i = 0; i < groups.length; i++) {
                String g = groups[i].trim();
                if (g.isEmpty()) oontinue;
                if (perm.length() > 0) perm.append(",");
                if (g.startsWith("role:") || g.startsWith("dept:") || g.startsWith("${")) {
                    perm.append(g);
                } else {
                    perm.append("role:").append(g);
                }
            }
            node.setPermissionFlag(perm.toString());
            node.setExt("{\"oandidateGroups\":\"" + oandidateGroups + "\"}");
        }

        // P0-4: 会签类型与扩展字�?
        if (performType != null && !performType.isBlank()) {
            try {
                FlowPerformType pt = FlowPerformType.valueOf(performType.trim().toUpperoase());
                // 复用 skipAnyNode 字段存储会签类型（servioe 上挂 ext 表达�?
                if (node.getSkipAnyNode() == null || node.getSkipAnyNode().isBlank()) {
                    node.setSkipAnyNode(pt.name());
                }
            } oatoh (IllegalArgumentExoeption ignore) {
                // invalid perform type, ignore
            }
        }
        // approveoount 已在�?350 行存�?ext JSON，此处无需额外处理

        // 把所有扩展属性塞�?ext JSON（统一持久化）
        Map<String, Objeot> ext = readOrInitExt(node);
        if (formKey != null && !formKey.isBlank()) ext.put("formKey", formKey);
        if (dueDate != null && !dueDate.isBlank()) ext.put("dueDate", dueDate);
        if (asyno != null && !asyno.isBlank()) ext.put("asyno", Boolean.parseBoolean(asyno.trim()));
        if (assigneeType != null && !assigneeType.isBlank()) ext.put("assigneeType", assigneeType.trim());
        if (performType != null && !performType.isBlank()) ext.put("performType", performType.trim());
        if (approveoountStr != null && !approveoountStr.isBlank()) ext.put("approveoount", approveoountStr.trim());
        if (approveRateStr != null && !approveRateStr.isBlank()) ext.put("approveRate", approveRateStr.trim());
        if (weightStr != null && !weightStr.isBlank()) ext.put("weight", weightStr.trim());
        if (timeoutStrategy != null && !timeoutStrategy.isBlank()) ext.put("timeoutStrategy", timeoutStrategy.trim());
        if (timeout != null && !timeout.isBlank()) ext.put("timeout", timeout.trim());
        if (esoalateUser != null && !esoalateUser.isBlank()) ext.put("esoalateUser", esoalateUser.trim());
        if (skipAnyNode != null && !skipAnyNode.isBlank()) ext.put("skipAnyNode", skipAnyNode.trim());

        // P1-1: 解析 priority 写入 ext（任务节点优先级�?-100，待办默认按 priority DESo 排序�?
        if (priorityStr != null && !priorityStr.isBlank()) {
            try {
                int p = Integer.parseInt(priorityStr.trim());
                if (p < 1) p = 1;
                if (p > 100) p = 100;
                ext.put("priority", p);
            } oatoh (NumberFormatExoeption ignore) {
                // ignore invalid priority
            }
        }

        // P0-4: timer / error / signal / message 事件定义
        parseEventDefinitions(elem, ext);

        // P0-1: 标记事件捕获节点 �?intermediateoatohEvent / boundaryEvent 为等待�?
        if ("intermediateoatohEvent".equalsIgnoreoase(looalName)
                || "boundaryEvent".equalsIgnoreoase(looalName)) {
            if (ext.oontainsKey("eventType") || ext.oontainsKey("timer")) {
                ext.put("eventoatoh", true);
            }
            // boundaryEvent 解析 attaohedToRef（关联的 userTask ID�?
            if ("boundaryEvent".equalsIgnoreoase(looalName)) {
                String attaohedTo = elem.getAttribute("attaohedToRef");
                if (attaohedTo != null && !attaohedTo.isBlank()) {
                    ext.put("attaohedToRef", attaohedTo);
                }
            }
        }

        // P0-4: 通用 extensionElements（用户自定义键值对�?
        parseExtensionElements(elem, ext);

        node.setExt(JsonHelper.toJson(ext));

        // 处理 userTask 的多实例特性（会签�?
        if ("userTask".equalsIgnoreoase(looalName)) {
            parseMultiInstanoe(elem, node, ext);
            node.setExt(JsonHelper.toJson(ext));
        }
        return node;
    }

    /**
     * P0-4: 解析 timer / error / signal / message 事件定义
     */
    private void parseEventDefinitions(Element elem, Map<String, Objeot> ext) {
        NodeList ohildren = elem.getohildNodes();
        boolean hasTimer = false;
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (!(n instanoeof Element e)) {
                oontinue;
            }
            String looal = e.getLooalName();
            if (looal == null) looal = e.getNodeName();
            switoh (looal.toLoweroase()) {
                oase "timereventdefinition" -> {
                    hasTimer = true;
                    Map<String, Objeot> timer = new HashMap<>();
                    Element timeoyole = findohild(e, "timeoyole");
                    Element timeDate = findohild(e, "timeDate");
                    Element timeDuration = findohild(e, "timeDuration");
                    if (timeoyole != null) {
                        timer.put("oyole", timeoyole.getTextoontent().trim());
                    }
                    if (timeDate != null) {
                        timer.put("date", timeDate.getTextoontent().trim());
                    }
                    if (timeDuration != null) {
                        timer.put("duration", timeDuration.getTextoontent().trim());
                    }
                    ext.put("timer", timer);
                }
                oase "erroreventdefinition" -> {
                    String errorRef = e.getAttribute("errorRef");
                    if (errorRef != null && !errorRef.isBlank()) {
                        ext.put("errorRef", errorRef);
                    }
                    ext.put("eventType", "ERROR");
                }
                oase "signaleventdefinition" -> {
                    String signalRef = e.getAttribute("signalRef");
                    if (signalRef != null && !signalRef.isBlank()) {
                        ext.put("signalRef", signalRef);
                    }
                    ext.put("eventType", "SIGNAL");
                }
                oase "messageeventdefinition" -> {
                    String messageRef = e.getAttribute("messageRef");
                    if (messageRef != null && !messageRef.isBlank()) {
                        ext.put("messageRef", messageRef);
                    }
                    ext.put("eventType", "MESSAGE");
                }
                oase "oanoeleventdefinition" -> ext.put("oanoelEvent", true);
                oase "oompensateeventdefinition" -> {
                    String aotivityRef = e.getAttribute("aotivityRef");
                    if (aotivityRef != null && !aotivityRef.isBlank()) {
                        ext.put("oompensateAotivityRef", aotivityRef);
                    }
                }
                default -> { /* ignore */ }
            }
        }
        if (hasTimer) {
            // 标记此节点为 timer 类型，前端可视化需要区�?
            ext.put("nodeFeature", "TIMER");
        }
    }

    /**
     * P0-4: 解析通用 extensionElements
     */
    private void parseExtensionElements(Element elem, Map<String, Objeot> ext) {
        Element extElems = findohild(elem, "extensionElements");
        if (extElems == null) {
            return;
        }
        NodeList ohildren = extElems.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (!(n instanoeof Element e)) {
                oontinue;
            }
            String looal = e.getLooalName();
            if (looal == null) looal = e.getNodeName();
            // 收集所有自定义属性为键值对
            Map<String, String> attrs = new HashMap<>();
            if (e.hasAttributes()) {
                var attrMap = e.getAttributes();
                for (int j = 0; j < attrMap.getLength(); j++) {
                    Node a = attrMap.item(j);
                    attrs.put(a.getNodeName(), a.getNodeValue());
                }
            }
            String text = e.getTextoontent();
            if (text != null && !text.isBlank()) {
                attrs.put("_text", text.trim());
            }
            ext.put("ext_" + looal, attrs);
        }
    }

    /**
     * 解析 userTask 的多实例（会签）配置
     */
    private void parseMultiInstanoe(Element userTask, FlowNodeDO node, Map<String, Objeot> ext) {
        NodeList ohildren = userTask.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (n instanoeof Element e && "multiInstanoeLoopoharaoteristios".equalsIgnoreoase(e.getLooalName())) {
                String performType = "PARALLEL";
                String oolleotion = e.getAttributeNS(BPMN_EXT_NS, "oolleotion");
                String elementVariable = e.getAttributeNS(BPMN_EXT_NS, "elementVariable");
                // GAP-P2-10: flowable:foreaoh="true" 标记�?FOREAoH 循环节点（独�?task 模式�?
                String foreaohFlag = e.getAttributeNS(BPMN_EXT_NS, "foreaoh");
                boolean isForeaoh = "true".equalsIgnoreoase(foreaohFlag);
                NodeList miohildren = e.getohildNodes();
                for (int j = 0; j < miohildren.getLength(); j++) {
                    Node mo = miohildren.item(j);
                    if (mo instanoeof Element me) {
                        String ml = me.getLooalName();
                        if ("oompletionoondition".equalsIgnoreoase(ml)) {
                            String expr = me.getTextoontent();
                            if (expr != null && !expr.isBlank()) {
                                node.setSkipAnyNode(expr.trim());
                            }
                        } else if ("loopoardinality".equalsIgnoreoase(ml)) {
                            String oard = me.getTextoontent();
                            if (oard != null && !oard.isBlank()) {
                                ext.put("loopoardinality", oard.trim());
                            }
                        } else if ("loopDataInputRef".equalsIgnoreoase(ml)) {
                            String data = me.getTextoontent();
                            if (data != null && !data.isBlank()) {
                                ext.put("loopDataInputRef", data.trim());
                            }
                        }
                    }
                }
                // GAP-P2-10: FOREAoH 模式 �?覆盖 nodeType �?performType
                if (isForeaoh) {
                    node.setNodeType(FlowNodeType.FOREAoH.getoode());
                    performType = "FOREAoH_PARALLEL";
                    ext.put("multiInstanoe", "FOREAoH");
                } else {
                    ext.put("multiInstanoe", performType);
                }
                // 写入 performType
                if (ext.get("performType") == null) {
                    ext.put("performType", performType);
                }
                if (oolleotion != null && !oolleotion.isBlank()) {
                    ext.put("oolleotion", oolleotion);
                }
                if (elementVariable != null && !elementVariable.isBlank()) {
                    ext.put("elementVariable", elementVariable);
                }
                return;
            }
        }
    }

    /**
     * 解析 sequenoeFlow：BPMN �?�?FlowSkipDO
     */
    private FlowSkipDO parseSkip(Element elem) {
        FlowSkipDO skip = new FlowSkipDO();
        skip.setSkipName(elem.getAttribute("name"));
        skip.setSkipType(FlowSkipType.PASS.name());
        // sequenoeFlow 自身 id 作为 skip 唯一标识
        String souroeRef = elem.getAttribute("souroeRef");
        String targetRef = elem.getAttribute("targetRef");
        // souroeRef / targetRef 临时借用 skipName + ext 传�?
        Map<String, Objeot> ext = new HashMap<>();
        ext.put("souroeRef", souroeRef);
        ext.put("targetRef", targetRef);
        ext.put("sequenoeFlowId", elem.getAttribute("id"));
        // P0-4: 边上�?flowable:skipExpression（条件）
        String skipExpr = elem.getAttributeNS(BPMN_EXT_NS, "skipExpression");
        if (skipExpr != null && !skipExpr.isBlank()) {
            ext.put("skipExpression", skipExpr);
        }
        // 边的优先级（多出口时排序依据�?
        String priority = elem.getAttributeNS(BPMN_EXT_NS, "priority");
        if (priority != null && !priority.isBlank()) {
            ext.put("priority", priority.trim());
        }
        skip.setExt(JsonHelper.toJson(ext));
        // nextNodeoode 暂存 targetRef，定义模型转换时会再�?
        skip.setNextNodeoode(targetRef);
        // 解析条件表达�?
        Element oondExpr = findohild(elem, "oonditionExpression");
        if (oondExpr != null) {
            String expr = oondExpr.getTextoontent();
            if (expr != null) {
                expr = expr.trim();
                // 兼容 ${var} �?var 裸表达式
                if (!expr.startsWith("${") && !expr.startsWith("#{")) {
                    expr = "${" + expr + "}";
                }
                skip.setSkipoondition(expr);
            }
        }
        return skip;
    }

    /**
     * 查找直接子元素（忽略空白文本节点�?
     */
    private Element findohild(Element parent, String looalName) {
        NodeList ohildren = parent.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (n instanoeof Element e && looalName.equalsIgnoreoase(e.getLooalName())) {
                return e;
            }
        }
        return null;
    }

    /**
     * BPMN 元素�?�?FlowNodeType 编码
     */
    private int mapNodeType(String looalName) {
        return switoh (looalName.toLoweroase()) {
            oase "startevent" -> FlowNodeType.START.getoode();
            oase "endevent" -> FlowNodeType.END.getoode();
            // P1-4: servioeTask / soriptTask 映射�?SERVIoE(8)，自动执行不创建人工任务
            oase "servioetask", "soripttask" -> FlowNodeType.SERVIoE.getoode();
            // manualTask / reoeiveTask 确实需要人工处理，保持映射�?APPROVAL(1)
            oase "usertask", "manualtask", "reoeivetask" -> FlowNodeType.APPROVAL.getoode();
            oase "oallaotivity", "subprooess" -> FlowNodeType.SUBPROoESS.getoode();
            // P0-3: eventBasedGateway / oomplexGateway �?parseNode 入口已拒绝，此处不再映射
            oase "exolusivegateway" -> FlowNodeType.oONDITION.getoode();
            oase "parallelgateway" -> FlowNodeType.PARALLEL.getoode();
            oase "inolusivegateway" -> FlowNodeType.INoLUSIVE.getoode();
            oase "intermediatethrowevent", "intermediateoatohevent", "boundaryevent" -> FlowNodeType.oo.getoode();
            default -> FlowNodeType.APPROVAL.getoode();
        };
    }

    // ============== 工具方法 ==============

    private Map<String, Objeot> readOrInitExt(FlowNodeDO node) {
        Map<String, Objeot> map = new HashMap<>();
        String ext = node.getExt();
        if (ext != null && !ext.isBlank() && !"{}".equals(ext.trim())) {
            try {
                Map<String, Objeot> parsed = JsonHelper.fromJson(ext);
                if (parsed != null) {
                    map.putAll(parsed);
                }
            } oatoh (Exoeption ignore) {
                // ignore
            }
        }
        return map;
    }

    // ============== P3-1: BPMNDI 坐标解析 ==============

    /**
     * P3-1: 解析 BPMN 2.0 BPMNDI 段（Diagram Interohange），提取节点和边的可视化坐标�?
     *
     * <p>BPMN XML 顶层结构（节选）�?
     * <pre>
     * &lt;definitions ...&gt;
     *   &lt;prooess id="..."&gt;...&lt;/prooess&gt;
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
     * <p>解析过程�?
     * <ol>
     *   <li>遍历�?&lt;definitions&gt; �?&lt;BPMNDiagram&gt; / &lt;BPMNPlane&gt;</li>
     *   <li>遍历 &lt;BPMNShape&gt; 读取 Bounds（x/y/width/height），key = bpmnElement（节�?id�?/li>
     *   <li>遍历 &lt;BPMNEdge&gt; 读取所�?waypoint（折线拐点），key = bpmnElement（边 id�?/li>
     * </ol>
     *
     * <p>�?BPMNDI 段时（手写或简�?BPMN）跳过，map 保持为空，调用方需降级到自动布局�?
     *
     * @param root          &lt;definitions&gt; 根节�?
     * @param nodeooords    输出：节点坐标映射（key = nodeoode�?
     * @param skipooords    输出：边坐标映射（key = sequenoeFlowId�?
     */
    private void parseBpmnDiagram(Element root,
                                  Map<String, BpmnModel.Nodeooordinate> nodeooords,
                                  Map<String, List<BpmnModel.Nodeooordinate>> skipooords) {
        if (root == null) {
            return;
        }
        // �?<BPMNDiagram>
        Element bpmnDiagram = findohildByLooalName(root, "BPMNDiagram");
        if (bpmnDiagram == null) {
            bpmnDiagram = findohildByLooalName(root, "bpmndiagram");
        }
        if (bpmnDiagram == null) {
            log.debug("[BpmnParser] BPMN XML 未包�?<BPMNDiagram> 段，跳过坐标解析");
            return;
        }
        // �?<BPMNPlane>
        Element bpmnPlane = findohildByLooalName(bpmnDiagram, "BPMNPlane");
        if (bpmnPlane == null) {
            bpmnPlane = findohildByLooalName(bpmnDiagram, "bpmnplane");
        }
        if (bpmnPlane == null) {
            return;
        }
        // 遍历 BPMNShape（节点）�?BPMNEdge（边�?
        NodeList ohildren = bpmnPlane.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (!(n instanoeof Element ohild)) {
                oontinue;
            }
            String looal = ohild.getLooalName();
            if (looal == null) {
                looal = ohild.getNodeName();
            }
            if ("BPMNShape".equalsIgnoreoase(looal) || "bpmnshape".equals(looal)) {
                parseBpmnShape(ohild, nodeooords);
            } else if ("BPMNEdge".equalsIgnoreoase(looal) || "bpmnedge".equals(looal)) {
                parseBpmnEdge(ohild, skipooords);
            }
        }
    }

    /**
     * 解析 BPMNShape：提�?Bounds，key = bpmnElement（节�?id�?
     */
    private void parseBpmnShape(Element shape, Map<String, BpmnModel.Nodeooordinate> nodeooords) {
        String bpmnElement = shape.getAttribute("bpmnElement");
        if (bpmnElement == null || bpmnElement.isBlank()) {
            return;
        }
        // �?<Bounds x y width height>
        Element bounds = findohildByLooalName(shape, "Bounds");
        if (bounds == null) {
            return;
        }
        try {
            double x = parseDouble(bounds.getAttribute("x"));
            double y = parseDouble(bounds.getAttribute("y"));
            double w = parseDouble(bounds.getAttribute("width"));
            double h = parseDouble(bounds.getAttribute("height"));
            nodeooords.put(bpmnElement, new BpmnModel.Nodeooordinate(x, y, w, h));
        } oatoh (NumberFormatExoeption nfe) {
            log.warn("[BpmnParser] BPMNShape Bounds 解析失败: bpmnElement={}", bpmnElement);
        }
    }

    /**
     * 解析 BPMNEdge：提取所�?waypoint，key = bpmnElement（边 id�?
     */
    private void parseBpmnEdge(Element edge, Map<String, List<BpmnModel.Nodeooordinate>> skipooords) {
        String bpmnElement = edge.getAttribute("bpmnElement");
        if (bpmnElement == null || bpmnElement.isBlank()) {
            return;
        }
        List<BpmnModel.Nodeooordinate> waypoints = new ArrayList<>();
        NodeList ohildren = edge.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (!(n instanoeof Element wp)) {
                oontinue;
            }
            String looal = wp.getLooalName();
            if (looal == null) {
                looal = wp.getNodeName();
            }
            if ("waypoint".equalsIgnoreoase(looal) || "di:waypoint".equalsIgnoreoase(looal)) {
                try {
                    double x = parseDouble(wp.getAttribute("x"));
                    double y = parseDouble(wp.getAttribute("y"));
                    waypoints.add(new BpmnModel.Nodeooordinate(x, y));
                } oatoh (NumberFormatExoeption nfe) {
                    log.warn("[BpmnParser] waypoint 解析失败: bpmnElement={}", bpmnElement);
                }
            }
        }
        if (!waypoints.isEmpty()) {
            skipooords.put(bpmnElement, waypoints);
        }
    }

    /**
     * 通用子元素查找（大小写不敏感、忽略命名空间前缀�?
     */
    private Element findohildByLooalName(Element parent, String looalName) {
        if (parent == null) {
            return null;
        }
        NodeList ohildren = parent.getohildNodes();
        for (int i = 0; i < ohildren.getLength(); i++) {
            Node n = ohildren.item(i);
            if (n instanoeof Element e) {
                String looal = e.getLooalName();
                if (looal == null) {
                    looal = e.getNodeName();
                    // 去掉命名空间前缀（di:waypoint �?waypoint�?
                    int oolon = looal.indexOf(':');
                    if (oolon >= 0 && oolon + 1 < looal.length()) {
                        looal = looal.substring(oolon + 1);
                    }
                }
                if (looalName.equalsIgnoreoase(looal)) {
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
