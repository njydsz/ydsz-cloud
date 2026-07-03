package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.enums.FlowSkipType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BpmnXmlParser 单元测试
 *
 * <p>覆盖 BPMN 2.0 XML 解析器的核心场景，包括基本流程、flowable 扩展属性、
 * 条件表达式自动包裹、多实例（会签）配置、事件定义（timer/signal）、BPMNDI 图形坐标、
 * 节点类型映射、XXE 防护、非法 XML 校验、缺少 startEvent 校验、重复节点 ID 校验。
 *
 * <p>BpmnXmlParser 为无状态解析类，直接 new 实例即可，无需 Mockito。
 *
 * <p>覆盖场景：
 * <ul>
 *   <li>解析基本流程：开始→审批→结束 三节点两跳转，nextNodeType 自动补全</li>
 *   <li>解析 flowable 扩展属性：assignee/priority/performType/approveCount/timeoutStrategy 等</li>
 *   <li>解析 candidateUsers / candidateGroups：多值逗号分隔展开与 role: 前缀补全</li>
 *   <li>解析 assignee 前缀逻辑：裸用户名补 user: 前缀，已带前缀原样保留</li>
 *   <li>解析条件表达式：裸表达式自动包裹 ${...}，#{...} EL 表达式原样保留</li>
 *   <li>解析 userTask 多实例配置：completionCondition / loopCardinality / collection 写入 ext</li>
 *   <li>解析 timerEventDefinition / signalEventDefinition：写入 ext.timer 与 eventType</li>
 *   <li>解析 BPMNDI：BPMNShape 的 Bounds 与 BPMNEdge 的 waypoint 写入坐标映射</li>
 *   <li>节点类型映射：7 种 BPMN 元素到 FlowNodeType 的正确转换</li>
 *   <li>XXE 防护：DOCTYPE/外部实体声明被拒绝</li>
 *   <li>非法 XML：null/空字符串/纯空白/非 XML 文本/根元素非 definitions/缺少 process 均抛异常</li>
 *   <li>缺少 startEvent：流程无开始节点抛 BizException</li>
 *   <li>重复节点 ID：nodeByCode.size() != nodes.size() 抛 BizException</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
class BpmnXmlParserTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    // ============================== 辅助方法 ==============================

    /**
     * 将 process 内部 XML 片段包裹为完整的 BPMN definitions 文档。
     * 默认带上 BPMN/flowable/bpmndi/dc/di/xsi 命名空间，便于各测试复用。
     */
    private String wrap(String processBody) {
        return wrap(processBody, "");
    }

    /**
     * 包裹 process 内部片段与 process 之后的附加内容（如 BPMNDI 段）。
     */
    private String wrap(String processBody, String afterProcess) {
        return """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:flowable="http://flowable.org/bpmn" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <process id="test-process" name="测试流程" isExecutable="true">
                """ + processBody + """
                  </process>
                """ + afterProcess + """
                </definitions>
                """;
    }

    /** 按 nodeCode 查找节点 */
    private FlowNodeDO findNode(BpmnModel model, String nodeCode) {
        return model.getNodes().stream()
                .filter(n -> nodeCode.equals(n.getNodeCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到节点: " + nodeCode));
    }

    /** 按 sequenceFlowId（写入 skip.ext）查找跳转 */
    private FlowSkipDO findSkipByFlowId(BpmnModel model, String sequenceFlowId) {
        return model.getSkips().stream()
                .filter(s -> {
                    Map<String, Object> ext = JsonHelper.fromJson(s.getExt());
                    return ext != null && sequenceFlowId.equals(ext.get("sequenceFlowId"));
                })
                .findFirst()
                .orElseThrow(() -> new AssertionError("未找到跳转: " + sequenceFlowId));
    }

    /** 解析 FlowNodeDO.ext 为 Map */
    private Map<String, Object> extOf(FlowNodeDO node) {
        return JsonHelper.fromJson(node.getExt());
    }

    // ============================== 1. 基本流程 ==============================

    @Test
    @DisplayName("解析基本流程：开始→审批→结束 三节点两跳转，nextNodeType 自动补全")
    void shouldParseBasicFlow() {
        String xml = wrap("""
                  <startEvent id="start" name="开始"/>
                  <userTask id="approve" name="审批"/>
                  <endEvent id="end" name="结束"/>
                  <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                  <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        assertThat(model.getProcessId()).isEqualTo("test-process");
        assertThat(model.getProcessName()).isEqualTo("测试流程");
        assertThat(model.getNodes()).hasSize(3);
        assertThat(model.getSkips()).hasSize(2);

        // 节点断言：nodeCode / nodeName / nodeType
        FlowNodeDO start = findNode(model, "start");
        assertThat(start.getNodeName()).isEqualTo("开始");
        assertThat(start.getNodeType()).isEqualTo(FlowNodeType.START.getCode());

        FlowNodeDO approve = findNode(model, "approve");
        assertThat(approve.getNodeName()).isEqualTo("审批");
        assertThat(approve.getNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());

        FlowNodeDO end = findNode(model, "end");
        assertThat(end.getNodeName()).isEqualTo("结束");
        assertThat(end.getNodeType()).isEqualTo(FlowNodeType.END.getCode());

        // 跳转断言：nextNodeCode / nextNodeType（应由目标节点类型补全）
        FlowSkipDO skip1 = findSkipByFlowId(model, "flow1");
        assertThat(skip1.getSkipType()).isEqualTo(FlowSkipType.PASS.name());
        assertThat(skip1.getNextNodeCode()).isEqualTo("approve");
        assertThat(skip1.getNextNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());

        FlowSkipDO skip2 = findSkipByFlowId(model, "flow2");
        assertThat(skip2.getNextNodeCode()).isEqualTo("end");
        assertThat(skip2.getNextNodeType()).isEqualTo(FlowNodeType.END.getCode());
    }

    @Test
    @DisplayName("process 缺少 name 时回退使用 processId 作为 processName")
    void shouldFallbackToProcessIdWhenNameMissing() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="lonely-process" isExecutable="true">
                    <startEvent id="start"/>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """;

        BpmnModel model = parser.parse(xml);

        assertThat(model.getProcessId()).isEqualTo("lonely-process");
        assertThat(model.getProcessName()).isEqualTo("lonely-process");
    }

    // ============================== 2. flowable 扩展属性 ==============================

    @Test
    @DisplayName("解析 flowable 扩展属性：assignee/priority/performType/approveCount/timeoutStrategy 等")
    void shouldParseFlowableExtensionAttributes() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <userTask id="approve" name="审批"
                            flowable:assignee="user:1001"
                            flowable:priority="50"
                            flowable:performType="OR"
                            flowable:approveCount="3"
                            flowable:approveRate="60"
                            flowable:weight="2"
                            flowable:timeoutStrategy="ESCALATE"
                            flowable:timeout="24h"
                            flowable:escalateUser="user:2002"
                            flowable:assigneeType="SELF_SELECT"
                            flowable:async="true"
                            flowable:formKey="leave-form"
                            flowable:dueDate="3d"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                  <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO approve = findNode(model, "approve");

        // assignee=user:1001 已带前缀，permissionFlag 原样保留
        assertThat(approve.getPermissionFlag()).isEqualTo("user:1001");
        // performType=OR 解析为 FlowPerformType.OR，写入 skipAnyNode
        assertThat(approve.getSkipAnyNode()).isEqualTo("OR");

        Map<String, Object> ext = extOf(approve);
        // priority 解析为 int 后写入（兼容 Integer/Long）
        assertThat(((Number) ext.get("priority")).intValue()).isEqualTo(50);
        assertThat(ext).containsEntry("performType", "OR");
        assertThat(ext).containsEntry("approveCount", "3");
        assertThat(ext).containsEntry("approveRate", "60");
        assertThat(ext).containsEntry("weight", "2");
        assertThat(ext).containsEntry("timeoutStrategy", "ESCALATE");
        assertThat(ext).containsEntry("timeout", "24h");
        assertThat(ext).containsEntry("escalateUser", "user:2002");
        assertThat(ext).containsEntry("assigneeType", "SELF_SELECT");
        assertThat(ext).containsEntry("async", true);
        assertThat(ext).containsEntry("formKey", "leave-form");
        assertThat(ext).containsEntry("dueDate", "3d");
    }

    @Test
    @DisplayName("解析 candidateUsers：多候选人逗号分隔展开为 user:u1,user:u2 形式")
    void shouldParseCandidateUsers() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <userTask id="approve" name="审批" flowable:candidateUsers="u1,u2,u3"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                  <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO approve = findNode(model, "approve");
        assertThat(approve.getPermissionFlag()).isEqualTo("user:u1,user:u2,user:u3");
        // 原始 candidateUsers 字符串写入 ext
        assertThat(extOf(approve)).containsEntry("candidateUsers", "u1,u2,u3");
    }

    @Test
    @DisplayName("解析 candidateGroups：候选组补 role: 前缀")
    void shouldParseCandidateGroups() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <userTask id="approve" name="审批" flowable:candidateGroups="hr,finance"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                  <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO approve = findNode(model, "approve");
        assertThat(approve.getPermissionFlag()).isEqualTo("role:hr,role:finance");
        assertThat(extOf(approve)).containsEntry("candidateGroups", "hr,finance");
    }

    @Test
    @DisplayName("解析 assignee 前缀逻辑：裸用户名补 user: 前缀，已带前缀/${expr} 原样保留")
    void shouldParseAssigneePrefixLogic() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <userTask id="u1" name="裸用户名" flowable:assignee="1001"/>
                  <userTask id="u2" name="带前缀" flowable:assignee="user:1001"/>
                  <userTask id="u3" name="角色" flowable:assignee="role:hr"/>
                  <userTask id="u4" name="部门" flowable:assignee="dept:10"/>
                  <userTask id="u5" name="领导" flowable:assignee="leader:1"/>
                  <userTask id="u6" name="岗位" flowable:assignee="position:ceo"/>
                  <userTask id="u7" name="自选" flowable:assignee="self_select:1"/>
                  <userTask id="u8" name="多领导" flowable:assignee="multi_leader:1"/>
                  <userTask id="u9" name="表达式" flowable:assignee="${approver}"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="u1"/>
                  <sequenceFlow id="f2" sourceRef="u1" targetRef="u2"/>
                  <sequenceFlow id="f3" sourceRef="u2" targetRef="u3"/>
                  <sequenceFlow id="f4" sourceRef="u3" targetRef="u4"/>
                  <sequenceFlow id="f5" sourceRef="u4" targetRef="u5"/>
                  <sequenceFlow id="f6" sourceRef="u5" targetRef="u6"/>
                  <sequenceFlow id="f7" sourceRef="u6" targetRef="u7"/>
                  <sequenceFlow id="f8" sourceRef="u7" targetRef="u8"/>
                  <sequenceFlow id="f9" sourceRef="u8" targetRef="u9"/>
                  <sequenceFlow id="f10" sourceRef="u9" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        assertThat(findNode(model, "u1").getPermissionFlag()).isEqualTo("user:1001");
        assertThat(findNode(model, "u2").getPermissionFlag()).isEqualTo("user:1001");
        assertThat(findNode(model, "u3").getPermissionFlag()).isEqualTo("role:hr");
        assertThat(findNode(model, "u4").getPermissionFlag()).isEqualTo("dept:10");
        assertThat(findNode(model, "u5").getPermissionFlag()).isEqualTo("leader:1");
        assertThat(findNode(model, "u6").getPermissionFlag()).isEqualTo("position:ceo");
        assertThat(findNode(model, "u7").getPermissionFlag()).isEqualTo("self_select:1");
        assertThat(findNode(model, "u8").getPermissionFlag()).isEqualTo("multi_leader:1");
        assertThat(findNode(model, "u9").getPermissionFlag()).isEqualTo("${approver}");
    }

    // ============================== 3. 条件表达式 ==============================

    @Test
    @DisplayName("解析条件表达式：裸表达式自动包裹 ${...}，已包裹则原样保留")
    void shouldWrapConditionExpression() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <exclusiveGateway id="gw" name="网关"/>
                  <endEvent id="end1"/>
                  <endEvent id="end2"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="gw"/>
                  <sequenceFlow id="f2" sourceRef="gw" targetRef="end1">
                    <conditionExpression xsi:type="tFormalExpression">approve == true</conditionExpression>
                  </sequenceFlow>
                  <sequenceFlow id="f3" sourceRef="gw" targetRef="end2">
                    <conditionExpression xsi:type="tFormalExpression">${approve == false}</conditionExpression>
                  </sequenceFlow>
                """);

        BpmnModel model = parser.parse(xml);

        // 裸表达式自动包裹
        FlowSkipDO rawSkip = findSkipByFlowId(model, "f2");
        assertThat(rawSkip.getSkipCondition()).isEqualTo("${approve == true}");

        // 已包裹 ${...} 原样保留
        FlowSkipDO wrappedSkip = findSkipByFlowId(model, "f3");
        assertThat(wrappedSkip.getSkipCondition()).isEqualTo("${approve == false}");
    }

    @Test
    @DisplayName("解析条件表达式：#{...} EL 表达式原样保留不重复包裹")
    void shouldKeepElExpressionAsIs() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <exclusiveGateway id="gw"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="gw"/>
                  <sequenceFlow id="f2" sourceRef="gw" targetRef="end">
                    <conditionExpression>#{approve == true}</conditionExpression>
                  </sequenceFlow>
                """);

        BpmnModel model = parser.parse(xml);

        FlowSkipDO skip = findSkipByFlowId(model, "f2");
        assertThat(skip.getSkipCondition()).isEqualTo("#{approve == true}");
    }

    @Test
    @DisplayName("sequenceFlow 的 ext 应记录 sourceRef/targetRef/sequenceFlowId")
    void shouldRecordSequenceFlowRefsInExt() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="flow1" name="通过" sourceRef="start" targetRef="end"
                                flowable:skipExpression="${pass}" flowable:priority="10"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowSkipDO skip = findSkipByFlowId(model, "flow1");
        assertThat(skip.getSkipName()).isEqualTo("通过");
        Map<String, Object> ext = JsonHelper.fromJson(skip.getExt());
        assertThat(ext).containsEntry("sourceRef", "start");
        assertThat(ext).containsEntry("targetRef", "end");
        assertThat(ext).containsEntry("sequenceFlowId", "flow1");
        assertThat(ext).containsEntry("skipExpression", "${pass}");
        assertThat(ext).containsEntry("priority", "10");
    }

    // ============================== 4. 多实例（会签）配置 ==============================

    @Test
    @DisplayName("解析 userTask 多实例配置：completionCondition/loopCardinality/collection 写入 ext")
    void shouldParseMultiInstanceLoopCharacteristics() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <userTask id="approve" name="审批" flowable:assignee="user:1001">
                    <multiInstanceLoopCharacteristics flowable:collection="userList" flowable:elementVariable="user">
                      <completionCondition>${nrOfCompletedInstances/nrOfInstances == 1}</completionCondition>
                      <loopCardinality>3</loopCardinality>
                      <loopDataInputRef>deptUsers</loopDataInputRef>
                    </multiInstanceLoopCharacteristics>
                  </userTask>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="approve"/>
                  <sequenceFlow id="f2" sourceRef="approve" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO approve = findNode(model, "approve");

        // completionCondition 文本写入 skipAnyNode 字段
        assertThat(approve.getSkipAnyNode())
                .isEqualTo("${nrOfCompletedInstances/nrOfInstances == 1}");

        Map<String, Object> ext = extOf(approve);
        // 源码当前实现：multiInstance 段默认 performType=PARALLEL
        assertThat(ext).containsEntry("multiInstance", "PARALLEL");
        assertThat(ext).containsEntry("performType", "PARALLEL");
        assertThat(ext).containsEntry("collection", "userList");
        assertThat(ext).containsEntry("elementVariable", "user");
        assertThat(ext).containsEntry("loopCardinality", "3");
        assertThat(ext).containsEntry("loopDataInputRef", "deptUsers");
    }

    // ============================== 5. 事件定义 ==============================

    @Test
    @DisplayName("解析 timerEventDefinition：timeDuration 写入 ext.timer，标记 nodeFeature=TIMER")
    void shouldParseTimerEventDefinition() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <intermediateCatchEvent id="timer1" name="等待1小时">
                    <timerEventDefinition>
                      <timeDuration>PT1H</timeDuration>
                    </timerEventDefinition>
                  </intermediateCatchEvent>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="timer1"/>
                  <sequenceFlow id="f2" sourceRef="timer1" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO timerNode = findNode(model, "timer1");
        Map<String, Object> ext = extOf(timerNode);

        // timer 子结构
        Object timerObj = ext.get("timer");
        assertThat(timerObj).isInstanceOf(Map.class);
        @SuppressWarnings("unchecked")
        Map<String, Object> timer = (Map<String, Object>) timerObj;
        assertThat(timer).containsEntry("duration", "PT1H");

        // intermediateCatchEvent + timer → 标记 nodeFeature / eventCatch
        assertThat(ext).containsEntry("nodeFeature", "TIMER");
        assertThat(ext).containsEntry("eventCatch", true);
    }

    @Test
    @DisplayName("解析 timerEventDefinition：timeCycle 与 timeDate 同时存在时全部写入")
    void shouldParseTimerCycleAndDate() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <intermediateCatchEvent id="timer2" name="周期定时">
                    <timerEventDefinition>
                      <timeCycle>R5/PT1H</timeCycle>
                      <timeDate>2026-12-31T23:59:59</timeDate>
                    </timerEventDefinition>
                  </intermediateCatchEvent>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="timer2"/>
                  <sequenceFlow id="f2" sourceRef="timer2" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO timerNode = findNode(model, "timer2");
        Map<String, Object> ext = extOf(timerNode);
        @SuppressWarnings("unchecked")
        Map<String, Object> timer = (Map<String, Object>) ext.get("timer");
        assertThat(timer).containsEntry("cycle", "R5/PT1H");
        assertThat(timer).containsEntry("date", "2026-12-31T23:59:59");
    }

    @Test
    @DisplayName("解析 signalEventDefinition：signalRef 写入 ext，eventType=SIGNAL")
    void shouldParseSignalEventDefinition() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <intermediateCatchEvent id="sig1" name="信号捕获">
                    <signalEventDefinition signalRef="alertSignal"/>
                  </intermediateCatchEvent>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="sig1"/>
                  <sequenceFlow id="f2" sourceRef="sig1" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO sigNode = findNode(model, "sig1");
        Map<String, Object> ext = extOf(sigNode);
        assertThat(ext).containsEntry("signalRef", "alertSignal");
        assertThat(ext).containsEntry("eventType", "SIGNAL");
        assertThat(ext).containsEntry("eventCatch", true);
    }

    @Test
    @DisplayName("解析 messageEventDefinition：messageRef 写入 ext，eventType=MESSAGE")
    void shouldParseMessageEventDefinition() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <intermediateCatchEvent id="msg1" name="消息捕获">
                    <messageEventDefinition messageRef="msgCreated"/>
                  </intermediateCatchEvent>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="msg1"/>
                  <sequenceFlow id="f2" sourceRef="msg1" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO msgNode = findNode(model, "msg1");
        Map<String, Object> ext = extOf(msgNode);
        assertThat(ext).containsEntry("messageRef", "msgCreated");
        assertThat(ext).containsEntry("eventType", "MESSAGE");
    }

    @Test
    @DisplayName("解析 errorEventDefinition：errorRef 写入 ext，eventType=ERROR")
    void shouldParseErrorEventDefinition() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <boundaryEvent id="bnd1" name="错误边界" attachedToRef="task1">
                    <errorEventDefinition errorRef="errCode1"/>
                  </boundaryEvent>
                  <userTask id="task1" name="任务"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="f1" sourceRef="start" targetRef="task1"/>
                  <sequenceFlow id="f2" sourceRef="task1" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        FlowNodeDO bndNode = findNode(model, "bnd1");
        Map<String, Object> ext = extOf(bndNode);
        assertThat(ext).containsEntry("errorRef", "errCode1");
        assertThat(ext).containsEntry("eventType", "ERROR");
        // boundaryEvent 解析 attachedToRef
        assertThat(ext).containsEntry("attachedToRef", "task1");
        assertThat(ext).containsEntry("eventCatch", true);
    }

    // ============================== 6. BPMNDI 图形坐标 ==============================

    @Test
    @DisplayName("解析 BPMNDI：BPMNShape 的 Bounds 与 BPMNEdge 的 waypoint 写入坐标映射")
    void shouldParseBpmnDiagramCoordinates() {
        String xml = wrap("""
                  <startEvent id="start" name="开始"/>
                  <endEvent id="end" name="结束"/>
                  <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
                """, """
                  <bpmndi:BPMNDiagram id="diag1">
                    <bpmndi:BPMNPlane bpmnElement="test-process">
                      <bpmndi:BPMNShape id="shape_start" bpmnElement="start">
                        <dc:Bounds x="100" y="80" width="36" height="36"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNShape id="shape_end" bpmnElement="end">
                        <dc:Bounds x="300" y="80" width="36" height="36"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNEdge id="edge_flow1" bpmnElement="flow1">
                        <di:waypoint x="136" y="98"/>
                        <di:waypoint x="300" y="98"/>
                      </bpmndi:BPMNEdge>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                """);

        BpmnModel model = parser.parse(xml);

        // 节点坐标
        Map<String, BpmnModel.NodeCoordinate> nodeCoords = model.getNodeCoordinates();
        assertThat(nodeCoords).containsKeys("start", "end");

        BpmnModel.NodeCoordinate startCoord = nodeCoords.get("start");
        assertThat(startCoord.getX()).isEqualTo(100d);
        assertThat(startCoord.getY()).isEqualTo(80d);
        assertThat(startCoord.getWidth()).isEqualTo(36d);
        assertThat(startCoord.getHeight()).isEqualTo(36d);

        BpmnModel.NodeCoordinate endCoord = nodeCoords.get("end");
        assertThat(endCoord.getX()).isEqualTo(300d);
        assertThat(endCoord.getY()).isEqualTo(80d);

        // 边坐标（折点）
        Map<String, List<BpmnModel.NodeCoordinate>> skipCoords = model.getSkipCoordinates();
        assertThat(skipCoords).containsKey("flow1");
        List<BpmnModel.NodeCoordinate> waypoints = skipCoords.get("flow1");
        assertThat(waypoints).hasSize(2);
        assertThat(waypoints.get(0).getX()).isEqualTo(136d);
        assertThat(waypoints.get(0).getY()).isEqualTo(98d);
        assertThat(waypoints.get(1).getX()).isEqualTo(300d);
        assertThat(waypoints.get(1).getY()).isEqualTo(98d);
    }

    @Test
    @DisplayName("无 BPMNDI 段时坐标映射为空 Map（不抛异常）")
    void shouldReturnEmptyCoordinatesWhenNoBpmnDi() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
                """);

        BpmnModel model = parser.parse(xml);

        assertThat(model.getNodeCoordinates()).isEmpty();
        assertThat(model.getSkipCoordinates()).isEmpty();
    }

    // ============================== 7. 节点类型映射 ==============================

    @Test
    @DisplayName("节点类型映射：7 种 BPMN 元素到 FlowNodeType 的正确转换")
    void shouldMapBpmnElementToFlowNodeType() {
        String xml = wrap("""
                  <startEvent id="s1"/>
                  <userTask id="u1"/>
                  <serviceTask id="sv1"/>
                  <exclusiveGateway id="eg1"/>
                  <parallelGateway id="pg1"/>
                  <inclusiveGateway id="ig1"/>
                  <endEvent id="e1"/>
                """);

        BpmnModel model = parser.parse(xml);

        assertThat(findNode(model, "s1").getNodeType()).isEqualTo(FlowNodeType.START.getCode());
        assertThat(findNode(model, "u1").getNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());
        assertThat(findNode(model, "sv1").getNodeType()).isEqualTo(FlowNodeType.SERVICE.getCode());
        assertThat(findNode(model, "eg1").getNodeType()).isEqualTo(FlowNodeType.CONDITION.getCode());
        assertThat(findNode(model, "pg1").getNodeType()).isEqualTo(FlowNodeType.PARALLEL.getCode());
        assertThat(findNode(model, "ig1").getNodeType()).isEqualTo(FlowNodeType.INCLUSIVE.getCode());
        assertThat(findNode(model, "e1").getNodeType()).isEqualTo(FlowNodeType.END.getCode());
    }

    // ============================== 8. XXE 防护 ==============================

    @Test
    @DisplayName("XXE 防护：包含 DOCTYPE/外部实体声明的 XML 应被拒绝")
    void shouldRejectXxeDoctype() {
        // 构造恶意 XML：通过外部实体引用 /etc/passwd
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [<!ENTITY xxe SYSTEM "file:///etc/passwd">]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="p1">
                    <startEvent id="start">&xxe;</startEvent>
                    <endEvent id="end"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """;

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class);
    }

    // ============================== 9. 非法 XML ==============================

    @Test
    @DisplayName("非法 XML：null/空字符串/纯空白/非 XML 文本均应抛 BizException")
    void shouldThrowOnIllegalXml() {
        assertThatThrownBy(() -> parser.parse(null)).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> parser.parse("")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> parser.parse("   ")).isInstanceOf(BizException.class);
        assertThatThrownBy(() -> parser.parse("not an xml")).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("根元素非 definitions 应抛 BizException")
    void shouldThrowWhenRootIsNotDefinitions() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <notDefinitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="p1"/>
                </notDefinitions>
                """;

        assertThatThrownBy(() -> parser.parse(xml)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("definitions 下缺少 process 元素应抛 BizException")
    void shouldThrowWhenProcessMissing() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"/>
                """;

        assertThatThrownBy(() -> parser.parse(xml)).isInstanceOf(BizException.class);
    }

    // ============================== 10. 缺少 startEvent ==============================

    @Test
    @DisplayName("流程缺少 startEvent 应抛 BizException")
    void shouldThrowWhenStartEventMissing() {
        String xml = wrap("""
                  <userTask id="approve" name="审批"/>
                  <endEvent id="end"/>
                  <sequenceFlow id="flow1" sourceRef="approve" targetRef="end"/>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class);
    }

    // ============================== 11. 重复节点 ID ==============================

    @Test
    @DisplayName("重复节点 ID（id 出现两次）应抛 BizException")
    void shouldThrowWhenNodeIdDuplicates() {
        String xml = wrap("""
                  <startEvent id="start"/>
                  <userTask id="approve" name="审批1"/>
                  <userTask id="approve" name="审批2"/>
                  <endEvent id="end"/>
                """);

        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class);
    }
}