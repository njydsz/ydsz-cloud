package com.njydsz.pmis.workflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.common.api.BizErrorCode;
import com.njydsz.pmis.common.exception.BizException;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.engine.BpmnModel;
import com.njydsz.pmis.workflow.engine.BpmnXmlParser;
import com.njydsz.pmis.workflow.engine.FlowDefinitionCacheService;
import com.njydsz.pmis.workflow.engine.FlowGraphValidator;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.entity.FlowNodeDO;
import com.njydsz.pmis.workflow.entity.FlowSkipDO;
import com.njydsz.pmis.workflow.enums.FlowNodeType;
import com.njydsz.pmis.workflow.mapper.FlowDefinitionMapper;
import com.njydsz.pmis.workflow.mapper.FlowNodeMapper;
import com.njydsz.pmis.workflow.mapper.FlowSkipMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * FlowDefinitionServiceImpl 单元测试
 *
 * <p>覆盖流程定义管理的核心方法：部署、发布、停用、查询、分页、详情、版本切换、
 * 启用/停用、导入/导出、表单配置、SLA配置、版本历史、差异对比。
 *
 * @author ydsz-pmis-team
 * @since 1.5.0
 */
@ExtendWith(MockitoExtension.class)
class FlowDefinitionServiceImplTest {

    @Mock private FlowDefinitionMapper definitionMapper;
    @Mock private FlowNodeMapper nodeMapper;
    @Mock private FlowSkipMapper skipMapper;
    @Mock private BpmnXmlParser bpmnXmlParser;
    @Mock private FlowGraphValidator graphValidator;
    @Mock private FlowDefinitionCacheService flowDefinitionCacheService;

    private FlowDefinitionServiceImpl service;

    private static final Long DEFINITION_ID = 200L;
    private static final String FLOW_CODE = "leave_approval";

    @BeforeEach
    void setUp() {
        service = new FlowDefinitionServiceImpl(
                definitionMapper, nodeMapper, skipMapper,
                bpmnXmlParser, graphValidator, flowDefinitionCacheService);
    }

    // ============ 部署 ============

    @Test
    @DisplayName("部署 JSON 模式流程定义 - 成功")
    void deployJsonModeShouldSucceed() {
        FlowDeployProcessDTO dto = buildJsonDeployDTO();
        when(definitionMapper.selectPublished(anyString(), anyString(), anyLong())).thenReturn(null);
        when(definitionMapper.insert(any(FlowDefinitionDO.class))).thenAnswer(inv -> {
            FlowDefinitionDO def = inv.getArgument(0);
            def.setId(DEFINITION_ID);
            return 1;
        });

        Long id = service.deploy(dto);

        assertThat(id).isEqualTo(DEFINITION_ID);
        verify(definitionMapper).insert(any(FlowDefinitionDO.class));
        verify(flowDefinitionCacheService).evict(DEFINITION_ID);
    }

    @Test
    @DisplayName("部署 BPMN 模式流程定义 - 成功")
    void deployBpmnModeShouldSucceed() {
        FlowDeployProcessDTO dto = buildBpmnDeployDTO();
        BpmnModel model = new BpmnModel();
        model.setProcessId("leave_approval");
        model.setProcessName("请假审批");
        model.setNodes(List.of());
        model.setSkips(List.of());
        model.setNodeCoordinates(Map.of());
        when(definitionMapper.selectPublished(anyString(), anyString(), anyLong())).thenReturn(null);
        when(bpmnXmlParser.parse("<bpmn>test</bpmn>")).thenReturn(model);
        when(definitionMapper.insert(any(FlowDefinitionDO.class))).thenAnswer(inv -> {
            FlowDefinitionDO def = inv.getArgument(0);
            def.setId(DEFINITION_ID);
            return 1;
        });

        Long id = service.deploy(dto);

        assertThat(id).isEqualTo(DEFINITION_ID);
        verify(bpmnXmlParser).parse("<bpmn>test</bpmn>");
        verify(flowDefinitionCacheService).evict(DEFINITION_ID);
    }

    @Test
    @DisplayName("部署 - flowCode 为空抛出异常")
    void deployWithEmptyFlowCodeShouldThrow() {
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("");

        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });
    }

    @Test
    @DisplayName("部署 - 重复定义抛出异常")
    void deployDuplicateShouldThrow() {
        FlowDeployProcessDTO dto = buildJsonDeployDTO();
        FlowDefinitionDO existing = new FlowDefinitionDO();
        when(definitionMapper.selectPublished(anyString(), anyString(), anyLong())).thenReturn(existing);

        assertThatThrownBy(() -> service.deploy(dto))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.DUPLICATE_KEY.getCode());
                });
    }

    // ============ 发布 / 停用 ============

    @Test
    @DisplayName("发布流程定义 - 成功")
    void publishShouldSucceed() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(def);

        service.publish(DEFINITION_ID);

        verify(definitionMapper).publish(DEFINITION_ID, 1);
    }

    @Test
    @DisplayName("发布不存在的流程定义 - 抛出异常")
    void publishNonExistentShouldThrow() {
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.publish(DEFINITION_ID))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    @Test
    @DisplayName("停用流程定义 - 成功")
    void deprecateShouldSucceed() {
        service.deprecate(DEFINITION_ID);

        verify(definitionMapper).publish(DEFINITION_ID, 9);
    }

    // ============ 查询 ============

    @Test
    @DisplayName("查询已发布流程 - 成功")
    void getPublishedShouldReturnDefinition() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectPublished(FLOW_CODE, "1.0", 1L)).thenReturn(def);

        FlowDefinitionDO result = service.getPublished(FLOW_CODE, "1.0", 1L);

        assertThat(result).isNotNull();
        assertThat(result.getFlowCode()).isEqualTo(FLOW_CODE);
    }

    @Test
    @DisplayName("查询已发布流程 - version为空默认1.0")
    void getPublishedShouldDefaultVersion() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectPublished(FLOW_CODE, "1.0", 1L)).thenReturn(def);

        FlowDefinitionDO result = service.getPublished(FLOW_CODE, null, 1L);

        assertThat(result).isNotNull();
        verify(definitionMapper).selectPublished(FLOW_CODE, "1.0", 1L);
    }

    @Test
    @DisplayName("查询最新版本流程 - 成功")
    void getLatestByCodeShouldReturnDefinition() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectLatestByCode(FLOW_CODE, 1L)).thenReturn(def);

        FlowDefinitionDO result = service.getLatestByCode(FLOW_CODE, 1L);

        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("分页查询流程定义 - 成功")
    void pageShouldReturnList() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectPage(any(Page.class), any(LambdaQueryWrapper.class)))
                .thenReturn(new Page<FlowDefinitionDO>().setRecords(List.of(def)));

        List<FlowDefinitionDO> result = service.page(1, 10, null, null);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getFlowCode()).isEqualTo(FLOW_CODE);
    }

    // ============ 详情 ============

    @Test
    @DisplayName("查询流程定义详情 - 成功")
    void getDetailShouldReturnMap() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(def);
        when(nodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of());
        when(skipMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of());

        Map<String, Object> detail = service.getDetail(DEFINITION_ID);

        assertThat(detail).isNotNull();
        assertThat(detail).containsKeys("definition", "nodes", "skips");
    }

    @Test
    @DisplayName("查询流程定义详情 - 不存在返回null")
    void getDetailShouldReturnNullWhenNotFound() {
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(null);

        Map<String, Object> detail = service.getDetail(DEFINITION_ID);

        assertThat(detail).isNull();
    }

    // ============ 启用 / 停用 ============

    @Test
    @DisplayName("启用流程定义 - 成功")
    void enableShouldSucceed() {
        service.enable(DEFINITION_ID);

        verify(definitionMapper).updateActivityStatus(DEFINITION_ID, 1);
    }

    @Test
    @DisplayName("停用流程定义 - 成功")
    void disableShouldSucceed() {
        service.disable(DEFINITION_ID);

        verify(definitionMapper).updateActivityStatus(DEFINITION_ID, 0);
    }

    // ============ 版本切换 ============

    @Test
    @DisplayName("切换版本 - flowCode为空抛出异常")
    void switchActiveVersionWithEmptyFlowCodeShouldThrow() {
        assertThatThrownBy(() -> service.switchActiveVersion("", DEFINITION_ID, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.BAD_REQUEST.getCode());
                });
    }

    @Test
    @DisplayName("切换版本 - 定义不存在抛出异常")
    void switchActiveVersionNonExistentShouldThrow() {
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.switchActiveVersion(FLOW_CODE, DEFINITION_ID, 1L))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    // ============ 导出/导入 ============

    @Test
    @DisplayName("导出流程定义 - 成功")
    void exportDefinitionShouldReturnJson() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(def);
        when(nodeMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of());
        when(skipMapper.selectByDefinitionId(DEFINITION_ID)).thenReturn(List.of());

        String json = service.exportDefinition(DEFINITION_ID);

        assertThat(json).isNotNull();
        assertThat(json).contains("definition");
    }

    @Test
    @DisplayName("导出流程定义 - 不存在抛出异常")
    void exportDefinitionNonExistentShouldThrow() {
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.exportDefinition(DEFINITION_ID))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    // ============ 表单配置 ============

    @Test
    @DisplayName("获取表单配置 - 成功")
    void getFormConfigShouldReturnConfig() {
        FlowNodeDO node = new FlowNodeDO();
        node.setFormFieldsConfig("{\"fields\":[]}");
        when(nodeMapper.selectByCode(DEFINITION_ID, "approval_1")).thenReturn(node);

        String config = service.getFormConfig(DEFINITION_ID, "approval_1");

        assertThat(config).isEqualTo("{\"fields\":[]}");
    }

    @Test
    @DisplayName("获取表单配置 - 节点不存在抛出异常")
    void getFormConfigNonExistentShouldThrow() {
        when(nodeMapper.selectByCode(DEFINITION_ID, "bad_node")).thenReturn(null);

        assertThatThrownBy(() -> service.getFormConfig(DEFINITION_ID, "bad_node"))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    @Test
    @DisplayName("保存表单配置 - 成功")
    void saveFormConfigShouldSucceed() {
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("approval_1");
        when(nodeMapper.selectByCode(DEFINITION_ID, "approval_1")).thenReturn(node);

        service.saveFormConfig(DEFINITION_ID, "approval_1", "{\"fields\":[{\"name\":\"reason\"}]}");

        verify(nodeMapper).updateById(node);
        assertThat(node.getFormFieldsConfig()).isEqualTo("{\"fields\":[{\"name\":\"reason\"}]}");
    }

    // ============ SLA 配置 ============

    @Test
    @DisplayName("获取SLA配置 - 成功")
    void getSlaConfigShouldReturnConfig() {
        FlowNodeDO node = new FlowNodeDO();
        node.setSlaConfig("{\"timeoutHours\":24}");
        when(nodeMapper.selectByCode(DEFINITION_ID, "approval_1")).thenReturn(node);

        String config = service.getSlaConfig(DEFINITION_ID, "approval_1");

        assertThat(config).isEqualTo("{\"timeoutHours\":24}");
    }

    @Test
    @DisplayName("保存SLA配置 - 成功")
    void saveSlaConfigShouldSucceed() {
        FlowNodeDO node = new FlowNodeDO();
        node.setNodeCode("approval_1");
        when(nodeMapper.selectByCode(DEFINITION_ID, "approval_1")).thenReturn(node);

        service.saveSlaConfig(DEFINITION_ID, "approval_1", "{\"timeoutHours\":48}");

        verify(nodeMapper).updateById(node);
        assertThat(node.getSlaConfig()).isEqualTo("{\"timeoutHours\":48}");
    }

    // ============ 版本历史 ============

    @Test
    @DisplayName("查询版本历史 - 成功")
    void listVersionsShouldReturnList() {
        FlowDefinitionDO def = buildDefinition();
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(def);
        when(definitionMapper.selectByFlowCode(FLOW_CODE, 1L)).thenReturn(List.of(def));

        List<Map<String, Object>> versions = service.listVersions(DEFINITION_ID);

        assertThat(versions).hasSize(1);
        assertThat(versions.get(0)).containsKeys("id", "version", "flowName", "isPublish");
    }

    @Test
    @DisplayName("查询版本历史 - 定义不存在抛出异常")
    void listVersionsNonExistentShouldThrow() {
        when(definitionMapper.selectById(DEFINITION_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.listVersions(DEFINITION_ID))
                .isInstanceOf(BizException.class)
                .satisfies(ex -> {
                    BizException biz = (BizException) ex;
                    assertThat(biz.getCode()).isEqualTo(BizErrorCode.NOT_FOUND.getCode());
                });
    }

    // ============ 辅助方法 ============

    private FlowDefinitionDO buildDefinition() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(DEFINITION_ID);
        def.setFlowCode(FLOW_CODE);
        def.setFlowName("请假审批");
        def.setCategory("HR");
        def.setVersion("1.0");
        def.setIsPublish(1);
        def.setActivityStatus(1);
        def.setTenantId(1L);
        return def;
    }

    private FlowDeployProcessDTO buildJsonDeployDTO() {
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode(FLOW_CODE);
        dto.setFlowName("请假审批");
        dto.setVersion("1.0");
        dto.setCategory("HR");
        dto.setTenantId(1L);

        FlowDeployProcessDTO.FlowNodeDTO startNode = new FlowDeployProcessDTO.FlowNodeDTO();
        startNode.setNodeCode("start");
        startNode.setNodeName("开始");
        startNode.setNodeType(FlowNodeType.START.getCode());

        FlowDeployProcessDTO.FlowNodeDTO approvalNode = new FlowDeployProcessDTO.FlowNodeDTO();
        approvalNode.setNodeCode("approval_1");
        approvalNode.setNodeName("部门审批");
        approvalNode.setNodeType(FlowNodeType.APPROVAL.getCode());
        approvalNode.setPermissionFlag("dept_manager");

        FlowDeployProcessDTO.FlowNodeDTO endNode = new FlowDeployProcessDTO.FlowNodeDTO();
        endNode.setNodeCode("end");
        endNode.setNodeName("结束");
        endNode.setNodeType(FlowNodeType.END.getCode());

        dto.setNodes(List.of(startNode, approvalNode, endNode));

        FlowDeployProcessDTO.FlowSkipDTO skip = new FlowDeployProcessDTO.FlowSkipDTO();
        skip.setSkipName("通过");
        skip.setSkipType("PASS");
        skip.setFromNodeCode("start");
        skip.setToNodeCode("approval_1");
        dto.setSkips(List.of(skip));

        return dto;
    }

    private FlowDeployProcessDTO buildBpmnDeployDTO() {
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode("leave_approval");
        dto.setFlowName("请假审批");
        dto.setVersion("1.0");
        dto.setBpmnXml("<bpmn>test</bpmn>");
        dto.setTenantId(1L);
        return dto;
    }
}