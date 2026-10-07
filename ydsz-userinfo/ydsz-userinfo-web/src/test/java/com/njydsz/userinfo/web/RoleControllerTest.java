package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.dto.AssignPermissionsDTO;
import com.njydsz.userinfo.domain.dto.RoleDTO;
import com.njydsz.userinfo.domain.query.RolePageQuery;
import com.njydsz.userinfo.domain.vo.RoleVO;
import com.njydsz.userinfo.server.service.RoleService;

/**
 * {@link RoleController} 契约测试。
 *
 * <p>覆盖：分页检索（游标模式成功路径）、创建参数校验 400、权限分配成功。
 */
@WebMvcTest(controllers = RoleController.class)
class RoleControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private RoleService service;

  @Nested
  @DisplayName("GET /role/page")
  class Page {

    /** 游标模式分页返回 total=0 + empty nextCursor → 200 OK。 */
    @Test
    @DisplayName("should return 200 with empty page when no data")
    void emptyPage() throws Exception {
      when(service.page(any(RolePageQuery.class))).thenReturn(PageResponse.ofList(List.of(), null));
      mvc.perform(get("/role/page").param("pageNum", "1").param("pageSize", "10"))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /role")
  class Create {

    /** roleCode 空白 → HTTP 400。 */
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

    /** roleName 空白 → HTTP 400。 */
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

    /** 创建成功 → 200 包装新 roleId。 */
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

    /** permissionIds 数组长度超出 {@code @Size(max=200)} → HTTP 400。 */
    @Test
    @DisplayName("should return 400 when permissionIds exceed max size")
    void exceedsMaxSize() throws Exception {
      AssignPermissionsDTO dto = new AssignPermissionsDTO();
      dto.setPermissionIds(
          java.util.stream.IntStream.range(0, 201).mapToObj(i -> "m_" + i).toList());
      mvc.perform(
              post("/role/{roleId}/permissions", "r_001")
                  .contentType(MediaType.APPLICATION_JSON)
                  .content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** 正常调用 → 200 包装 true。 */
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
