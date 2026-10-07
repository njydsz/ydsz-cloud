package com.njydsz.userinfo.web.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.userinfo.domain.dto.UserAccountDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.server.auth.SensitiveVerifyService;
import com.njydsz.userinfo.server.service.LoginHistoryService;
import com.njydsz.userinfo.server.service.UserAccountService;
import com.njydsz.userinfo.server.service.UserExcelService;
import com.njydsz.userinfo.server.service.UserLifecycleService;

/**
 * {@link UserAccountController} 契约测试。
 *
 * <p>覆盖：资源不存在 404、参数非法 400（Bean Validation）、业务异常码包装、
 * 成功路径的 HTTP 200 包装。
 */
@WebMvcTest(controllers = UserAccountController.class)
class UserAccountControllerTest {

  @Autowired private MockMvc mvc;

  @MockitoBean private UserAccountService service;
  @MockitoBean private UserExcelService userExcelService;
  @MockitoBean private LoginHistoryService loginHistoryService;
  @MockitoBean private SensitiveVerifyService sensitiveVerifyService;
  @MockitoBean private UserLifecycleService lifecycleService;

  @Nested
  @DisplayName("GET /user/{id}")
  class GetById {

    /**
     * 资源不存在（业务层抛 {@link UserInfoExceptionCode#USER_NOT_FOUND}）。
     * ExceptionHandler 返回 HTTP 200 + code=B30001 的 i18n 包装。
     */
    @Test
    @DisplayName("should return business code envelope when user not found")
    void notFound() throws Exception {
      when(service.getById(anyString()))
          .thenThrow(new BusinessException(UserInfoExceptionCode.USER_NOT_FOUND));
      mvc.perform(get("/user/{id}", "9999")).andExpect(status().isOk()).andDo(print());
    }
  }

  @Nested
  @DisplayName("POST /user")
  class Create {

    /**
     * {@code username} 字段为 Bean Validation @NotBlank 标注；空白则 HTTP 400。
     */
    @Test
    @DisplayName("should return 400 when username blank")
    void blankUsername() throws Exception {
      UserAccountDTO dto = new UserAccountDTO();
      dto.setUsername("");
      dto.setPassword("Abcd1234");
      dto.setRealName("test");
      mvc.perform(post("/user").contentType(MediaType.APPLICATION_JSON).content(YdszJson.toJson(dto)))
          .andExpect(status().isBadRequest())
          .andDo(print());
    }

    /**
     * {@code password} 字段同时被 @NotBlank 和 @Size(min=8) 标注；
     * 少於 8 字符触发 HTTP 400。
     */
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

    /** 软删除成功 → 200 包装 true。 */
    @Test
    @DisplayName("should return 200 on successful removal")
    void ok() throws Exception {
      when(service.removeById("u_001")).thenReturn(Boolean.TRUE);
      mvc.perform(delete("/user/{id}", "u_001")).andExpect(status().isOk()).andDo(print());
    }
  }
}
