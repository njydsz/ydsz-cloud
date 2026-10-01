package com.njydsz.common.notify.provider;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.date.DateUtils;
import com.njydsz.common.notify.signature.AliyunSmsSigner;

/**
 * 阿里云短信提供商实现（阿里云官方 RPC 协议）。
 *
 * <p>基于阿里云短信服务（dysmsapi）官方 RPC 风格接口实现：
 *
 * <ul>
 *   <li>请求方式：GET 至 {@code https://dysmsapi.aliyuncs.com/}（标准 Common RPC 协议）
 *   <li>签名算法：RPC 签名 V1（HMAC-SHA1，AccessKey 认证）— 委托 {@link AliyunSmsSigner}
 *   <li>核心参数：{@code Action=SendSms}、{@code Version=2017-05-25}、{@code PhoneNumbers}、{@code
 *       SignName}、{@code TemplateCode}、{@code TemplateParam}
 * </ul>
 *
 * <p><b>配置：</b>
 *
 * <pre>{@code
 * ydsz:
 *   notify:
 *     sms:
 *       provider: aliyun
 *       endpoint: https://dysmsapi.aliyuncs.com
 *       access-key-id: your-access-key-id
 *       access-key-secret: your-access-key-secret
 * }</pre>
 *
 * <p><b>说明：</b>阿里云短信服务未提供公开的余额查询接口，{@link #queryBalance()} 返回明确的「不支持」结果。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public class AliyunSmsProvider implements SmsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AliyunSmsProvider.class);

  /** 阿里云短信 API 版本 */
  private static final String API_VERSION = "2017-05-25";

  /** 接口操作名 */
  private static final String ACTION_SEND_SMS = "SendSms";

  private static final int MAP_CAPACITY_16 = 16;

  private final RestTemplate restTemplate;
  private final String endpoint;
  private final String accessKey;
  private final String secretKey;

  /**
   * 构造阿里云短信提供商
   *
   * @param restTemplate HTTP 客户端
   * @param endpoint API 端点
   * @param accessKey 访问密钥 AccessKeyId
   * @param secretKey 秘密密钥 AccessKeySecret
   */
  public AliyunSmsProvider(
      RestTemplate restTemplate, String endpoint, String accessKey, String secretKey) {
    this.restTemplate = restTemplate;
    this.endpoint = endpoint;
    this.accessKey = accessKey;
    this.secretKey = secretKey;
  }

  @Override
  public String getProviderName() {
    return "aliyun";
  }

  @Override
  public SmsSendResult send(
      String phoneNumber,
      String signName,
      String templateCode,
      Map<String, Object> templateParams) {
    if (accessKey == null || accessKey.isEmpty() || secretKey == null || secretKey.isEmpty()) {
      LOG.warn("[AliyunSmsProvider] credential not configured, skip send: phone={}", phoneNumber);
      return SmsSendResult.failure("credential_missing", "Aliyun SMS credential not configured");
    }
    try {
      Map<String, String> params = buildCommonParams();
      params.put("PhoneNumbers", phoneNumber);
      params.put("SignName", signName);
      params.put("TemplateCode", templateCode);
      params.put("TemplateParam", YdszJson.toJson(templateParams != null ? templateParams : Map.of()));
      String signature = AliyunSmsSigner.sign(params, secretKey);
      params.put("Signature", signature);
      String url = endpoint + "/?" + AliyunSmsSigner.buildQuery(params);
      ResponseEntity<String> resp = restTemplate.getForEntity(url, String.class);
      return parseResponse(resp.getBody());
    } catch (Exception e) {
      LOG.error("[AliyunSmsProvider] send failed: phone={}, error={}", phoneNumber, e.getMessage(), e);
      return SmsSendResult.failure("send_error", e.getMessage());
    }
  }

  @Override
  public SmsSendResult batchSend(
      List<String> phoneNumbers,
      String signName,
      String templateCode,
      Map<String, Object> templateParams) {
    int successCount = 0;
    String lastError = null;
    for (String phone : phoneNumbers) {
      SmsSendResult result = send(phone, signName, templateCode, templateParams);
      if (result.isSuccess()) {
        successCount++;
      } else {
        lastError = result.getErrorMessage();
      }
    }
    if (successCount == phoneNumbers.size()) {
      return SmsSendResult.success("batch:" + successCount);
    }
    return SmsSendResult.failure(
        "partial_failure",
        "success " + successCount + "/" + phoneNumbers.size() + ", last error: " + lastError);
  }

  @Override
  public SmsBalance queryBalance() {
    LOG.warn("Aliyun SMS does not provide public balance query API, queryBalance returns -1");
    return new SmsBalance(-1, "CNY", java.math.BigDecimal.ZERO);
  }

  /** 构造公共请求参数 */
  private Map<String, String> buildCommonParams() {
    Map<String, String> p = new HashMap<>(MAP_CAPACITY_16);
    p.put("AccessKeyId", accessKey);
    p.put("Action", ACTION_SEND_SMS);
    p.put("Format", "JSON");
    p.put("RegionId", "cn-hangzhou");
    p.put("SignatureMethod", "HMAC-SHA1");
    p.put("SignatureNonce", UUID.randomUUID().toString());
    p.put("SignatureVersion", "1.0");
    p.put("Timestamp", DateUtils.formatUtcDateTime(LocalDateTime.now()));
    p.put("Version", API_VERSION);
    return p;
  }

  private SmsSendResult parseResponse(String response) {
    if (response == null || response.isEmpty()) {
      return SmsSendResult.failure("empty_response", "Aliyun SMS returned empty response");
    }
    try {
      Map<String, Object> json = YdszJson.parseMap(response);
      Object codeObj = json.get("Code");
      String code = codeObj != null ? codeObj.toString() : null;
      if ("OK".equals(code)) {
        Object bizIdObj = json.get("BizId");
        String messageId = bizIdObj != null ? bizIdObj.toString() : "sent";
        return SmsSendResult.success(messageId);
      }
      Object msgObj = json.get("Message");
      String errorMsg = msgObj != null ? msgObj.toString() : "send failed";
      return SmsSendResult.failure(code != null ? code : "unknown", errorMsg);
    } catch (Exception e) {
      return SmsSendResult.failure("parse_error", "Aliyun SMS response parse failed: " + e.getMessage());
    }
  }
}
