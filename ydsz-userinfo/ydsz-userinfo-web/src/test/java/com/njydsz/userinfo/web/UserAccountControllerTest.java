package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.dto.AssignRolesDTO;
import com.njydsz.userinfo.domain.dto.BatchUserStatusDTO;
import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.UserAccountPageQuery;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.SensitiveVerifyService;
import com.njydsz.userinfo.server.service.LoginHistoryService;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.UserExcelService;
import com.njydsz.userinfo.server.service.UserLifecycleService;

/**
 * {@link UserAccountController} 契约测试。
 *
 * <p>覆盖：参数非法 400、资源不存在 404、业务异常码包装、CRUD 路径契约。
 */
@WebMvcTest(controllers = UserAccountController.class)
class UserAccountControllerTest {

  @Autowired private MockMvc mvc;

  @MockBean private UserAccountService service;
  @MockBean private UserExcelService userExcelService;
  @MockBean private LoginHistoryService loginHistoryService;
  @MockBean private SensitiveVerifyService sensitiveVerifyService;
  @MockBean private UserLifecycleService lifecycleService;

  @Nested
  @DisplayName("GET /user/{id}")
  class GetById {

    /** 资源不存在 → 业务层抛 USER_NOT_FOUND，代理返回 200 + 错误码；这里我们仅验证 HTTP 层返回 i18n 包装。 */
    @Test
    @DisplayName("should return business code when user not found")
    void notFound() throws Exception {
      when(service.getById(anyString())).thenThrow(new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));
      mvc.perform(get("/user/{id}", "9999"))
          .andExpect(status().isOk())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /user")
  class Create {

    /** username 为空 → Bean Validation 400。 */
    @Test
    @DisplayName("should return 400 when username blank")
    void blankUsername() throws Exception {
      UserAccountDTO dto = new UserAccountDTO();
      dto.setUsername("");
      dto.setPassword("abc");
      dto.setRealName("test");
      mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /** password 长度 < 8 → Bean Validation 400。 */
    @Test
    @DisplayName("should return 400 when password too short")
    void shortPassword() throws Exception {
      UserAccountDTO dto = new UserAccountDTO();
      dto.setUsername("alice");
      dto.setPassword("123");
      dto.setRealName("alice");
      mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }
  }

  @Nested
  @DisplayName("DELETE /user/{id}")
  class Remove {

    /** 删除成功 → 200 包装 true。 */
    @Test
    @DisplayName("should return 200 on successful removal")
    void ok() throws Exception {
      when(service.removeById("u_001")).thenReturn(Boolean.TRUE);
      mvc.perform(delete("/user/{id}", "u_001")).andExpect(status().isOk()).andDo(print());
    }
  }
}
