package com.njydsz.userinfo.web.controller;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.njydsz.common.test.BaseControllerMockTest;
import com.njydsz.userinfo.domain.vo.LoginVO;
import com.njydsz.userinfo.domain.vo.UserAccountVO;
import com.njydsz.userinfo.server.auth.AuthService;
import com.njydsz.userinfo.server.auth.CasService;
import com.njydsz.userinfo.server.config.CasProperties;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@link CasController} HTTP 契约测试。
 *
 * <p>覆盖 CAS 2.0/3.0 登录、ST 校验（XML/JSON）与登出端点。CAS 协议端点豁免 YdszResponse 包装，
 * 故测试直接断言协议要求的 XML/JSON 响应体与重定向头。
 *
 * @author ydsz-smoke-test
 * @since 26.10.09
 */
class CasControllerTest extends BaseControllerMockTest {

  private static final String BASE = "/cas";

  @Mock private CasService casService;
  @Mock private AuthService authService;

  private CasProperties casProperties;

  @InjectMocks private CasController controller;

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    casProperties = new CasProperties();
    casProperties.setTicketGrantingTicketTtl(Duration.ofHours(8));
    casProperties.setServiceTicketTtl(Duration.ofMinutes(5));
    controller = new CasController(casService, authService, casProperties);
    mockMvc = standaloneSetup(controller);
  }

  private static LoginVO loginVO(String userId, String username) {
    LoginVO.UserInfoVO userInfo = new LoginVO.UserInfoVO();
    userInfo.setUserId(userId);
    userInfo.setUsername(username);
    LoginVO vo = new LoginVO();
    vo.setAccessToken("access");
    vo.setRefreshToken("refresh");
    vo.setUserInfo(userInfo);
    return vo;
  }

  @Nested
  @DisplayName("GET /cas/login")
  class LoginPage {

    @Test
    @DisplayName("无 TGT 且未提交表单：返回 HTML 登录页")
    void should_return_login_page() throws Exception {
      mockMvc.perform(get(BASE + "/login").param("service", "https://app.example.com"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML_VALUE))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("CAS Login")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("https://app.example.com")));

      verify(casService, never()).issueServiceTicket(anyString(), anyString());
    }

    @Test
    @DisplayName("携带有效 TGT Cookie：直接签发 ST 并重定向回 service")
    void should_redirect_with_ticket_when_tgt_present() throws Exception {
      when(casService.issueServiceTicket("tgt-1", "https://app.example.com")).thenReturn("ST-1");

      mockMvc.perform(get(BASE + "/login")
              .param("service", "https://app.example.com")
              .cookie(new jakarta.servlet.http.Cookie("TGC", "tgt-1")))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", "https://app.example.com?ticket=ST-1"));
    }

    @Test
    @DisplayName("提交错误密码：返回 401 + 登录页")
    void should_return_401_when_credentials_invalid() throws Exception {
      when(authService.login(any(), any())).thenThrow(new IllegalStateException("bad credentials"));

      // 登录失败页仍是同一个 CAS Login 表单（带 service 回跳），仅状态码为 401
      mockMvc.perform(get(BASE + "/login")
              .param("service", "https://app.example.com")
              .param("username", "admin")
              .param("password", "wrong"))
          .andExpect(status().isUnauthorized())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("CAS Login")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("https://app.example.com")));
    }
  }

  @Nested
  @DisplayName("POST /cas/login")
  class LoginSubmit {

    @Test
    @DisplayName("登录成功：签发 TGT + ST 并设置 TGC Cookie")
    void should_issue_tgt_and_redirect() throws Exception {
      when(authService.login(any(), any())).thenReturn(loginVO("user-001", "admin"));
      when(casService.issueTicketGrantingTicket(any(UserAccountVO.class))).thenReturn("tgt-2");
      when(casService.issueServiceTicket("tgt-2", "https://app.example.com")).thenReturn("ST-2");

      mockMvc.perform(post(BASE + "/login")
              .param("service", "https://app.example.com")
              .param("username", "admin")
              .param("password", "S3cret!"))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", "https://app.example.com?ticket=ST-2"))
          .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
              .cookie().exists("TGC"));

      verify(casService).issueTicketGrantingTicket(any(UserAccountVO.class));
    }

    @Test
    @DisplayName("反例：认证失败 → HTTP 401")
    void should_return_401_when_auth_fails() throws Exception {
      when(authService.login(any(), any())).thenReturn(null);

      mockMvc.perform(post(BASE + "/login")
              .param("service", "https://app.example.com")
              .param("username", "admin")
              .param("password", "wrong"))
          .andExpect(status().isUnauthorized());
    }
  }

  @Nested
  @DisplayName("GET /cas/serviceValidate")
  class ServiceValidateXml {

    @Test
    @DisplayName("ST 校验成功：返回 CAS 2.0 authenticationSuccess XML")
    void should_return_success_xml() throws Exception {
      CasService.CasServiceTicketValidationResult result =
          CasService.CasServiceTicketValidationResult.builder()
              .success(true)
              .userId("user-001")
              .username("admin")
              .build();
      when(casService.validateServiceTicket("ST-1", "https://app.example.com")).thenReturn(result);

      mockMvc.perform(get(BASE + "/serviceValidate")
              .param("ticket", "ST-1")
              .param("service", "https://app.example.com"))
          .andExpect(status().isOk())
          .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML_VALUE))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("authenticationSuccess")))
          .andExpect(content().string(org.hamcrest.Matchers.containsString("<cas:user>admin</cas:user>")));
    }

    @Test
    @DisplayName("反例：ST 无效 → HTTP 403 + authenticationFailure XML")
    void should_return_failure_xml() throws Exception {
      when(casService.validateServiceTicket("ST-bad", "https://app.example.com"))
          .thenThrow(new IllegalStateException("ticket not recognized"));

      mockMvc.perform(get(BASE + "/serviceValidate")
              .param("ticket", "ST-bad")
              .param("service", "https://app.example.com"))
          .andExpect(status().isForbidden())
          .andExpect(content().string(org.hamcrest.Matchers.containsString("INVALID_TICKET")));
    }
  }

  @Nested
  @DisplayName("GET /cas/p3/serviceValidate")
  class ServiceValidateJson {

    @Test
    @DisplayName("ST 校验成功：返回 YdszResponse 包裹的用户信息")
    void should_return_success_json() throws Exception {
      CasService.CasServiceTicketValidationResult result =
          CasService.CasServiceTicketValidationResult.builder()
              .success(true)
              .userId("user-001")
              .username("admin")
              .build();
      when(casService.validateServiceTicket("ST-1", "https://app.example.com")).thenReturn(result);

      mockMvc.perform(get(BASE + "/p3/serviceValidate")
              .param("ticket", "ST-1")
              .param("service", "https://app.example.com"))
          .andExpect(status().isOk())
          .andExpect(jsonPath("$.code").value(SUCCESS_CODE))
          .andExpect(jsonPath("$.data.userId").value("user-001"))
          .andExpect(jsonPath("$.data.username").value("admin"));
    }

    @Test
    @DisplayName("反例：ST 无效 → HTTP 403 + INVALID_TICKET 错误信封")
    void should_return_error_json() throws Exception {
      when(casService.validateServiceTicket("ST-bad", "https://app.example.com"))
          .thenThrow(new IllegalStateException("ticket not recognized"));

      mockMvc.perform(get(BASE + "/p3/serviceValidate")
              .param("ticket", "ST-bad")
              .param("service", "https://app.example.com"))
          .andExpect(status().isForbidden())
          .andExpect(jsonPath("$.code").value("INVALID_TICKET"));
    }
  }

  @Nested
  @DisplayName("GET /cas/logout")
  class Logout {

    @Test
    @DisplayName("携带 TGT：删除 Redis TGT、清除 Cookie 并重定向到 service")
    void should_logout_and_redirect() throws Exception {
      mockMvc.perform(get(BASE + "/logout")
              .param("service", "https://app.example.com")
              .cookie(new jakarta.servlet.http.Cookie("TGC", "tgt-1")))
          .andExpect(status().isFound())
          .andExpect(header().string("Location", "https://app.example.com"));

      verify(casService).logout("tgt-1");
    }

    @Test
    @DisplayName("无 TGT 且无 service：返回 200 空响应")
    void should_return_ok_when_no_tgt() throws Exception {
      mockMvc.perform(get(BASE + "/logout"))
          .andExpect(status().isOk());

      verify(casService, never()).logout(anyString());
    }
  }
}
