package com.njydsz.pmis.workflow.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.njydsz.pmis.common.api.Result;
import com.njydsz.pmis.workflow.dto.FlowDefinitionSimulateDTO;
import com.njydsz.pmis.workflow.dto.FlowDeployProcessDTO;
import com.njydsz.pmis.workflow.entity.FlowDefinitionDO;
import com.njydsz.pmis.workflow.service.FlowDefinitionService;
import com.njydsz.pmis.workflow.service.FlowInstanceService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * FlowDefinitionController MockMvc 集成测试
 *
 * <p>GAP-P1-8: 补强 Controller 层测试覆盖（从 0 到 1 建立样板）。
 * 使用 MockMvc standalone 模式，不加载 Spring Security/AOP，专注验证路由绑定、
 * 请求参数解析、JSON 序列化、Service 委托调用、响应封装。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@ExtendWith(MockitoExtension.class)
class FlowDefinitionControllerTest {

    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Mock private FlowDefinitionService definitionService;
    @Mock private FlowInstanceService instanceService;
    @InjectMocks private FlowDefinitionController controller;

    private static final Long DEF_ID = 200L;
    private static final String FLOW_CODE = "leave_approval";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(controller).build();
    }

    // ============ 部署 ============

    @Test
    @DisplayName("POST /definition/deploy - 成功部署返回定义 ID")
    void deployShouldReturnId() throws Exception {
        FlowDeployProcessDTO dto = new FlowDeployProcessDTO();
        dto.setFlowCode(FLOW_CODE);
        dto.setFlowName("请假审批");
        dto.setBpmnXml("<bpmn/>");

        when(definitionService.deploy(any(FlowDeployProcessDTO.class))).thenReturn(DEF_ID);

        mockMvc.perform(post("/api/v1/workflow/engine/definition/deploy")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(DEF_ID));

        verify(definitionService).deploy(any(FlowDeployProcessDTO.class));
    }

    // ============ 发布 / 废弃 ============

    @Test
    @DisplayName("POST /definition/{id}/publish - 发布流程定义")
    void publishShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/workflow/engine/definition/{id}/publish", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(definitionService).publish(DEF_ID);
    }

    @Test
    @DisplayName("POST /definition/{id}/deprecate - 废弃流程定义")
    void deprecateShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/workflow/engine/definition/{id}/deprecate", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(definitionService).deprecate(DEF_ID);
    }

    // ============ 查询 ============

    @Test
    @DisplayName("GET /definition/code/{code} - 按编码查询已发布流程定义")
    void getByCodeShouldReturnDefinition() throws Exception {
        FlowDefinitionDO def = buildDefinition();
        when(definitionService.getPublished(eq(FLOW_CODE), anyString(), anyLong())).thenReturn(def);

        mockMvc.perform(get("/api/v1/workflow/engine/definition/code/{code}", FLOW_CODE)
                        .param("version", "1.0")
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.flowCode").value(FLOW_CODE));

        verify(definitionService).getPublished(FLOW_CODE, "1.0", 1L);
    }

    @Test
    @DisplayName("GET /definition/page - 分页查询流程定义")
    void pageShouldReturnList() throws Exception {
        when(definitionService.page(anyInt(), anyInt(), anyString(), anyString()))
                .thenReturn(List.of(buildDefinition()));

        mockMvc.perform(get("/api/v1/workflow/engine/definition/page")
                        .param("pageNo", "1")
                        .param("pageSize", "20")
                        .param("category", "HR")
                        .param("flowCode", FLOW_CODE))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].flowCode").value(FLOW_CODE));

        verify(definitionService).page(1, 20, "HR", FLOW_CODE);
    }

    @Test
    @DisplayName("GET /definition/{id} - 查询流程定义详情")
    void getDefinitionDetailShouldReturnMap() throws Exception {
        when(definitionService.getDetail(DEF_ID)).thenReturn(Map.of("definition", buildDefinition()));

        mockMvc.perform(get("/api/v1/workflow/engine/definition/{id}", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.definition.flowCode").value(FLOW_CODE));

        verify(definitionService).getDetail(DEF_ID);
    }

    // ============ 版本切换 / 启用 / 停用 ============

    @Test
    @DisplayName("POST /definition/{code}/switchVersion - 切换激活版本")
    void switchVersionShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/workflow/engine/definition/{code}/switchVersion", FLOW_CODE)
                        .param("definitionId", "201")
                        .param("tenantId", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(definitionService).switchActiveVersion(FLOW_CODE, 201L, 1L);
    }

    @Test
    @DisplayName("POST /definition/{id}/enable - 启用流程定义")
    void enableShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/workflow/engine/definition/{id}/enable", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(definitionService).enable(DEF_ID);
    }

    @Test
    @DisplayName("POST /definition/{id}/disable - 停用流程定义")
    void disableShouldReturnOk() throws Exception {
        mockMvc.perform(post("/api/v1/workflow/engine/definition/{id}/disable", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        verify(definitionService).disable(DEF_ID);
    }

    // ============ 导入导出 ============

    @Test
    @DisplayName("GET /definition/{id}/export - 导出流程定义 JSON")
    void exportDefinitionShouldReturnJson() throws Exception {
        when(definitionService.exportDefinition(DEF_ID)).thenReturn("{\"flowCode\":\"leave\"}");

        mockMvc.perform(get("/api/v1/workflow/engine/definition/{id}/export", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isString());

        verify(definitionService).exportDefinition(DEF_ID);
    }

    @Test
    @DisplayName("POST /definition/import - 从 JSON 导入流程定义")
    void importDefinitionShouldReturnId() throws Exception {
        when(definitionService.importDefinition(anyString(), anyLong())).thenReturn(DEF_ID);

        mockMvc.perform(post("/api/v1/workflow/engine/definition/import")
                        .param("tenantId", "1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"flowCode\":\"leave\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").value(DEF_ID));

        verify(definitionService).importDefinition("{\"flowCode\":\"leave\"}", 1L);
    }

    // ============ 版本历史 / 差异对比 ============

    @Test
    @DisplayName("GET /definition/{id}/versions - 列出历史版本")
    void listVersionsShouldReturnList() throws Exception {
        when(definitionService.listVersions(DEF_ID))
                .thenReturn(List.of(Map.of("id", DEF_ID, "version", "1.0")));

        mockMvc.perform(get("/api/v1/workflow/engine/definition/{id}/versions", DEF_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(DEF_ID));

        verify(definitionService).listVersions(DEF_ID);
    }

    @Test
    @DisplayName("GET /definition/{id}/diff - 版本差异对比")
    void diffVersionsShouldReturnMap() throws Exception {
        when(definitionService.diffVersions(DEF_ID, 1, 2))
                .thenReturn(Map.of("version1", 1, "version2", 2));

        mockMvc.perform(get("/api/v1/workflow/engine/definition/{id}/diff", DEF_ID)
                        .param("v1", "1")
                        .param("v2", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.version1").value(1));

        verify(definitionService).diffVersions(DEF_ID, 1, 2);
    }

    // ============ 模拟运行 ============

    @Test
    @DisplayName("POST /definition/simulate - 流程模拟运行")
    void simulateShouldReturnPath() throws Exception {
        FlowDefinitionSimulateDTO dto = new FlowDefinitionSimulateDTO();
        dto.setFlowCode(FLOW_CODE);
        dto.setVersion(1);
        dto.setVariables(Map.of());

        when(instanceService.simulate(anyString(), anyString(), any(), anyLong()))
                .thenReturn(List.of(Map.of("node", "start")));

        mockMvc.perform(post("/api/v1/workflow/engine/definition/simulate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].node").value("start"));

        verify(instanceService).simulate(anyString(), anyString(), any(), anyLong());
    }

    // ============ 辅助方法 ============

    private FlowDefinitionDO buildDefinition() {
        FlowDefinitionDO def = new FlowDefinitionDO();
        def.setId(DEF_ID);
        def.setFlowCode(FLOW_CODE);
        def.setFlowName("请假审批");
        def.setCategory("HR");
        def.setFlowVersion("1.0");
        def.setIsPublish(1);
        def.setActivityStatus(1);
        def.setTenantId(1L);
        return def;
    }
}
