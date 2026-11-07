package com.njydsz.userinfo.web.controller;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class DiagTest {
  public record Req(
      @RequestParam String grantType,
      @RequestParam(required = false) String code,
      @RequestParam(required = false) String clientId,
      @RequestParam(required = false) String clientSecret,
      @RequestParam(required = false) String codeVerifier) {}

  @RestController
  static class C {
    @PostMapping("/diag")
    public String x(Req req) {
      return "grant=" + req.grantType() + ", code=" + req.code()
          + ", clientId=" + req.clientId() + ", secret=" + req.clientSecret()
          + ", verifier=" + req.codeVerifier();
    }
  }

  @Test
  void diag() throws Exception {
    MockMvc mvc = MockMvcBuilders.standaloneSetup(new C()).build();
    String body = mvc.perform(post("/diag")
            .param("grantType", "authorization_code")
            .param("code", "c1")
            .param("clientId", "cid")
            .param("clientSecret", "sec")
            .param("codeVerifier", "ver"))
        .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
    System.out.println("DIAG-BODY=" + body);
  }
}
