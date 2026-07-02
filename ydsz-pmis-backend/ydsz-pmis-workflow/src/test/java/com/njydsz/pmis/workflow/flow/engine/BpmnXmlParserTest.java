package com.njydsz.pmis.workflow.flow.engine;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.enums.FlowNodeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * BpmnXmlParser 单元测试
 *
 * <p>覆盖标准 BPMN 2.0 流程的解析能力：startEvent / endEvent / userTask / gateway / sequenceFlow / condition。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("BpmnXmlParser 单元测试")
class BpmnXmlParserTest {

    private BpmnXmlParser parser;

    @BeforeEach
    void setUp() {
        parser = new BpmnXmlParser();
    }

    @Test
    @DisplayName("解析空 XML 应抛 BAD_REQUEST")
    void testParseEmpty() {
        assertThatThrownBy(() -> parser.parse(""))
                .isInstanceOf(BizException.class);
        assertThatThrownBy(() -> parser.parse(null))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("缺少 <process> 节点应抛 BAD_REQUEST")
    void testMissingProcess() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "</definitions>";
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("<process>");
    }

    @Test
    @DisplayName("缺少开始节点应抛 BAD_REQUEST")
    void testMissingStartEvent() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"p1\" name=\"测试\">\n" +
                "    <endEvent id=\"end1\" name=\"结束\"/>\n" +
                "  </process>\n" +
                "</definitions>";
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("startEvent");
    }

    @Test
    @DisplayName("解析标准线性流程：start → userTask → end")
    void testParseLinearFlow() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"pmis_leave\" name=\"请假流程\">\n" +
                "    <startEvent id=\"startEvent\" name=\"提交\"/>\n" +
                "    <userTask id=\"managerApprove\" name=\"上级审批\"\n" +
                "              flowable:assignee=\"user:1001\"/>\n" +
                "    <endEvent id=\"endEvent\" name=\"结束\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"startEvent\" targetRef=\"managerApprove\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"managerApprove\" targetRef=\"endEvent\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        assertThat(model.getProcessId()).isEqualTo("pmis_leave");
        assertThat(model.getProcessName()).isEqualTo("请假流程");
        assertThat(model.getNodes()).hasSize(3);
        assertThat(model.getSkips()).hasSize(2);

        // 节点类型
        FlowNodeDO start = findNode(model.getNodes(), "startEvent");
        assertThat(start.getNodeType()).isEqualTo(FlowNodeType.START.getCode());
        FlowNodeDO userTask = findNode(model.getNodes(), "managerApprove");
        assertThat(userTask.getNodeType()).isEqualTo(FlowNodeType.APPROVAL.getCode());
        assertThat(userTask.getPermissionFlag()).isEqualTo("user:1001");
        FlowNodeDO end = findNode(model.getNodes(), "endEvent");
        assertThat(end.getNodeType()).isEqualTo(FlowNodeType.END.getCode());

        // 跳转：nextNodeCode 解析为 targetRef
        assertThat(model.getSkips()).extracting(FlowSkipDO::getNextNodeCode)
                .containsExactlyInAnyOrder("managerApprove", "endEvent");
    }

    @Test
    @DisplayName("解析排他网关 + 条件表达式：approved == true")
    void testParseExclusiveGatewayWithCondition() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n" +
                "  <process id=\"p1\" name=\"审批\">\n" +
                "    <startEvent id=\"start1\"/>\n" +
                "    <exclusiveGateway id=\"gw1\"/>\n" +
                "    <endEvent id=\"approveEnd\" name=\"通过\"/>\n" +
                "    <endEvent id=\"rejectEnd\" name=\"驳回\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"start1\" targetRef=\"gw1\"/>\n" +
                "    <sequenceFlow id=\"f2\" name=\"通过\" sourceRef=\"gw1\" targetRef=\"approveEnd\">\n" +
                "      <conditionExpression xsi:type=\"tFormalExpression\">${approved == true}</conditionExpression>\n" +
                "    </sequenceFlow>\n" +
                "    <sequenceFlow id=\"f3\" name=\"驳回\" sourceRef=\"gw1\" targetRef=\"rejectEnd\">\n" +
                "      <conditionExpression xsi:type=\"tFormalExpression\">${approved == false}</conditionExpression>\n" +
                "    </sequenceFlow>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        // 网关映射为 CONDITION
        FlowNodeDO gw = findNode(model.getNodes(), "gw1");
        assertThat(gw.getNodeType()).isEqualTo(FlowNodeType.CONDITION.getCode());

        // 条件表达式被自动加 ${}
        assertThat(model.getSkips()).hasSize(3);
        FlowSkipDO approveSkip = model.getSkips().stream()
                .filter(s -> "approveEnd".equals(s.getNextNodeCode()))
                .findFirst().orElseThrow();
        assertThat(approveSkip.getSkipCondition()).isEqualTo("${approved == true}");
    }

    @Test
    @DisplayName("解析并行网关：分支同时推进")
    void testParseParallelGateway() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"p1\" name=\"并行\">\n" +
                "    <startEvent id=\"start1\"/>\n" +
                "    <parallelGateway id=\"fork\"/>\n" +
                "    <parallelGateway id=\"join\"/>\n" +
                "    <userTask id=\"task1\" name=\"分支1\"/>\n" +
                "    <userTask id=\"task2\" name=\"分支2\"/>\n" +
                "    <endEvent id=\"end1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"start1\" targetRef=\"fork\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"fork\" targetRef=\"task1\"/>\n" +
                "    <sequenceFlow id=\"f3\" sourceRef=\"fork\" targetRef=\"task2\"/>\n" +
                "    <sequenceFlow id=\"f4\" sourceRef=\"task1\" targetRef=\"join\"/>\n" +
                "    <sequenceFlow id=\"f5\" sourceRef=\"task2\" targetRef=\"join\"/>\n" +
                "    <sequenceFlow id=\"f6\" sourceRef=\"join\" targetRef=\"end1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        assertThat(model.getNodes()).hasSize(6);
        FlowNodeDO fork = findNode(model.getNodes(), "fork");
        assertThat(fork.getNodeType()).isEqualTo(FlowNodeType.PARALLEL.getCode());
        FlowNodeDO join = findNode(model.getNodes(), "join");
        assertThat(join.getNodeType()).isEqualTo(FlowNodeType.PARALLEL.getCode());
    }

    @Test
    @DisplayName("userTask candidateUsers 多人全部写入 permissionFlag（P2-15）")
    void testParseCandidateUsers() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"多候选人\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"会签\" flowable:candidateUsers=\"1001,1002,1003\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // P2-15: 三人均带 user: 前缀写入 permissionFlag，由 expandAssignees 展开为多人
        assertThat(t1.getPermissionFlag()).isEqualTo("user:1001,user:1002,user:1003");
        assertThat(t1.getExt()).contains("candidateUsers").contains("1001,1002,1003");
    }

    @Test
    @DisplayName("userTask candidateUsers 含已带前缀的混合形式（P2-15）")
    void testParseCandidateUsersMixedPrefix() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"混合\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"混合\" flowable:candidateUsers=\"user:1001,role:hr,1002\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // 已带前缀原样保留，无前缀补 user:
        assertThat(t1.getPermissionFlag()).isEqualTo("user:1001,role:hr,user:1002");
    }

    @Test
    @DisplayName("userTask candidateGroups 多组全部写入 permissionFlag（P2-15）")
    void testParseCandidateGroups() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"多组\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"多组\" flowable:candidateGroups=\"hr,finance\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        assertThat(t1.getPermissionFlag()).isEqualTo("role:hr,role:finance");
    }

    @Test
    @DisplayName("userTask assignee = SpEL 表达式原样保留")
    void testParseSpelAssignee() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"SpEL\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"动态\" flowable:assignee=\"${initiatorId}\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        assertThat(t1.getPermissionFlag()).isEqualTo("${initiatorId}");
    }

    @Test
    @DisplayName("userTask assignee = leader:1001 原样保留（P2-19）")
    void testParseLeaderAssignee() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"上级审批\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"直属上级\" flowable:assignee=\"leader:1001\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // P2-19: leader: 前缀原样保留，由 SPI 展开为具体上级用户
        assertThat(t1.getPermissionFlag()).isEqualTo("leader:1001");
    }

    @Test
    @DisplayName("userTask assignee = position:PM 原样保留（P2-19）")
    void testParsePositionAssignee() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"岗位审批\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"PM审批\" flowable:assignee=\"position:PM\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // P2-19: position: 前缀原样保留，由 SPI 展开为该岗位所有用户
        assertThat(t1.getPermissionFlag()).isEqualTo("position:PM");
    }

    @Test
    @DisplayName("userTask candidateUsers 含 leader:/position: 混合（P2-19）")
    void testParseCandidateUsersWithLeaderPosition() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"混合找人\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"会签\"\n" +
                "              flowable:candidateUsers=\"leader:1001,position:PM,user:2001\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // 三种前缀均原样保留，由 expandAssignees 分别展开
        assertThat(t1.getPermissionFlag())
                .isEqualTo("leader:1001,position:PM,user:2001");
    }

    @Test
    @DisplayName("P2-38: userTask assignee = self_select:approvers 原样保留")
    void testParseSelfSelectAssignee() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"发起人自选\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"自选审批人\" flowable:assignee=\"self_select:approvers\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // P2-38: self_select: 前缀原样保留到 permissionFlag
        assertThat(t1.getPermissionFlag()).isEqualTo("self_select:approvers");
    }

    @Test
    @DisplayName("P2-39: userTask assignee = multi_leader:3 原样保留")
    void testParseMultiLeaderAssignee() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\"\n" +
                "             xmlns:flowable=\"http://flowable.org/bpmn\">\n" +
                "  <process id=\"p1\" name=\"多级上级\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\" name=\"连续多级主管\" flowable:assignee=\"multi_leader:3\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        FlowNodeDO t1 = findNode(model.getNodes(), "t1");
        // P2-39: multi_leader: 前缀原样保留到 permissionFlag（3 表示连续 3 级上级）
        assertThat(t1.getPermissionFlag()).isEqualTo("multi_leader:3");
    }

    @Test
    @DisplayName("节点 ID 重复应抛 BAD_REQUEST")
    void testDuplicateNodeId() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"p1\">\n" +
                "    <startEvent id=\"dup\"/>\n" +
                "    <userTask id=\"dup\" name=\"重复\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"dup\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("唯一");
    }

    @Test
    @DisplayName("服务任务被映射为审批节点（APPROVAL）")
    void testParseServiceTask() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"p1\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <serviceTask id=\"svc1\" name=\"调用服务\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"svc1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"svc1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        assertThat(findNode(model.getNodes(), "svc1").getNodeType())
                .isEqualTo(FlowNodeType.APPROVAL.getCode());
    }

    @Test
    @DisplayName("包容网关被映射为 INCLUSIVE")
    void testParseInclusiveGateway() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"p1\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <inclusiveGateway id=\"inc1\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f1\" sourceRef=\"s1\" targetRef=\"inc1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"inc1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";

        BpmnModel model = parser.parse(xml);
        assertThat(findNode(model.getNodes(), "inc1").getNodeType())
                .isEqualTo(FlowNodeType.INCLUSIVE.getCode());
    }

    @Test
    @DisplayName("XXE 攻击应被拦截（外部实体禁用）")
    void testXxeProtection() {
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<!DOCTYPE definitions [\n" +
                "  <!ENTITY xxe SYSTEM \"file:///etc/passwd\">\n" +
                "]>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"p1\">&xxe;</process>\n" +
                "</definitions>";
        // JDK 解析器会抛错（DOCTYPE 被禁用），业务上应转 BizException
        assertThatThrownBy(() -> parser.parse(xml))
                .isInstanceOf(BizException.class);
    }

    private FlowNodeDO findNode(List<FlowNodeDO> nodes, String code) {
        return nodes.stream()
                .filter(n -> code.equals(n.getNodeCode()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("节点未找到: " + code));
    }
}
