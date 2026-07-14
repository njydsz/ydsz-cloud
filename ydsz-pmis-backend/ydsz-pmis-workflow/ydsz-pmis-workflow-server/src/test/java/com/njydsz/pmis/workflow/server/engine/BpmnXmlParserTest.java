package com.njydsz.pmis.workflow.server.engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.njydsz.pmis.common.exception.custom.SysException;
import com.njydsz.pmis.workflow.domain.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.domain.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.domain.enums.FlowNodeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BpmnXmlParser 单元测试
 *
 * <p>纯单元测试，不依赖 Spring 上下文。覆盖 BPMN 2.0 XML 解析为 BpmnModel 的各种场景，
 * 包括异常输入、线性流程、网关、办理人、会签、条件表达式、BPMNDI 坐标、事件定义、
 * 服务节点、子流程以及 XXE 防护等。
 */
@DisplayName("BpmnXmlParser BPMN XML 解析器测试")
class BpmnXmlParserTest {

    private final BpmnXmlParser parser = new BpmnXmlParser();

    // ==================== 异常输入测试 ====================

    @Test
    @DisplayName("null 输入抛 SysException")
    void testParseNullXml() {
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("空白字符串抛 SysException")
    void testParseBlankXml() {
        assertThatThrownBy(() -> parser.parse("   \n\t "))
                .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("根元素非 definitions 抛 SysException")
    void testParseInvalidRoot() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <notDefinitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                </notDefinitions>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("缺少 process 元素抛 SysException")
    void testParseNoProcess() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("无 startEvent 抛 SysException")
    void testParseNoStartEvent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="noStart" name="无开始节点" isExecutable="true">
                    <userTask id="task1" name="任务"/>
                    <endEvent id="end" name="结束"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("重复节点 id 抛 SysException")
    void testParseDuplicateNodeCode() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="dupFlow" name="重复节点流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="task1" name="任务1"/>
                    <userTask id="task1" name="任务2"/>
                    <endEvent id="end" name="结束"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    // ==================== 基本流程解析测试 ====================

    @Test
    @DisplayName("线性流程 start→userTask→end，验证 3 个节点、2 条边、节点类型正确")
    void testParseSimpleLinearFlow() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="linearFlow" name="线性审批流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批" flowable:assignee="1001"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        // 验证流程基本信息
        assertThat(model.getProcessId()).isEqualTo("linearFlow");
        assertThat(model.getProcessName()).isEqualTo("线性审批流程");

        // 验证节点数量与类型
        assertThat(model.getNodes()).hasSize(3);
        FlowNodeDO startNode = findNode(model, "start");
        FlowNodeDO approveNode = findNode(model, "approve");
        FlowNodeDO endNode = findNode(model, "end");
        assertThat(startNode.getNodeType()).isEqualTo(FlowNodeType.START.getCode());
        assertThat(approveNode.getNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());
        assertThat(endNode.getNodeType()).isEqualTo(FlowNodeType.END.getCode());

        // 验证边数量与跳转类型
        assertThat(model.getSkips()).hasSize(2);
        FlowSkipDO skip1 = findSkipByTarget(model, "approve");
        FlowSkipDO skip2 = findSkipByTarget(model, "end");
        assertThat(skip1.getSkipType()).isEqualTo("PASS");
        assertThat(skip2.getSkipType()).isEqualTo("PASS");

        // 验证下一节点类型回填
        assertThat(skip1.getNextNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());
        assertThat(skip2.getNextNodeType()).isEqualTo(FlowNodeType.END.getCode());
    }

    // ==================== 网关测试 ====================

    @Test
    @DisplayName("排他网关，验证 CONDITION(3) 类型、条件表达式解析")
    void testParseExclusiveGateway() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <process id="exclusiveFlow" name="排他网关流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <exclusiveGateway id="gw1" name="条件判断"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="gw1"/>
                    <sequenceFlow id="flow2" sourceRef="gw1" targetRef="end">
                      <conditionExpression xsi:type="tFormalExpression">${approved == true}</conditionExpression>
                    </sequenceFlow>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        // 验证排他网关映射为 CONDITION(3)
        FlowNodeDO gwNode = findNode(model, "gw1");
        assertThat(gwNode.getNodeType()).isEqualTo(FlowNodeType.CONDITION.getCode());

        // 验证条件表达式解析
        FlowSkipDO condSkip = findSkipByTarget(model, "end");
        assertThat(condSkip.getSkipCondition()).isEqualTo("${approved == true}");
    }

    @Test
    @DisplayName("并行网关，验证 PARALLEL(4) 类型")
    void testParseParallelGateway() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="parallelFlow" name="并行网关流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <parallelGateway id="fork" name="并行分支"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="fork"/>
                    <sequenceFlow id="flow2" sourceRef="fork" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO forkNode = findNode(model, "fork");
        assertThat(forkNode.getNodeType()).isEqualTo(FlowNodeType.PARALLEL.getCode());
    }

    @Test
    @DisplayName("包容网关，验证 INCLUSIVE(5) 类型")
    void testParseInclusiveGateway() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="inclusiveFlow" name="包容网关流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <inclusiveGateway id="gw1" name="包容网关"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="gw1"/>
                    <sequenceFlow id="flow2" sourceRef="gw1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO gwNode = findNode(model, "gw1");
        assertThat(gwNode.getNodeType()).isEqualTo(FlowNodeType.INCLUSIVE.getCode());
    }

    // ==================== 办理人解析测试 ====================

    @Test
    @DisplayName("flowable:assignee=\"1001\" → permissionFlag=\"user:1001\"")
    void testParseUserTaskAssignee() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="assigneeFlow" name="指定办理人" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批" flowable:assignee="1001"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO approveNode = findNode(model, "approve");
        assertThat(approveNode.getPermissionFlag()).isEqualTo("user:1001");
    }

    @Test
    @DisplayName("flowable:expression=\"${approver}\" → permissionFlag=\"${approver}\"")
    void testParseUserTaskExpression() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="exprFlow" name="表达式办理人" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批" flowable:expression="${approver}"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO approveNode = findNode(model, "approve");
        assertThat(approveNode.getPermissionFlag()).isEqualTo("${approver}");
    }

    @Test
    @DisplayName("flowable:candidateUsers=\"u1,u2\" → permissionFlag 包含 \"user:u1,user:u2\"")
    void testParseUserTaskCandidateUsers() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="candidateUsersFlow" name="候选用户" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批" flowable:candidateUsers="u1,u2"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO approveNode = findNode(model, "approve");
        assertThat(approveNode.getPermissionFlag()).contains("user:u1");
        assertThat(approveNode.getPermissionFlag()).contains("user:u2");
    }

    @Test
    @DisplayName("flowable:candidateGroups=\"g1,g2\" → permissionFlag 包含 \"role:g1,role:g2\"")
    void testParseUserTaskCandidateGroups() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="candidateGroupsFlow" name="候选组" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批" flowable:candidateGroups="g1,g2"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO approveNode = findNode(model, "approve");
        assertThat(approveNode.getPermissionFlag()).contains("role:g1");
        assertThat(approveNode.getPermissionFlag()).contains("role:g2");
    }

    @Test
    @DisplayName("multiInstanceLoopCharacteristics 并行会签，验证 ext 中 multiInstance=PARALLEL")
    void testParseMultiInstanceParallel() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="multiInstanceFlow" name="并行会签" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="会签审批">
                      <multiInstanceLoopCharacteristics isSequential="false">
                        <completionCondition>${nrOfCompletedInstances/nrOfInstances >= 1}</completionCondition>
                      </multiInstanceLoopCharacteristics>
                    </userTask>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO approveNode = findNode(model, "approve");
        // 验证 ext 中包含 multiInstance=PARALLEL
        assertThat(approveNode.getExt()).contains("\"multiInstance\":\"PARALLEL\"");
    }

    // ==================== 条件表达式测试 ====================

    @Test
    @DisplayName("sequenceFlow 上的 conditionExpression，验证 skipCondition")
    void testParseConditionExpression() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <process id="condFlow" name="条件表达式流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve">
                      <conditionExpression xsi:type="tFormalExpression">${amount > 1000}</conditionExpression>
                    </sequenceFlow>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowSkipDO skip = findSkipByTarget(model, "approve");
        assertThat(skip.getSkipCondition()).isEqualTo("${amount > 1000}");
    }

    // ==================== BPMNDI 坐标测试 ====================

    @Test
    @DisplayName("BPMNDI 段坐标解析，验证 nodeCoordinates 非空")
    void testParseBpmnDiagram() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn"
                             xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                             xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                             xmlns:di="http://www.omg.org/spec/DD/20100524/DI">
                  <process id="diagramFlow" name="坐标流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <userTask id="approve" name="审批"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="approve"/>
                    <sequenceFlow id="flow2" sourceRef="approve" targetRef="end"/>
                  </process>
                  <bpmndi:BPMNDiagram id="diag1">
                    <bpmndi:BPMNPlane bpmnElement="diagramFlow">
                      <bpmndi:BPMNShape id="shape_start" bpmnElement="start">
                        <dc:Bounds x="100" y="100" width="30" height="30"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNShape id="shape_approve" bpmnElement="approve">
                        <dc:Bounds x="200" y="100" width="80" height="60"/>
                      </bpmndi:BPMNShape>
                      <bpmndi:BPMNEdge id="edge_flow1" bpmnElement="flow1">
                        <di:waypoint x="130" y="115"/>
                        <di:waypoint x="200" y="115"/>
                      </bpmndi:BPMNEdge>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        // 验证节点坐标非空
        assertThat(model.getNodeCoordinates()).isNotEmpty();
        assertThat(model.getNodeCoordinates()).containsKey("start");
        assertThat(model.getNodeCoordinates()).containsKey("approve");

        // 验证坐标值
        BpmnModel.NodeCoordinate startCoord = model.getNodeCoordinates().get("start");
        assertThat(startCoord.getX()).isEqualTo(100.0);
        assertThat(startCoord.getY()).isEqualTo(100.0);
        assertThat(startCoord.getWidth()).isEqualTo(30.0);
        assertThat(startCoord.getHeight()).isEqualTo(30.0);

        // 验证边坐标非空
        assertThat(model.getSkipCoordinates()).isNotEmpty();
        assertThat(model.getSkipCoordinates()).containsKey("flow1");
        assertThat(model.getSkipCoordinates().get("flow1")).hasSize(2);
    }

    @Test
    @DisplayName("无 BPMNDI 段，nodeCoordinates 为空 Map")
    void testParseNoBpmnDiagram() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="noDiFlow" name="无坐标流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        // 无 BPMNDI 段时，坐标 Map 为空（非 null）
        assertThat(model.getNodeCoordinates()).isEmpty();
        assertThat(model.getSkipCoordinates()).isEmpty();
    }

    // ==================== 不支持的网关测试 ====================

    @Test
    @DisplayName("eventBasedGateway 抛 SysException")
    void testParseEventBasedGatewayRejected() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="eventGwFlow" name="事件网关流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <eventBasedGateway id="gw1" name="事件网关"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="gw1"/>
                    <sequenceFlow id="flow2" sourceRef="gw1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    @Test
    @DisplayName("complexGateway 抛 SysException")
    void testParseComplexGatewayRejected() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="complexGwFlow" name="复杂网关流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <complexGateway id="gw1" name="复杂网关"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="gw1"/>
                    <sequenceFlow id="flow2" sourceRef="gw1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    // ==================== 事件定义测试 ====================

    @Test
    @DisplayName("intermediateCatchEvent 包含 timerEventDefinition，验证 ext 中 timer 字段")
    void testParseTimerEvent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="timerFlow" name="定时器流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <intermediateCatchEvent id="timer1" name="定时捕获">
                      <timerEventDefinition>
                        <timeCycle>R/PT1H</timeCycle>
                      </timerEventDefinition>
                    </intermediateCatchEvent>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="timer1"/>
                    <sequenceFlow id="flow2" sourceRef="timer1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO timerNode = findNode(model, "timer1");
        // 验证 ext 中包含 timer 字段及周期值
        assertThat(timerNode.getExt()).contains("\"timer\"");
        assertThat(timerNode.getExt()).contains("\"cycle\":\"R/PT1H\"");
    }

    @Test
    @DisplayName("signalEventDefinition，验证 ext 中 eventType=SIGNAL")
    void testParseSignalEvent() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="signalFlow" name="信号流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <intermediateCatchEvent id="sig1" name="信号捕获">
                      <signalEventDefinition signalRef="signal1"/>
                    </intermediateCatchEvent>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="sig1"/>
                    <sequenceFlow id="flow2" sourceRef="sig1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO sigNode = findNode(model, "sig1");
        // 验证 ext 中包含 eventType=SIGNAL
        assertThat(sigNode.getExt()).contains("\"eventType\":\"SIGNAL\"");
    }

    // ==================== 服务节点与子流程测试 ====================

    @Test
    @DisplayName("serviceTask 映射为 SERVICE(8)")
    void testParseServiceTask() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="serviceFlow" name="服务节点流程" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <serviceTask id="svc1" name="自动执行"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="svc1"/>
                    <sequenceFlow id="flow2" sourceRef="svc1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO svcNode = findNode(model, "svc1");
        assertThat(svcNode.getNodeType()).isEqualTo(FlowNodeType.SERVICE.getCode());
    }

    @Test
    @DisplayName("callActivity 映射为 SUBPROCESS(7)")
    void testParseSubProcess() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL"
                             xmlns:flowable="http://flowable.org/bpmn">
                  <process id="subFlow" name="子流程调用" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <callActivity id="sub1" name="子流程调用" calledElement="subProcess1"/>
                    <endEvent id="end" name="结束"/>
                    <sequenceFlow id="flow1" sourceRef="start" targetRef="sub1"/>
                    <sequenceFlow id="flow2" sourceRef="sub1" targetRef="end"/>
                  </process>
                </definitions>
                """;
        BpmnModel model = parser.parse(xml);

        FlowNodeDO subNode = findNode(model, "sub1");
        assertThat(subNode.getNodeType()).isEqualTo(FlowNodeType.SUBPROCESS.getCode());
    }

    // ==================== 安全测试 ====================

    @Test
    @DisplayName("包含 DOCTYPE/外部实体定义的 XML 抛异常（XXE 防护）")
    void testParseXXEProtection() {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <definitions xmlns="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <process id="xxeFlow" name="XXE防护测试" isExecutable="true">
                    <startEvent id="start" name="开始"/>
                    <endEvent id="end" name="结束"/>
                  </process>
                </definitions>
                """;
        // XXE 防护：DOCTYPE 声明被解析器拒绝，抛出 SysException
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(SysException.class);
    }

    // ==================== 辅助方法 ====================

    /**
     * 按 nodeCode 从模型中查找节点
     *
     * @param model    BPMN 模型
     * @param nodeCode 节点编码
     * @return 匹配的节点
     */
    private FlowNodeDO findNode(BpmnModel model, String nodeCode) {
        return model.getNodes().stream()
                .filter(n -> nodeCode.equals(n.getNodeCode()))
                .findFirst()
                .orElseThrow();
    }

    /**
     * 按 nextNodeCode（目标节点编码）从模型中查找跳转
     *
     * @param model           BPMN 模型
     * @param targetNodeCode  目标节点编码
     * @return 匹配的跳转
     */
    private FlowSkipDO findSkipByTarget(BpmnModel model, String targetNodeCode) {
        return model.getSkips().stream()
                .filter(s -> targetNodeCode.equals(s.getNextNodeCode()))
                .findFirst()
                .orElseThrow();
    }
}
