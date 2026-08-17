package com.njydsz.system.server.service.impl;

import java.math.BigDecimal;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.njydsz.common.json.YdszJson;
import com.njydsz.system.server.service.ConfigService;
import com.njydsz.system.server.service.TypedConfigService;

/**
 * 强类型配置服务实现
 *
 * <p>基于 {@link ConfigService} 提供类型安全的配置值获取能力。
 *
 * @author ydsz-team
 * @since 1.9.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TypedConfigServiceImpl implements TypedConfigService {

  /** 系统配置服务 */
  private final ConfigService configService;

  @Override
  public String getString(String configKey, String defaultValue) {
    String value = configService.getConfigValue(configKey);
    return value != null ? value : defaultValue;
  }

  @Override
  public Integer getInt(String configKey, Integer defaultValue) {
    String value = configService.getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      log.warn("[TypedConfigService] 整数解析失败: key={}, value={}", configKey, value);
      return defaultValue;
    }
  }

  @Override
  public Long getLong(String configKey, Long defaultValue) {
    String value = configService.getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      log.warn("[TypedConfigService] 长整数解析失败: key={}, value={}", configKey, value);
      return defaultValue;
    }
  }

  @Override
  public Boolean getBoolean(String configKey, Boolean defaultValue) {
    String value = configService.getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    String trimmed = value.trim().toLowerCase();
    if ("true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed)) {
      return Boolean.TRUE;
    }
    if ("false".equals(trimmed) || "0".equals(trimmed) || "no".equals(trimmed)) {
      return Boolean.FALSE;
    }
    log.warn("[TypedConfigService] 布尔值解析失败: key={}, value={}", configKey, value);
    return defaultValue;
  }

  @Override
  public BigDecimal getDecimal(String configKey, BigDecimal defaultValue) {
    String value = configService.getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      log.warn("[TypedConfigService] 数值解析失败: key={}, value={}", configKey, value);
      return defaultValue;
    }
  }

  @Override
  public <T> T getJson(String configKey, Class<T> clazz, T defaultValue) {
    String value = configService.getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return YdszJson.fromJson(value, clazz);
    } catch (Exception e) {
      log.warn("[TypedConfigService] JSON 解析失败: key={}, valueType={}", configKey, clazz.getSimpleName());
      return defaultValue;
    }
  }

  @Override
  public Map<String, Object> getJsonAsMap(String configKey, Map<String, Object> defaultValue) {
    String value = configService.getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return YdszJson.parseMap(value);
    } catch (Exception e) {
      log.warn("[TypedConfigService] JSON Map 解析失败: key={}", configKey);
      return defaultValue;
    }
  }
}
