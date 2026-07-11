package com.njydsz.pmis.project.controller.execution;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.project.dto.execution.WbsTaskCreateDTO;
import com.njydsz.pmis.project.dto.execution.WbsTaskStatusDTO;
import com.njydsz.pmis.project.entity.execution.WbsTaskDO;
import com.njydsz.pmis.project.service.execution.WbsTaskService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * {@link WbsTaskController} WebMvc 切片测试
 *
 * <p>验证 WBS 任务 CRUD + 状态流转接口的路由映射、入参校验与响应格式。
 *
 * @author ydsz-pmis-team
 * @since 1.4.0
 */
@WebMvcTest(WbsTaskController.class)
@DisplayName("WbsTaskController WebMvc 切片测试")
class WbsTaskControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private WbsTaskService service;

    private static final String BASE_URL = "/execution/wbs";

    @Nested
    @DisplayName("POST /execution/wbs - 创建 WBS 任务")
    class CreateWbsTask {

        @Test
        @DisplayName("合法参数返回 200 + 任务 ID")
        void shouldReturnIdWhenValidInput() throws Exception {
            var dto = new WbsTaskCreateDTO();
            dto.setTaskCode("WBS-001");
            dto.setTaskName("需求分析");
            dto.setInitiationId("P001");
            dto.setTaskType("TASK");
            dto.setPriority("NORMAL");
            when(service.create(any(WbsTaskCreateDTO.class))).thenReturn("WBS-001");

            mockMvc.perform(post(BASE_URL)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0))
                    .andExpect(jsonPath("$.data").value("WBS-001"));
        }
    }

    @Nested
    @DisplayName("PUT /execution/wbs/status - 变更任务状态")
    class ChangeStatus {

        @Test
        @DisplayName("合法状态变更返回成功")
        void shouldReturnOkWhenChangeStatus() throws Exception {
            var dto = new WbsTaskStatusDTO();
            dto.setId("WBS-001");
            dto.setTargetStatus("IN_PROGRESS");
            doNothing().when(service).changeStatus(any(WbsTaskStatusDTO.class));

            mockMvc.perform(put(BASE_URL + "/status")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(dto)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));

            verify(service).changeStatus(any(WbsTaskStatusDTO.class));
        }
    }

    @Nested
    @DisplayName("GET /execution/wbs/page - 分页查询")
    class PageWbsTask {

        @Test
        @DisplayName("默认参数分页返回结果")
        void shouldReturnPageWithDefaultParams() throws Exception {
            var page = new Page<WbsTaskDO>(1, 20);
            page.setTotal(0);
            when(service.page(eq(1), eq(20), isNull(), isNull(), isNull(), isNull(), isNull()))
                    .thenReturn(page);

            mockMvc.perform(get(BASE_URL + "/page"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }

        @Test
        @DisplayName("page=0 返回 400")
        void shouldReturn400WhenPageIsZero() throws Exception {
            mockMvc.perform(get(BASE_URL + "/page")
                            .param("page", "0"))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /execution/wbs/{id} - 查询详情")
    class GetWbsTask {

        @Test
        @DisplayName("存在时返回任务数据")
        void shouldReturnTaskWhenExists() throws Exception {
            var task = new WbsTaskDO();
            task.setId("WBS-001");
            task.setTaskName("需求分析");
            when(service.getById("WBS-001")).thenReturn(task);

            mockMvc.perform(get(BASE_URL + "/WBS-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.id").value("WBS-001"))
                    .andExpect(jsonPath("$.data.taskName").value("需求分析"));
        }
    }

    @Nested
    @DisplayName("GET /execution/wbs/progress/{initiationId} - 项目整体进度")
    class GetProjectProgress {

        @Test
        @DisplayName("返回项目进度百分比")
        void shouldReturnProgressData() throws Exception {
            when(service.calcOverallProgress("P001"))
                    .thenReturn(new BigDecimal("55.0"));

            mockMvc.perform(get(BASE_URL + "/progress/P001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }

    @Nested
    @DisplayName("DELETE /execution/wbs/{id} - 删除任务")
    class DeleteWbsTask {

        @Test
        @DisplayName("合法 ID 删除返回成功")
        void shouldReturnOkWhenDelete() throws Exception {
            doNothing().when(service).delete("WBS-001");

            mockMvc.perform(delete(BASE_URL + "/WBS-001"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(0));
        }
    }
}
