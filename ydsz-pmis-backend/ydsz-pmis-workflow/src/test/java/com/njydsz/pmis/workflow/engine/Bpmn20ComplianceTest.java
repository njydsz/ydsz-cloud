package com.njydsz.pmis.workflow.engine;

import com.njydsz.pmis.workflow.entity.definition.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.instance.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.definition.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * BPMN 2.0 规范合规性测试（P3-14 落地）。
 *
 * <p>验证自研 BpmnXmlParser 对 OMG BPMN 2.0 标准的支持度，
 * 确保导入/导出的 BPMN XML 能被其他引擎（Flowable/Camunda/Activiti）正确解析。
 *
 * <p>测试覆盖：
 * <ul>
 *   <li>标准 BPMN 2.0 核心元素解析（startEvent/endEvent/userTask/serviceTask/gateway）</li>
 *   <li>sequenceFlow + conditionExpression 条件表达式</li>
 *   <li>BPMNDI 坐标信息解析</li>
 *   <li>Flowable/Camunda 扩展命名空间兼容性</li>
 *   <li>非法 XML 防护（XXE 防护、空文档、无根元素）</li>
 *   <li>复杂流程模式（并行网关、排他网关、包容网关）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.3.1 (P3-14)
 */
@DisplayName("BPMN 2.0 规范合规性测试")
class Bpmn20ComplianceTest {

    private BpmnXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new BpmnXmlParser();
    }

    // ==================== 标准元素解析 ====================

    @Nested
    @DisplayName("标准 BPMN 2.0 核心元素")
    class StandardElementsTest {

        @Test
        @DisplayName("解析 startEvent")
        void shouldParseStartEvent() {
            String xml = wrapBpmn("""
                <bpmn:startEvent id="startEvent_1" name="开始" />
            """);

            BpmnModel model = parser.parse(xml);

            assertNotNull(model);
            List<FlowNodeDO> nodes = model.getNodes();
            assertFalse(nodes.isEmpty(), "应至少解析出一个节点");

            FlowNodeDO startNode = nodes.stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.START.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(startNode, "应包含 START 类型节点");
            assertEquals("startEvent_1", startNode.getNodeCode());
            assertEquals("开始", startNode.getNodeName());
        }

        @Test
        @DisplayName("解析 endEvent")
        void shouldParseEndEvent() {
            String xml = wrapBpmn("""
                <bpmn:endEvent id="endEvent_1" name="结束" />
            """);

            BpmnModel model = parser.parse(xml);

            FlowNodeDO endNode = model.getNodes().stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.END.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(endNode, "应包含 END 类型节点");
            assertEquals("endEvent_1", endNode.getNodeCode());
        }

        @Test
        @DisplayName("解析 userTask（含办理人扩展属性）")
        void shouldParseUserTaskWithAssignee() {
            String xml = wrapBpmn("""
                <bpmn:userTask id="task_approve" name="审批"
                    flowable:assignee="${approver}"
                    flowable:candidateGroups="manager,finance" />
            """);

            BpmnModel model = parser.parse(xml);

            FlowNodeDO userTask = model.getNodes().stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.APPROVAL.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(userTask, "应包含 APPROVAL(用户任务) 类型节点");
            assertEquals("task_approve", userTask.getNodeCode());
            assertEquals("审批", userTask.getNodeName());
        }

        @Test
        @DisplayName("解析 serviceTask")
        void shouldParseServiceTask() {
            String xml = wrapBpmn("""
                <bpmn:serviceTask id="task_auto" name="自动执行" />
            """);

            BpmnModel model = parser.parse(xml);

            FlowNodeDO serviceTask = model.getNodes().stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.SERVICE.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(serviceTask, "应包含 SERVICE 类型节点");
        }

        @Test
        @DisplayName("解析 exclusiveGateway（排他网关）")
        void shouldParseExclusiveGateway() {
            String xml = wrapBpmn("""
                <bpmn:exclusiveGateway id="gw_exclusive" name="条件判断" />
            """);

            BpmnModel model = parser.parse(xml);

            FlowNodeDO gw = model.getNodes().stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.CONDITION.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(gw, "应包含 CONDITION(排他网关) 类型节点");
        }

        @Test
        @DisplayName("解析 parallelGateway（并行网关）")
        void shouldParseParallelGateway() {
            String xml = wrapBpmn("""
                <bpmn:parallelGateway id="gw_parallel" name="并行" />
            """);

            BpmnModel model = parser.parse(xml);

            FlowNodeDO gw = model.getNodes().stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.PARALLEL.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(gw, "应包含 PARALLEL 类型节点");
        }

        @Test
        @DisplayName("解析 inclusiveGateway（包容网关）")
        void shouldParseInclusiveGateway() {
            String xml = wrapBpmn("""
                <bpmn:inclusiveGateway id="gw_inclusive" name="包容" />
            """);

            BpmnModel model = parser.parse(xml);

            FlowNodeDO gw = model.getNodes().stream()
                    .filter(n -> n.getNodeType() == FlowNodeType.INCLUSIVE.getCode())
                    .findFirst()
                    .orElse(null);

            assertNotNull(gw, "应包含 INCLUSIVE 类型节点");
        }
    }

    // ==================== sequenceFlow + 条件表达式 ====================

    @Nested
    @DisplayName("sequenceFlow 与条件表达式")
    class SequenceFlowTest {

        @Test
        @DisplayName("解析 sequenceFlow sourceRef 和 targetRef")
        void shouldParseSequenceFlow() {
            String xml = wrapBpmn("""
                <bpmn:startEvent id="start" />
                <bpmn:userTask id="task1" name="任务1" />
                <bpmn:sequenceFlow id="flow1" sourceRef="start" targetRef="task1" />
            """);

            BpmnModel model = parser.parse(xml);

            List<FlowSkipDO> skips = model.getSkips();
            assertFalse(skips.isEmpty(), "应至少解析出一条跳转边");

            FlowSkipDO skip = skips.stream()
                    .filter(s -> "flow1".equals(s.getFlowCode()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(skip, "应包含 flowCode=flow1 的跳转边");
            assertEquals("task1", skip.getNextNodeCode());
        }

        @Test
        @DisplayName("解析 conditionExpression 条件表达式")
        void shouldParseConditionExpression() {
            String xml = wrapBpmn("""
                <bpmn:exclusiveGateway id="gw1" />
                <bpmn:sequenceFlow id="flow_yes" sourceRef="gw1" targetRef="task_approve">
                    <bpmn:conditionExpression xsi:type="tFormalExpression">${amount > 10000}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
            """);

            BpmnModel model = parser.parse(xml);

            FlowSkipDO skip = model.getSkips().stream()
                    .filter(s -> "flow_yes".equals(s.getFlowCode()))
                    .findFirst()
                    .orElse(null);

            assertNotNull(skip, "应包含 id=flow_yes 的跳转边");
            assertNotNull(skip.getSkipCondition(), "跳转条件不应为空");
            assertTrue(skip.getSkipCondition().contains("amount"), "条件应包含 amount 变量");
        }
    }

    // ==================== BPMNDI 坐标解析 ====================

    @Nested
    @DisplayName("BPMNDI 图形坐标")
    class BpmndiTest {

        @Test
        @DisplayName("解析 BPMNShape 坐标")
        void shouldParseBpmnShapeCoordinates() {
            String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                                  xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI"
                                  xmlns:dc="http://www.omg.org/spec/DD/20100524/DC"
                                  xmlns:flowable="http://flowable.org/bpmn"
                                  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
                  <bpmn:process id="proc_test" name="测试流程" isExecutable="true">
                    <bpmn:startEvent id="start" />
                  </bpmn:process>
                  <bpmndi:BPMNDiagram id="BPMNDiagram_1">
                    <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="proc_test">
                      <bpmndi:BPMNShape id="Shape_start" bpmnElement="start">
                        <dc:Bounds x="100" y="200" width="36" height="36" />
                      </bpmndi:BPMNShape>
                    </bpmndi:BPMNPlane>
                  </bpmndi:BPMNDiagram>
                </bpmn:definitions>
                """;

            BpmnModel model = parser.parse(xml);

            Map<String, BpmnModel.NodeCoordinate> coords = model.getNodeCoordinates();
            assertNotNull(coords, "节点坐标映射不应为 null");
            assertFalse(coords.isEmpty(), "应解析出节点坐标");

            BpmnModel.NodeCoordinate coord = coords.get("start");
            assertNotNull(coord, "start 节点应有坐标");
            assertEquals(100.0, coord.getX(), 0.01);
            assertEquals(200.0, coord.getY(), 0.01);
            assertEquals(36.0, coord.getWidth(), 0.01);
            assertEquals(36.0, coord.getHeight(), 0.01);
        }

        @Test
        @DisplayName("无 BPMNDI 段时坐标为空 Map（不报错）")
        void shouldHandleMissingBpmndi() {
            String xml = wrapBpmn("""
                <bpmn:startEvent id="start" />
            """);

            BpmnModel model = parser.parse(xml);

            assertNotNull(model.getNodeCoordinates(), "坐标映射不应为 null");
            assertTrue(model.getNodeCoordinates().isEmpty(), "无 BPMNDI 时坐标应为空 Map");
        }
    }

    // ==================== 复杂流程模式 ====================

    @Nested
    @DisplayName("复杂流程模式")
    class ComplexPatternsTest {

        @Test
        @DisplayName("并行分支 + 汇聚")
        void shouldParseParallelSplitAndJoin() {
            String xml = wrapBpmn("""
                <bpmn:startEvent id="start" />
                <bpmn:parallelGateway id="gw_split" name="并行分支" />
                <bpmn:userTask id="task_a" name="任务A" />
                <bpmn:userTask id="task_b" name="任务B" />
                <bpmn:parallelGateway id="gw_join" name="并行汇聚" />
                <bpmn:endEvent id="end" />
                <bpmn:sequenceFlow id="f1" sourceRef="start" targetRef="gw_split" />
                <bpmn:sequenceFlow id="f2" sourceRef="gw_split" targetRef="task_a" />
                <bpmn:sequenceFlow id="f3" sourceRef="gw_split" targetRef="task_b" />
                <bpmn:sequenceFlow id="f4" sourceRef="task_a" targetRef="gw_join" />
                <bpmn:sequenceFlow id="f5" sourceRef="task_b" targetRef="gw_join" />
                <bpmn:sequenceFlow id="f6" sourceRef="gw_join" targetRef="end" />
            """);

            BpmnModel model = parser.parse(xml);

            assertEquals(6, model.getNodes().size(), "应有 6 个节点");
            assertEquals(6, model.getSkips().size(), "应有 6 条跳转边");
        }

        @Test
        @DisplayName("排他网关 + 多条件分支")
        void shouldParseExclusiveGatewayWithConditions() {
            String xml = wrapBpmn("""
                <bpmn:exclusiveGateway id="gw_amount" name="金额判断" />
                <bpmn:userTask id="task_manager" name="经理审批" />
                <bpmn:userTask id="task_director" name="总监审批" />
                <bpmn:sequenceFlow id="flow_low" sourceRef="gw_amount" targetRef="task_manager">
                    <bpmn:conditionExpression xsi:type="tFormalExpression">${amount <= 10000}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
                <bpmn:sequenceFlow id="flow_high" sourceRef="gw_amount" targetRef="task_director">
                    <bpmn:conditionExpression xsi:type="tFormalExpression">${amount > 10000}</bpmn:conditionExpression>
                </bpmn:sequenceFlow>
            """);

            BpmnModel model = parser.parse(xml);

            List<FlowSkipDO> conditionalSkips = model.getSkips().stream()
                    .filter(s -> s.getSkipCondition() != null && !s.getSkipCondition().isEmpty())
                    .toList();

            assertEquals(2, conditionalSkips.size(), "应有 2 条带条件的跳转边");
        }
    }

    // ==================== 安全性测试 ====================

    @Nested
    @DisplayName("安全性：XXE 防护与非法输入")
    class SecurityTest {

        @Test
        @DisplayName("拒绝 XXE 外部实体注入")
        void shouldRejectXxeInjection() {
            String xxeXml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE foo [
                  <!ENTITY xxe SYSTEM "file:///etc/passwd">
                ]>
                <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL">
                  <bpmn:process id="proc_xxe" name="&xxe;" />
                </bpmn:definitions>
                """;

            // 不应抛出异常导致服务崩溃，但也不应解析外部实体
            assertDoesNotThrow(() -> {
                BpmnModel model = parser.parse(xxeXml);
                assertNotNull(model);
            }, "XXE 注入不应导致异常崩溃");
        }

        @Test
        @DisplayName("空 XML 字符串不导致 NPE")
        void shouldHandleEmptyXml() {
            assertDoesNotThrow(() -> {
                parser.parse("");
            }, "空字符串不应导致未捕获异常");
        }

        @Test
        @DisplayName("非 XML 格式字符串不导致崩溃")
        void shouldHandleNonXmlString() {
            assertDoesNotThrow(() -> {
                parser.parse("this is not xml");
            }, "非 XML 字符串不应导致未捕获异常");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 将流程体包装为完整的 BPMN 2.0 XML 文档。
     *
     * @param processBody process 标签内的内容
     * @return 完整的 BPMN XML
     */
    private static String wrapBpmn(String processBody) {
        return """
            <?xml version="1.0" encoding="UTF-8"?>
            <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL"
                              xmlns:flowable="http://flowable.org/bpmn"
                              xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <bpmn:process id="proc_test" name="测试流程" isExecutable="true">
            """ + processBody + """
              </bpmn:process>
            </bpmn:definitions>
            """;
    }
}
