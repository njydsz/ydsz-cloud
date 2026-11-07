package com.njydsz.system.web.controller;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import com.njydsz.common.auth.context.AuthContextUtils;
import com.njydsz.common.auth.model.LoginUser;
import com.njydsz.common.core.context.RequestContext;
import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.system.domain.vo.SecondaryAuthVO;
import com.njydsz.system.server.service.SecondaryAuthService;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@link SecondaryAuthController} HTTP contract tests. */
class SecondaryAuthControllerTest extends BaseControllerMockTest {

  private MockMvc mockMvc;

  @Mock private SecondaryAuthService secondaryAuthService;

  @InjectMocks private SecondaryAuthController controller;

  @BeforeEach
  void setUp() {
    LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
    validator.afterPropertiesSet();
    mockMvc = MockMvcBuilders.standaloneSetup(controller).setValidator(validator).build();
  }

  @AfterEach
  void tearDown() {
    RequestContext.clear();
  }

  private void authenticate(String userId) {
    LoginUser loginUser = new LoginUser();
    loginUser.setUserId(userId);
    AuthContextUtils.setCurrent(loginUser);
  }

  @Test
  @DisplayName("POST /auth/2fa binds request body and passes current user to service")
  void should_verify_and_return_token() throws Exception {
    authenticate("user-1");
    SecondaryAuthVO result = new SecondaryAuthVO();
    result.setToken("secondary-token");
    result.setExpiresIn(1_800_000L);
    result.setExpiresAt(1_800_000L);
    when(secondaryAuthService.verify("user-1", "password-1", "config-edit")).thenReturn(result);

    String body = "{\"password\":\"password-1\",\"scene\":\"config-edit\"}";
    mockMvc.perform(
            post("/auth/2fa").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
        .andExpect(jsonPath("$.data.token").value("secondary-token"))
        .andExpect(jsonPath("$.data.expiresIn").value(1_800_000))
        .andExpect(jsonPath("$.data.expiresAt").value(1_800_000));

    verify(secondaryAuthService).verify("user-1", "password-1", "config-edit");
  }

  @Test
  @DisplayName("blank password is rejected by validation")
  void should_reject_blank_password() throws Exception {
    authenticate("user-1");

    mockMvc
        .perform(
            post("/auth/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\" \",\"scene\":\"config-edit\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("missing request body is rejected")
  void should_reject_missing_body() throws Exception {
    authenticate("user-1");

    mockMvc
        .perform(post("/auth/2fa").contentType(MediaType.APPLICATION_JSON))
        .andExpect(status().isBadRequest());
  }

  @Test
  @DisplayName("missing authentication context produces the configured unauthorized envelope")
  void should_reject_unauthenticated_request() throws Exception {
    mockMvc
        .perform(
            post("/auth/2fa")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"password\":\"password-1\",\"scene\":\"config-edit\"}"))
        .andExpect(status().isUnauthorized());
  }
}
