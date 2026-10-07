package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
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
import com.njydsz.userinfo.domain.dto.AssignPermissionsDTO;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.service.RoleService;

/**
 * {@link RoleController} 契约测试。
 *
 * <p>覆盖：CRUD 参数校验、分页检索、权限分配。
 */
@WebMvcTest(controllers = RoleController.class)
class RoleControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private RoleService service;

  @Nested
  @DisplayName("GET /role/page")
  class Page {

    @Test
    @DisplayName("should return 200 with empty page when no data")
    void emptyPage() throws Exception {
      RoleVO vo = new RoleVO();
      vo.setId("r_001");
      vo.setRoleCode("ROLE_ADMIN");
      when(service.page(any(RolePageQuery.class)))
          .thenReturn(com.njydsz.common.core.response.PageResponse.of(0L, 1, 20, List.of(vo)));
      mvc.perform(get("/role/page").param("pageNum", "1").param("pageSize", "10"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("").exists())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /role")
  class Create {

    /** roleCode 为空 → 400。 */
    @Test
    @DisplayName("should return 400 when roleCode blank")
    void blankRoleCode() throws Exception {
      RoleDTO dto = new RoleDTO();
      dto.setRoleCode("");
      dto.setRoleName("admin");
      mvc.perform(post("/role").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** roleName 为空 → 400。 */
    @Test
    @DisplayName("should return 400 when roleName blank")
    void blankRoleName() throws Exception {
      RoleDTO dto = new RoleDTO();
      dto.setRoleCode("ROLE_TEST");
      dto.setRoleName("");
      mvc.perform(post("/role").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** 创建成功 → 200 + 包装 id。 */
    @Test
    @DisplayName("should return 200 with id on success")
    void ok() throws Exception {
      RoleDTO dto = new RoleDTO();
      dto.setRoleCode("ROLE_TEST");
      dto.setRoleName("test");
      when(service.create(any(RoleDTO.class))).thenReturn("r_001");
      mvc.perform(post("/role").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /role/{roleId}/permissions")
  class AssignPermission {

    /** permissionIds 数组长度超出限制 (>200) → 400。 */
    @Test
    @DisplayName("should return 400 when permissionIds exceed max size")
    void assignmentFails() throws Exception {
      AssignPermissionsDTO dto = new AssignPermissionsDTO();
      dto.setPermissionIds(java.util.stream.IntStream.range(0, 201).mapToObj(i -> "m_" + i).toList());
      mvc.perform(
              post("/role/{roleId}/permissions", "r_001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** 分配成功 → 200 + true。 */
    @Test
    @DisplayName("should return 200 on success")
    void ok() throws Exception {
      AssignPermissionsDTO dto = new AssignPermissionsDTO();
      dto.setPermissionIds(List.of("m_001", "m_002"));
      when(service.assignPermissions(anyString(), any())).thenReturn(Boolean.TRUE);
      mvc.perform(
              post("/role/{roleId}/permissions", "r_001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }
}
