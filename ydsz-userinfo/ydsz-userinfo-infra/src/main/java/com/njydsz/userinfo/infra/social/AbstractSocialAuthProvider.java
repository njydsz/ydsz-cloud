package com.njydsz.userinfo.infra.social;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.userinfo.infra.config.SocialAuthProperties;
import com.njydsz.userinfo.domain.social.SocialAuthProvider;

/**
 * 社交认证提供者抽象基类。
 *
 * <p>封装各平台通用的 HTTP 调用、配置获取、响应解析等公共逻辑，子类只需实现差异化的端点 URL
 * 构建与响应字段映射。
 *
 * <p><b>子类约定：</b>
 *
 * <ul>
 *   <li>实现 {@link #getPlatform()} 返回平台标识</li>
 *   <li>实现 {@link #authorize(String, String)} 构建授权 URL</li>
 *   <li>实现 {@link #exchangeToken(String, String)} 完成令牌交换</li>
 *   <li>实现 {@link #getUserInfo(com.njydsz.userinfo.domain.social.SocialAccessToken)} 获取用户信息</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 2.24.0
 */
@Slf4j
@RequiredArgsConstructor
public abstract class AbstractSocialAuthProvider implements SocialAuthProvider {

  /** 社交认证配置 */
  protected final SocialAuthProperties socialAuthProperties;

  /** HTTP 客户端 */
  protected final JustAuthHttpClient httpClient;

  /**
   * 获取当前平台的配置。
   *
   * @return 平台配置；未配置返回 null
   */
  protected SocialAuthProperties.ProviderConfig getProviderConfig() {
    return socialAuthProperties.getProvider(getPlatform().toLowerCase());
  }

  /**
   * URL 编码参数值。
   *
   * @param value 原始值
   * @return UTF-8 URL 编码后的值
   */
  protected String urlEncode(String value) {
    return URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  /**
   * 从响应 Map 中获取字符串值。
   *
   * @param map 响应 Map
   * @param key 键
   * @return 值，不存在返回 null
   */
  protected String getStr(Map<String, Object> map, String key) {
    Object value = map.get(key);
    return value != null ? value.toString() : null;
  }

  /**
   * 从响应 Map 中获取长整数值。
   *
   * @param map 响应 Map
   * @param key 键
   * @param defaultValue 默认值
   * @return 值
   */
  protected Long getLong(Map<String, Object> map, String key, Long defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).longValue();
    }
    return defaultValue;
  }

  /**
   * 从响应 Map 中获取整数值。
   *
   * @param map 响应 Map
   * @param key 键
   * @param defaultValue 默认值
   * @return 值
   */
  protected Integer getInt(Map<String, Object> map, String key, Integer defaultValue) {
    Object value = map.get(key);
    if (value instanceof Number) {
      return ((Number) value).intValue();
    }
    return defaultValue;
  }
}
