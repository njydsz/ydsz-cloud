package com.njydsz.pmis.workflow.service.impl;

import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    // ============== P2-21: 流程定义详情查询 ==============

    @Test
    @DisplayName("getDetail 定义不存在应返回 null")
    void testGetDetailNotFound() {
        when(definitionMapper.selectById(99L)).thenReturn(null);
        assertThat(service.getDetail(99L)).isNull();
        verify(nodeMapper, never()).selectByDefinitionId(any());
        verify(skipMapper, never()).selectByDefinitionId(any());
    }

    @Test
    @DisplayName("getDetail 应组装 definition + nodes + skips")
    void testGetDetail() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(1L);
        def.setFlowCode("f1");
        def.setFlowName("F1");
        when(definitionMapper.selectById(1L)).thenReturn(def);

        FlowNodeDO n1 = new FlowNodeDO();
        n1.setId(10L);
        n1.setNodeCode("s1");
        n1.setNodeName("开始");
        FlowNodeDO n2 = new FlowNodeDO();
        n2.setId(11L);
        n2.setNodeCode("t1");
        n2.setNodeName("审批");
        when(nodeMapper.selectByDefinitionId(1L)).thenReturn(List.of(n1, n2));

        FlowSkipDO sk1 = new FlowSkipDO();
        sk1.setId(20L);
        sk1.setNextNodeCode("t1");
        when(skipMapper.selectByDefinitionId(1L)).thenReturn(List.of(sk1));

        Map<String, Object> result = service.getDetail(1L);
        assertThat(result).isNotNull();
        assertThat(result.get("definition")).isSameAs(def);
        @SuppressWarnings("unchecked")
        List<FlowNodeDO> nodes = (List<FlowNodeDO>) result.get("nodes");
        assertThat(nodes).hasSize(2);
        assertThat(nodes.get(0).getNodeCode()).isEqualTo("s1");
        assertThat(nodes.get(1).getNodeCode()).isEqualTo("t1");
        @SuppressWarnings("unchecked")
        List<FlowSkipDO> skips = (List<FlowSkipDO>) result.get("skips");
        assertThat(skips).hasSize(1);
        assertThat(skips.get(0).getNextNodeCode()).isEqualTo("t1");

        verify(definitionMapper).selectById(1L);
        verify(nodeMapper).selectByDefinitionId(1L);
        verify(skipMapper).selectByDefinitionId(1L);
    }

    // ============== P2-27: 流程定义版本切换 ==============

    @Test
    @DisplayName("switchActiveVersion P2-27: 失效其他版本 + 激活目标版本")
    void testSwitchActiveVersion() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setFlowCode("f1");
        when(definitionMapper.selectById(10L)).thenReturn(def);

        service.switchActiveVersion("f1", 10L, 1L);

        // 失效同 flowCode 的其他已发布版本
        verify(definitionMapper).deactivateByFlowCode("f1", 10L, 1L);
        // 激活目标版本
        verify(definitionMapper).publish(10L, 1);
    }

    @Test
    @DisplayName("switchActiveVersion P2-27: 定义不存在抛 NOT_FOUND")
    void testSwitchActiveVersionNotFound() {
        when(definitionMapper.selectById(99L)).thenReturn(null);
        assertThatThrownBy(() -> service.switchActiveVersion("f1", 99L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
        verify(definitionMapper, never()).deactivateByFlowCode(any(), any(), any());
    }

    @Test
    @DisplayName("switchActiveVersion P2-27: flowCode 不匹配抛 BAD_REQUEST")
    void testSwitchActiveVersionCodeMismatch() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setFlowCode("f2");
        when(definitionMapper.selectById(10L)).thenReturn(def);
        assertThatThrownBy(() -> service.switchActiveVersion("f1", 10L, 1L))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不匹配");
        verify(definitionMapper, never()).deactivateByFlowCode(any(), any(), any());
    }

    // ============== P2-28: 流程定义启用/停用 ==============

    @Test
    @DisplayName("enable P2-28: 设置 activityStatus=1")
    void testEnable() {
        service.enable(10L);
        verify(definitionMapper).updateActivityStatus(10L, 1);
    }

    @Test
    @DisplayName("disable P2-28: 设置 activityStatus=0")
    void testDisable() {
        service.disable(10L);
        verify(definitionMapper).updateActivityStatus(10L, 0);
    }

    // ============== P2-40: 节点坐标更新 ==============

    @Test
    @DisplayName("updateNodeCoordinate P2-40: 成功更新节点坐标")
    void testUpdateNodeCoordinate() {
        FlowNodeDO node = new FlowNodeDO();
        node.setId(100L);
        node.setDefinitionId(10L);
        node.setNodeCode("t1");
        node.setNodeName("审批");
        when(nodeMapper.selectByCode(10L, "t1")).thenReturn(node);

        String coordinate = "{\"x\":100,\"y\":200}";
        service.updateNodeCoordinate(10L, "t1", coordinate);

        ArgumentCaptor<FlowNodeDO> captor = ArgumentCaptor.forClass(FlowNodeDO.class);
        verify(nodeMapper).updateById(captor.capture());
        FlowNodeDO updated = captor.getValue();
        assertThat(updated.getCoordinate()).isEqualTo(coordinate);
    }

    @Test
    @DisplayName("updateNodeCoordinate P2-40: 节点不存在抛 NOT_FOUND")
    void testUpdateNodeCoordinate_NodeNotFound() {
        when(nodeMapper.selectByCode(10L, "unknown")).thenReturn(null);
        assertThatThrownBy(() -> service.updateNodeCoordinate(10L, "unknown", "{}"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("节点不存在");
        verify(nodeMapper, never()).updateById(any(FlowNodeDO.class));
    }

    // ============== P2-41: 流程定义草稿编辑 ==============

    @Test
    @DisplayName("updateDefinition P2-41: 成功编辑未发布定义的元数据 + 节点/跳转")
    void testUpdateDefinition_Success() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setFlowCode("f1");
        def.setFlowName("旧名称");
        def.setVersion("1.0");
        def.setIsPublish(0);  // 未发布
        def.setTenantId(1L);
        when(definitionMapper.selectById(10L)).thenReturn(def);

        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowName("新名称");
        dto.setCategory("新分类");
        dto.setDescription("新描述");
        dto.setFormPath("/form/new");
        // 包含节点和跳转
        FlowDeployProcessDTO.FlowNodeDTO n1 = new FlowDeployProcessDTO.FlowNodeDTO();
        n1.setNodeCode("s1");
        n1.setNodeName("开始");
        n1.setNodeType(0);
        FlowDeployProcessDTO.FlowNodeDTO n2 = new FlowDeployProcessDTO.FlowNodeDTO();
        n2.setNodeCode("t1");
        n2.setNodeName("审批");
        n2.setNodeType(1);
        dto.setNodes(List.of(n1, n2));
        FlowDeployProcessDTO.FlowSkipDTO sk = new FlowDeployProcessDTO.FlowSkipDTO();
        sk.setFromNodeCode("s1");
        sk.setToNodeCode("t1");
        dto.setSkips(List.of(sk));

        service.updateDefinition(10L, dto);

        // 1. 元数据更新
        ArgumentCaptor<FlowDefinitionDO> defCaptor = ArgumentCaptor.forClass(FlowDefinitionDO.class);
        verify(definitionMapper).updateById(defCaptor.capture());
        FlowDefinitionDO updatedDef = defCaptor.getValue();
        assertThat(updatedDef.getFlowName()).isEqualTo("新名称");
        assertThat(updatedDef.getCategory()).isEqualTo("新分类");
        assertThat(updatedDef.getDescription()).isEqualTo("新描述");
        assertThat(updatedDef.getFormPath()).isEqualTo("/form/new");
        // version 和 flowCode 不变
        assertThat(updatedDef.getFlowCode()).isEqualTo("f1");
        assertThat(updatedDef.getVersion()).isEqualTo("1.0");
        // 2. 删除旧节点/跳转
        verify(skipMapper).deleteByDefinitionId(10L);
        verify(nodeMapper).deleteByDefinitionId(10L);
        // 3. 插入新节点/跳转
        verify(nodeMapper, times(2)).insert(any(FlowNodeDO.class));
        verify(skipMapper, times(1)).insert(any(FlowSkipDO.class));
    }

    @Test
    @DisplayName("updateDefinition P2-41: 已发布定义不可编辑抛 BAD_REQUEST")
    void testUpdateDefinition_AlreadyPublished() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setIsPublish(1);  // 已发布
        when(definitionMapper.selectById(10L)).thenReturn(def);

        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowName("新名称");
        assertThatThrownBy(() -> service.updateDefinition(10L, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可编辑");
        // 不应该更新任何内容
        verify(definitionMapper, never()).updateById(any(FlowDefinitionDO.class));
        verify(nodeMapper, never()).deleteByDefinitionId(anyLong());
        verify(skipMapper, never()).deleteByDefinitionId(anyLong());
    }

    @Test
    @DisplayName("updateDefinition P2-41: 定义不存在抛 NOT_FOUND")
    void testUpdateDefinition_NotFound() {
        when(definitionMapper.selectById(99L)).thenReturn(null);
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowName("新名称");
        assertThatThrownBy(() -> service.updateDefinition(99L, dto))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不存在");
    }

    @Test
    @DisplayName("updateDefinition P2-41: 仅更新元数据（无 nodes/skips 时不删除旧节点）")
    void testUpdateDefinition_MetadataOnly() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setFlowCode("f1");
        def.setFlowName("旧名称");
        def.setIsPublish(0);
        def.setTenantId(1L);
        when(definitionMapper.selectById(10L)).thenReturn(def);

        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowName("仅改名");
        // 不设置 nodes/skips

        service.updateDefinition(10L, dto);

        verify(definitionMapper).updateById(any(FlowDefinitionDO.class));
        // 没有节点/跳转时不删除
        verify(nodeMapper, never()).deleteByDefinitionId(anyLong());
        verify(skipMapper, never()).deleteByDefinitionId(anyLong());
    }

    // ============== GAP-V2-06: 导入/导出 ==============

    @Test
    @DisplayName("exportDefinition GAP-V2-06: 导出流程定义为 JSON 字符串")
    void testExportDefinition() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(1L);
        def.setFlowCode("f1");
        def.setFlowName("F1");
        when(definitionMapper.selectById(1L)).thenReturn(def);

        FlowNodeDO n1 = new FlowNodeDO();
        n1.setNodeCode("s1");
        n1.setNodeName("开始");
        when(nodeMapper.selectByDefinitionId(1L)).thenReturn(List.of(n1));

        FlowSkipDO sk1 = new FlowSkipDO();
        sk1.setId(20L);
        sk1.setNextNodeCode("t1");
        sk1.setSkipName("通过");
        when(skipMapper.selectByDefinitionId(1L)).thenReturn(List.of(sk1));

        String json = service.exportDefinition(1L);
        assertThat(json).isNotNull();
        assertThat(json).contains("definition");
        assertThat(json).contains("nodes");
        assertThat(json).contains("skips");
        assertThat(json).contains("f1");
        assertThat(json).contains("s1");
    }

    @Test
    @DisplayName("importDefinition GAP-V2-06: 解析 JSON 并调用 deploy 创建草稿")
    void testImportDefinition() {
        FlowDefinitionServiceImpl spy = org.mockito.Mockito.spy(service);
        // 桩掉 deploy，避免重复部署检查等副作用
        org.mockito.Mockito.doReturn(42L).when(spy).deploy(any(FlowDeployProcessDTO.class));

        String json = "{\"definition\":{\"flowCode\":\"f1\",\"flowName\":\"F1\",\"version\":\"1.0\"},"
                + "\"nodes\":[{\"nodeCode\":\"s1\",\"nodeName\":\"开始\",\"nodeType\":0}],"
                + "\"skips\":[{\"skipName\":\"pass\",\"skipType\":\"PASS\","
                + "\"nextNodeCode\":\"s1\",\"ext\":\"{\\\"sourceRef\\\":\\\"s1\\\"}\"}]}";

        Long result = spy.importDefinition(json, 1L);
        assertThat(result).isEqualTo(42L);

        ArgumentCaptor<FlowDeployProcessDTO> dtoCaptor =
                ArgumentCaptor.forClass(FlowDeployProcessDTO.class);
        verify(spy).deploy(dtoCaptor.capture());
        FlowDeployProcessDTO deployedDto = dtoCaptor.getValue();
        assertThat(deployedDto.getFlowCode()).isEqualTo("f1");
        assertThat(deployedDto.getFlowName()).isEqualTo("F1");
        assertThat(deployedDto.getVersion()).isEqualTo("1.0");
        assertThat(deployedDto.getTenantId()).isEqualTo(1L);
        assertThat(deployedDto.getNodes()).hasSize(1);
        assertThat(deployedDto.getSkips()).hasSize(1);
    }

    // ============== GAP-V2-01: 设计器数据 API ==============

    @Test
    @DisplayName("getDesignerData GAP-V2-01: 返回设计器数据含 edges 数组")
    void testGetDesignerData() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(1L);
        def.setFlowCode("f1");
        when(definitionMapper.selectById(1L)).thenReturn(def);

        FlowNodeDO n1 = new FlowNodeDO();
        n1.setNodeCode("s1");
        n1.setNodeName("开始");
        when(nodeMapper.selectByDefinitionId(1L)).thenReturn(List.of(n1));

        FlowSkipDO sk1 = new FlowSkipDO();
        sk1.setId(20L);
        sk1.setNextNodeCode("t1");
        sk1.setSkipName("通过");
        sk1.setExt("{\"sourceRef\":\"s1\"}");
        when(skipMapper.selectByDefinitionId(1L)).thenReturn(List.of(sk1));

        Map<String, Object> result = service.getDesignerData(1L);
        assertThat(result).isNotNull();
        assertThat(result).containsKey("edges");
        assertThat(result).containsKey("definition");
        assertThat(result).containsKey("nodes");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> edges = (List<Map<String, Object>>) result.get("edges");
        assertThat(edges).hasSize(1);
        assertThat(edges.get(0).get("source")).isEqualTo("s1");
        assertThat(edges.get(0).get("target")).isEqualTo("t1");
        assertThat(edges.get(0).get("label")).isEqualTo("通过");
    }

    @Test
    @DisplayName("saveDesignerData GAP-V2-01: 未发布定义可保存节点坐标/属性")
    void testSaveDesignerData() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setIsPublish(0);  // 未发布
        when(definitionMapper.selectById(10L)).thenReturn(def);

        FlowNodeDO node = new FlowNodeDO();
        node.setId(100L);
        node.setNodeCode("t1");
        node.setNodeName("审批");
        when(nodeMapper.selectByCode(10L, "t1")).thenReturn(node);

        Map<String, Object> designerData = new HashMap<>();
        Map<String, Object> nodeData = new HashMap<>();
        nodeData.put("nodeCode", "t1");
        nodeData.put("coordinate", "{\"x\":100,\"y\":200}");
        nodeData.put("nodeName", "审批修改");
        designerData.put("nodes", List.of(nodeData));

        service.saveDesignerData(10L, designerData);

        // coordinate + nodeName 各触发一次 updateById
        verify(nodeMapper, times(2)).updateById(any(FlowNodeDO.class));
    }

    @Test
    @DisplayName("saveDesignerData GAP-V2-01: 已发布定义保存抛 BAD_REQUEST")
    void testSaveDesignerDataPublishedThrows() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(10L);
        def.setIsPublish(1);  // 已发布
        when(definitionMapper.selectById(10L)).thenReturn(def);

        assertThatThrownBy(() -> service.saveDesignerData(10L, Map.of()))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不可编辑");
        verify(nodeMapper, never()).updateById(any(FlowNodeDO.class));
    }

    // ============== GAP-V2-02: 表单字段配置 ==============

    @Test
    @DisplayName("getFormConfig GAP-V2-02: 返回节点表单字段配置")
    void testGetFormConfig() {
        FlowNodeDO node = new FlowNodeDO();
        node.setId(100L);
        node.setNodeCode("t1");
        String config = "[{\"field\":\"amount\",\"label\":\"金额\"}]";
        node.setFormFieldsConfig(config);
        when(nodeMapper.selectByCode(10L, "t1")).thenReturn(node);

        String result = service.getFormConfig(10L, "t1");
        assertThat(result).isEqualTo(config);
    }

    @Test
    @DisplayName("getFormConfig GAP-V2-02: 节点不存在抛 NOT_FOUND")
    void testGetFormConfigNotFound() {
        when(nodeMapper.selectByCode(10L, "unknown")).thenReturn(null);
        assertThatThrownBy(() -> service.getFormConfig(10L, "unknown"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("节点不存在");
    }

    @Test
    @DisplayName("saveFormConfig GAP-V2-02: 保存节点表单字段配置")
    void testSaveFormConfig() {
        FlowNodeDO node = new FlowNodeDO();
        node.setId(100L);
        node.setNodeCode("t1");
        when(nodeMapper.selectByCode(10L, "t1")).thenReturn(node);

        String config = "[{\"field\":\"amount\",\"label\":\"金额\"}]";
        service.saveFormConfig(10L, "t1", config);

        ArgumentCaptor<FlowNodeDO> captor = ArgumentCaptor.forClass(FlowNodeDO.class);
        verify(nodeMapper).updateById(captor.capture());
        assertThat(captor.getValue().getFormFieldsConfig()).isEqualTo(config);
    }
}
