package com.njydsz.userinfo.infra.social;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import com.njydsz.common.json.YdszJson;

/**
 * JustAuth HTTP 客户端工具
 *
 * <p>封装 OAuth2 标准 HTTP 调用，支持授权码换令牌和获取用户信息。
 * 统一处理请求头、参数序列化、响应解析，简化各平台 Provider 实现。
 *
 * <p><b>线程安全：</b>无状态实现，可多线程并发调用。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Component
public class JustAuthHttpClient {

  /** RestTemplate 实例（线程安全） */
  private final RestTemplate restTemplate;

  /**
   * 构造 HTTP 客户端
   *
   * @param restTemplate RestTemplate Bean
   */
  public JustAuthHttpClient(RestTemplate restTemplate) {
    this.restTemplate = restTemplate;
  }

  /**
   * POST 表单请求换取访问令牌
   *
   * @param tokenUrl 令牌端点 URL
   * @param params 表单参数
   * @return 响应体 Map
   */
  public Map<String, Object> postFormForMap(String tokenUrl, Map<String, String> params) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);

    MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
    params.forEach(form::add);

    HttpEntity<MultiValueMap<String, String>> request = new HttpEntity<>(form, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

    return parseResponse(response.getBody());
  }

  /**
   * POST JSON 请求换取访问令牌
   *
   * @param tokenUrl 令牌端点 URL
   * @param params JSON 参数
   * @return 响应体 Map
   */
  public Map<String, Object> postJsonForMap(String tokenUrl, Map<String, String> params) {
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);

    HttpEntity<Map<String, String>> request = new HttpEntity<>(params, headers);
    ResponseEntity<String> response = restTemplate.postForEntity(tokenUrl, request, String.class);

    return parseResponse(response.getBody());
  }

  /**
   * GET 请求获取用户信息
   *
   * @param userInfoUrl 用户信息端点 URL
   * @param accessToken 访问令牌
   * @param params 附加参数
   * @return 响应体 Map
   */
  public Map<String, Object> getForMap(String userInfoUrl, String accessToken,
      Map<String, String> params) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(userInfoUrl);
    if (params != null) {
      params.forEach(builder::queryParam);
    }

    HttpHeaders headers = new HttpHeaders();
    headers.set("Authorization", "Bearer " + accessToken);
    headers.setAccept(java.util.Collections.singletonList(MediaType.APPLICATION_JSON));

    HttpEntity<Void> request = new HttpEntity<>(headers);
    ResponseEntity<String> response = restTemplate.exchange(
        builder.toUriString(),
        org.springframework.http.HttpMethod.GET,
        request,
        String.class);

    return parseResponse(response.getBody());
  }

  /**
   * 解析响应 JSON 为 Map
   *
   * @param body 响应体 JSON 字符串
   * @return 解析后的 Map
   */
  @SuppressWarnings("unchecked")
  private Map<String, Object> parseResponse(String body) {
    if (body == null || body.isBlank()) {
      return new HashMap<>();
    }
    return YdszJson.fromJson(body, Map.class);
  }
}
