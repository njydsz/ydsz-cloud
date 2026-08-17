package com.njydsz.common.notify.provider;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestTemplate;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;
import com.njydsz.common.notify.channel.NotifyChannelStrategy;

/**
 * 阿里云短信提供商实现（阿里云官方 RPC 协议）。
 *
 * <p>基于阿里云短信服务（dysmsapi）官方 RPC 风格接口实现：
 *
 * <ul>
 *   <li>请求方式：POST 至 {@code https://dysmsapi.aliyuncs.com/}
 *   <li>签名算法：RPC 签名 V1（HMAC-SHA1，AccessKey 认证）
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
 * <p><b>说明：</b>阿里云短信服务未提供公开的余额查询接口，{@link #queryBalance()}
 * 返回明确的「不支持」结果而非伪造数据。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public class AliyunSmsProvider implements SmsProvider {

  private static final Logger LOG = LoggerFactory.getLogger(AliyunSmsProvider.class);

  /** 阿里云短信 API 版本 */
  private static final String API_VERSION = "2017-05-25";

  /** 接口操作名 */
  private static final String ACTION_SEND_SMS = "SendSms";

  /** RPC 签名算法 */
  private static final String HMAC_SHA1 = "HmacSHA1";

  private final RestTemplate restTemplate;
  private final String endpoint;
  private final String accessKey;
  private final String secretKey;

  /**
   * 构造阿里云短信提供商
   *
   * @param restTemplate HTTP 客户端
   * @param endpoint API 端点（如 https://dysmsapi.aliyuncs.com）
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
    try {
      // 1. 组装公共请求参数（TreeMap 保证字典序，签名要求）
      TreeMap<String, String> params = new TreeMap<>();
      params.put("Action", ACTION_SEND_SMS);
      params.put("Version", API_VERSION);
      params.put("Format", "JSON");
      params.put("RegionId", "cn-hangzhou");
      params.put("AccessKeyId", accessKey);
      params.put("SignatureMethod", "HMAC-SHA1");
      params.put("SignatureVersion", "1.0");
      params.put("SignatureNonce", java.util.UUID.randomUUID().toString());
      params.put("Timestamp", formatTimestamp());
      // 2. 业务参数
      params.put("PhoneNumbers", phoneNumber);
      params.put("SignName", signName);
      params.put("TemplateCode", templateCode);
      params.put("TemplateParam", YdszJson.toJson(templateParams != null ? templateParams : Map.of()));

      // 3. 计算签名并加入参数
      params.put("Signature", sign(params));

      // 4. 以 application/x-www-form-urlencoded 表单提交
      HttpHeaders headers = new HttpHeaders();
      headers.setContentType(MediaType.APPLICATION_FORM_URLENCODED);
      String body = buildFormBody(params);
      String response =
          restTemplate.postForObject(endpoint, new HttpEntity<>(body, headers), String.class);
      return parseResponse(response);
    } catch (Exception e) {
      LOG.error("[AliyunSmsProvider] 发送失败: phone={}, error={}", phoneNumber, e.getMessage(), e);
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
        "成功" + successCount + "/" + phoneNumbers.size() + ", 最后错误: " + lastError);
  }

  @Override
  public SmsBalance queryBalance() {
    // 阿里云短信服务未提供公开余额查询 API，返回 -1 表示不支持，避免伪造数据
    LOG.warn("阿里云短信服务不提供公开余额查询接口，queryBalance 返回 -1（不支持）");
    return new SmsBalance(-1, "CNY", 0);
  }

  /**
   * 计算 RPC 签名 V1（HMAC-SHA1）。
   *
   * <p>签名串格式：{@code POST&%2F&<规范化参数串>}， 规范化参数串为按字典序排列的
   * {@code key=percentEncode(value)} 以 {@code &} 连接。
   *
   * @param params 请求参数（含 AccessKeyId 等公共参数）
   * @return 签名值
   */
  private String sign(TreeMap<String, String> params) {
    String canonicalizedQueryString = buildCanonicalizedQueryString(params);
    String stringToSign = "POST&%2F&" + percentEncode(canonicalizedQueryString);
    try {
      Mac mac = Mac.getInstance(HMAC_SHA1);
      mac.init(new SecretKeySpec((secretKey + "&").getBytes(StandardCharsets.UTF_8), HMAC_SHA1));
      byte[] signData = mac.doFinal(stringToSign.getBytes(StandardCharsets.UTF_8));
      return java.util.Base64.getEncoder().encodeToString(signData);
    } catch (NoSuchAlgorithmException | InvalidKeyException e) {
      throw new IllegalStateException("阿里云短信 RPC 签名失败", e);
    }
  }

  /** 构建规范化查询串：{@code key=percentEncode(value)&...}（参数已按字典序排列） */
  private String buildCanonicalizedQueryString(TreeMap<String, String> params) {
    StringBuilder sb = new StringBuilder();
    params.forEach(
        (k, v) -> {
          if (sb.length() > 0) {
            sb.append('&');
          }
          sb.append(percentEncode(k)).append('=').append(percentEncode(v));
        });
    return sb.toString();
  }

  /** 构建表单提交体（与签名时的规范化串一致，需重复百分号编码） */
  private String buildFormBody(TreeMap<String, String> params) {
    StringBuilder sb = new StringBuilder();
    params.forEach(
        (k, v) -> {
          if (sb.length() > 0) {
            sb.append('&');
          }
          // 表单提交按签名同款编码
          sb.append(percentEncode(k)).append('=').append(percentEncode(v));
        });
    return sb.toString();
  }

  /**
   * RFC 3986 百分号编码（阿里云 RPC 签名要求）。
   *
   * <p>保留 {@code A-Za-z0-9-_.~}，其余字符 UTF-8 编码为 {@code %XX}，空格编码为 {@code %20}。
   *
   * @param value 待编码字符串
   * @return 编码后的字符串
   */
  private String percentEncode(String value) {
    if (value == null) {
      return "";
    }
    try {
      // 先标准编码，再修正为 RFC 3986（+ 表示空格 → %20，* → %2A，%7E → ~）
      String encoded =
          URLEncoder.encode(value, StandardCharsets.UTF_8.name())
              .replace("+", "%20")
              .replace("*", "%2A")
              .replace("%7E", "~");
      return encoded;
    } catch (Exception e) {
      throw new IllegalStateException("URL 编码失败", e);
    }
  }

  /** ISO8601 UTC 时间戳格式化器（线程安全） */
  private static final DateTimeFormatter ISO8601_UTC =
      DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss'Z'").withZone(ZoneOffset.UTC);

  /** 生成阿里云要求的 ISO8601 时间戳（UTC） */
  private String formatTimestamp() {
    return ISO8601_UTC.format(Instant.now());
  }

  private SmsSendResult parseResponse(String response) {
    if (response == null || response.isEmpty()) {
      return SmsSendResult.failure("empty_response", "阿里云短信返回空响应");
    }
    try {
      JsonNode json = YdszJson.readTree(response);
      String code = json.has("Code") ? json.get("Code").asText() : null;
      if ("OK".equals(code)) {
        String messageId =
            json.has("BizId") ? json.get("BizId").asText() : "sent";
        return SmsSendResult.success(messageId);
      }
      String errorMsg = json.has("Message") ? json.get("Message").asText() : "发送失败";
      return SmsSendResult.failure(code != null ? code : "unknown", errorMsg);
    } catch (Exception e) {
      return SmsSendResult.failure("parse_error", "阿里云短信响应解析失败: " + e.getMessage());
    }
  }
}
