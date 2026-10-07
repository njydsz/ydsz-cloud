package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.bean.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.dto.DepartmentDTO;
import com.njydsz.userinfo.domain.vo.DepartmentTreeVO;
import com.njydsz.userinfo.domain.vo.DepartmentVO;
import com.njydsz.userinfo.server.service.DepartmentService;

/**
 * {@link DepartmentController} 契约测试。
 *
 * <p>覆盖：创建参数校验、扁平列表、树形查询。
 */
@WebMvcTest(controllers = DepartmentController.class)
class DepartmentControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private DepartmentService service;

  @Nested
  @DisplayName("GET /dept/list")
  class List {

    @Test
    @DisplayName("should return 200 with empty list")
    void ok() throws Exception {
      when(service.list()).thenReturn(List.of());
      mvc.perform(get("/dept/list")).andExpect(status().isOk()).andDo(print());
    }
  }

  @Nested
  @DisplayName("GET /dept/tree")
  class Tree {

    @Test
    @DisplayName("should return 200 with tree list")
    void ok() throws Exception {
      DepartmentTreeVO node = new DepartmentTreeVO();
      node.setId("d_001");
      node.setDeptName("hq");
      when(service.tree()).thenReturn(List.of(node));
      mvc.perform(get("/dept/tree")).andExpect(status().isOk()).andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /dept")
  class Create {

    /** deptCode 缺失 → 400。 */
    @Test
    @DisplayName("should return 400 when deptCode missing")
    void blankDeptCode() throws Exception {
      DepartmentDTO dto = new DepartmentDTO();
      dto.setDeptCode("");
      dto.setDeptName("hq");
      mvc.perform(post("/dept").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** deptName 缺失 → 400。 */
    @Test
    @DisplayName("should return 400 when deptName blank")
    void blankDeptName() throws Exception {
      DepartmentDTO dto = new DepartmentDTO();
      dto.setDeptCode("DEPT_001");
      dto.setDeptName("");
      mvc.perform(post("/dept").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** 创建成功 → 200 + id。 */
    @Test
    @DisplayName("should return 200 with id on success")
    void ok() throws Exception {
      DepartmentDTO dto = new DepartmentDTO();
      dto.setDeptCode("DEPT_001");
      dto.setDeptName("hq");
      when(service.create(any(DepartmentDTO.class))).thenReturn("d_001");
      mvc.perform(post("/dept").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }
}
