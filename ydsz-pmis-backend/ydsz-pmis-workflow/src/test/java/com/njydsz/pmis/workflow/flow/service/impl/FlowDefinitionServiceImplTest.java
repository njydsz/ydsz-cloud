package com.njydsz.pmis.workflow.flow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.flow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.flow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.flow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.flow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.flow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.flow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.flow.mapper.FlowSkipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowDefinitionServiceImpl 单元测试
 *
 * <p>覆盖 deploy / publish / deprecate / getPublished / getLatestByCode / page 等核心逻辑。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@DisplayName("FlowDefinitionServiceImpl 单元测试")
class FlowDefinitionServiceImplTest {

    private FlowDefinitionMapper definitionMapper;
    private FlowNodeMapper nodeMapper;
    private FlowSkipMapper skipMapper;
    private BpmnXmlParser bpmnXmlParser;
    private FlowDefinitionServiceImpl service;

    @BeforeEach
    void setUp() {
        definitionMapper = mock(FlowDefinitionMapper.class);
        nodeMapper = mock(FlowNodeMapper.class);
        skipMapper = mock(FlowSkipMapper.class);
        bpmnXmlParser = new BpmnXmlParser();
        service = new FlowDefinitionServiceImpl(definitionMapper, nodeMapper, skipMapper, bpmnXmlParser);
    }

    @Test
    @DisplayName("deploy 缺 flowCode/flowName 抛 BAD_REQUEST")
    void testDeployMissingRequired() {
        assertThatThrownBy(() -> service.deploy(new FlowDeployProcessDTO()))
                .isInstanceOf(BizException.class);
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("deploy 重复 (code+version+tenant) 抛 DUPLICATE_KEY")
    void testDeployDuplicate() {
        FlowDefinitionDO existing = new FlowDefinitionDO();
        existing.setId(1L);
        when(definitionMapper.selectPublished(eq("f1"), eq("1.0"), anyLong())).thenReturn(existing);
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("已存在");
    }

    @Test
    @DisplayName("deploy 无 BPMN 也无 JSON 节点抛 BAD_REQUEST")
    void testDeployNoModel() {
        when(definitionMapper.selectPublished(any(), any(), anyLong())).thenReturn(null);
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("二选一");
    }

    @Test
    @DisplayName("deploy JSON 模式：成功部署 + 写库")
    void testDeployJson() {
        when(definitionMapper.selectPublished(any(), any(), anyLong())).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            FlowDefinitionDO arg = inv.getArgument(0);
            arg.setId(10L);
            return 1;
        }).when(definitionMapper).insert((FlowDefinitionDO) any());
        org.mockito.Mockito.doAnswer(inv -> {
            FlowNodeDO arg = inv.getArgument(0);
            arg.setId(System.nanoTime());
            return 1;
        }).when(nodeMapper).insert((FlowNodeDO) any());
        org.mockito.Mockito.doAnswer(inv -> {
            FlowSkipDO arg = inv.getArgument(0);
            arg.setId(System.nanoTime());
            return 1;
        }).when(skipMapper).insert((FlowSkipDO) any());

        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        FlowDeployProcessDTO.FlowNodeDTO n1 = new FlowDeployProcessDTO.FlowNodeDTO();
        n1.setNodeCode("s1");
        n1.setNodeName("开始");
        n1.setNodeType(0);
        FlowDeployProcessDTO.FlowNodeDTO n2 = new FlowDeployProcessDTO.FlowNodeDTO();
        n2.setNodeCode("t1");
        n2.setNodeName("审批");
        n2.setNodeType(1);
        FlowDeployProcessDTO.FlowNodeDTO n3 = new FlowDeployProcessDTO.FlowNodeDTO();
        n3.setNodeCode("e1");
        n3.setNodeName("结束");
        n3.setNodeType(6);
        dto.setNodes(List.of(n1, n2, n3));
        FlowDeployProcessDTO.FlowSkipDTO sk = new FlowDeployProcessDTO.FlowSkipDTO();
        sk.setFromNodeCode("s1");
        sk.setToNodeCode("t1");
        dto.setSkips(List.of(sk));

        Long defId = service.deploy(dto);
        assertThat(defId).isEqualTo(10L);
        verify(definitionMapper, times(1)).insert(any(FlowDefinitionDO.class));
        verify(nodeMapper, times(3)).insert(any(FlowNodeDO.class));
        verify(skipMapper, times(1)).insert(any(FlowSkipDO.class));
    }

    @Test
    @DisplayName("deploy JSON 模式：缺开始节点抛 BAD_REQUEST")
    void testDeployJsonNoStart() {
        when(definitionMapper.selectPublished(any(), any(), anyLong())).thenReturn(null);
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        FlowDeployProcessDTO.FlowNodeDTO n = new FlowDeployProcessDTO.FlowNodeDTO();
        n.setNodeCode("t1");
        n.setNodeType(1);
        dto.setNodes(List.of(n));
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("开始节点");
    }

    @Test
    @DisplayName("deploy JSON 模式：节点编码重复抛 BAD_REQUEST")
    void testDeployJsonDuplicateCode() {
        when(definitionMapper.selectPublished(any(), any(), anyLong())).thenReturn(null);
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        FlowDeployProcessDTO.FlowNodeDTO s = new FlowDeployProcessDTO.FlowNodeDTO();
        s.setNodeCode("dup");
        s.setNodeType(0);
        FlowDeployProcessDTO.FlowNodeDTO t = new FlowDeployProcessDTO.FlowNodeDTO();
        t.setNodeCode("dup");
        t.setNodeType(1);
        dto.setNodes(List.of(s, t));
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("唯一");
    }

    @Test
    @DisplayName("deploy BPMN 模式：成功解析并写库")
    void testDeployBpmn() {
        when(definitionMapper.selectPublished(any(), any(), anyLong())).thenReturn(null);
        org.mockito.Mockito.doAnswer(inv -> {
            FlowDefinitionDO arg = inv.getArgument(0);
            arg.setId(20L);
            return 1;
        }).when(definitionMapper).insert((FlowDefinitionDO) any());
        org.mockito.Mockito.doAnswer(inv -> {
            if (inv.getArgument(0) instanceof FlowNodeDO) {
                ((FlowNodeDO) inv.getArgument(0)).setId(System.nanoTime());
            } else if (inv.getArgument(0) instanceof FlowSkipDO) {
                ((FlowSkipDO) inv.getArgument(0)).setId(System.nanoTime());
            }
            return 1;
        }).when(nodeMapper).insert((FlowNodeDO) any());
        org.mockito.Mockito.doAnswer(inv -> {
            ((FlowSkipDO) inv.getArgument(0)).setId(System.nanoTime());
            return 1;
        }).when(skipMapper).insert((FlowSkipDO) any());

        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"f1\" name=\"F1\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <userTask id=\"t1\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f\" sourceRef=\"s1\" targetRef=\"t1\"/>\n" +
                "    <sequenceFlow id=\"f2\" sourceRef=\"t1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        dto.setBpmnXml(xml);

        Long defId = service.deploy(dto);
        assertThat(defId).isEqualTo(20L);
        ArgumentCaptor<FlowDefinitionDO> defCap = ArgumentCaptor.forClass(FlowDefinitionDO.class);
        verify(definitionMapper).insert(defCap.capture());
        assertThat(defCap.getValue().getFlowCode()).isEqualTo("f1");
        assertThat(defCap.getValue().getIsPublish()).isEqualTo(0);
        verify(nodeMapper, times(3)).insert(any(FlowNodeDO.class));
        verify(skipMapper, times(2)).insert(any(FlowSkipDO.class));
    }

    @Test
    @DisplayName("deploy BPMN 模式：flowCode 与 process id 不一致抛 BAD_REQUEST")
    void testDeployBpmnCodeMismatch() {
        when(definitionMapper.selectPublished(any(), any(), anyLong())).thenReturn(null);
        String xml = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" +
                "<definitions xmlns=\"http://www.omg.org/spec/BPMN/20100524/MODEL\">\n" +
                "  <process id=\"other_code\" name=\"X\">\n" +
                "    <startEvent id=\"s1\"/>\n" +
                "    <endEvent id=\"e1\"/>\n" +
                "    <sequenceFlow id=\"f\" sourceRef=\"s1\" targetRef=\"e1\"/>\n" +
                "  </process>\n" +
                "</definitions>";
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("f1");
        dto.setFlowName("F1");
        dto.setBpmnXml(xml);
        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不一致");
    }

    @Test
    @DisplayName("publish 找不到定义抛 NOT_FOUND")
    void testPublishNotFound() {
        when(definitionMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.publish(99L)).isInstanceOf(BizException.class);
    }

    @Test
    @DisplayName("publish 成功更新发布状态")
    void testPublish() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(1L);
        when(definitionMapper.selectById(1L)).thenReturn(def);
        service.publish(1L);
        verify(definitionMapper).publish(1L, 1);
    }

    @Test
    @DisplayName("deprecate 更新停用状态")
    void testDeprecate() {
        service.deprecate(1L);
        verify(definitionMapper).publish(1L, 9);
    }

    @Test
    @DisplayName("getPublished 委托给 mapper（空 version 默认 1.0）")
    void testGetPublished() {
        when(definitionMapper.selectPublished(eq("f1"), eq("2.0"), anyLong())).thenReturn(new FlowDefinitionDO());
        service.getPublished("f1", "2.0", 1L);
        verify(definitionMapper).selectPublished("f1", "2.0", 1L);
        // 空 version 时应默认 1.0
        service.getPublished("f1", null, 1L);
        verify(definitionMapper).selectPublished("f1", "1.0", 1L);
    }

    @Test
    @DisplayName("getLatestByCode 委托给 mapper")
    void testGetLatestByCode() {
        service.getLatestByCode("f1", 1L);
        verify(definitionMapper).selectLatestByCode("f1", 1L);
    }
}
