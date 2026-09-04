package com.njydsz.workflow.server.service.impl.instance;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.njydsz.common.core.code.YdszResultCode;
import com.njydsz.common.exception.custom.SysException;
import com.njydsz.common.json.YdszJson;
import com.njydsz.common.util.collection.MapUtils;
import com.njydsz.workflow.domain.repository.FlowInstanceRepository;
import com.njydsz.workflow.domain.vo.FlowInstanceVO;

/**
 * 流程变量管理器
 *
 * <p>负责流程实例变量的读取、写入与解析，封装 variable JSON 字段的序列化/反序列化细节。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>变量读取</b>：{@link #getVariables} — 读取实例 variable JSON 并解析为 Map
 *   <li><b>变量写入</b>：{@link #setVariable} / {@link #setVariables} — 合并写入变量并持久化
 *   <li><b>变量解析</b>：{@link #parseVariables} — 将 variable JSON 解析为 Map（容错）
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FlowInstanceVariableManager {

  /** 流程实例仓储，负责 ydsz_flow_instance 的领域持久化 */
  private final FlowInstanceRepository instanceRepository;

  /**
   * P2-24: 读取实例流程变量
   *
   * @param instanceId 实例 ID
   * @return 变量 Map，无变量返回空 Map
   */
  @Transactional(readOnly = true)
  public Map<String, Object> getVariables(String instanceId) {
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null || !StringUtils.hasText(instance.getVariable())) {
      return Collections.emptyMap();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(instance.getVariable());
      return map == null ? Collections.emptyMap() : map;
    } catch (Exception e) {
      log.warn("[Flow] 解析 variable JSON 失败: instanceId={} err={}", instanceId, e.getMessage());
      return Collections.emptyMap();
    }
  }

  /**
   * P2-24: 合并写入单个变量并持久化
   *
   * @param instanceId 实例 ID
   * @param key 变量名
   * @param value 变量值
   */
  @Transactional(rollbackFor = Exception.class)
  public void setVariable(String instanceId, String key, Object value) {
    if (!StringUtils.hasText(key)) {
      throw SysException.builder()
          .resultCode(YdszResultCode.BAD_REQUEST)
          .message("error.workflow.instance.variable.key.required")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(instanceId)
          .build();
    }
    Map<String, Object> map = parseVariables(instance.getVariable());
    map.put(key, value);
    instanceRepository.updateVariable(instanceId, YdszJson.toJson(map));
    log.info("[Flow] 设置变量: instanceId={} key={}", instanceId, key);
  }

  /**
   * P2-24: 批量合并写入变量并持久化
   *
   * @param instanceId 实例 ID
   * @param variables 变量 Map
   */
  @Transactional(rollbackFor = Exception.class)
  public void setVariables(String instanceId, Map<String, Object> variables) {
    if (variables == null || variables.isEmpty()) {
      return;
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.instance.not.found")
          .params(instanceId)
          .build();
    }
    Map<String, Object> map = parseVariables(instance.getVariable());
    map.putAll(variables);
    instanceRepository.updateVariable(instanceId, YdszJson.toJson(map));
    log.info("[Flow] 批量设置变量: instanceId={} keys={}", instanceId, variables.keySet());
  }

  /**
   * 解析 variable JSON 为 Map，空值返回空 Map
   *
   * @param variable variable JSON 字符串
   * @return 解析后的 Map，解析失败返回空 Map
   */
  Map<String, Object> parseVariables(String variable) {
    if (!StringUtils.hasText(variable)) {
      return new HashMap<>(0);
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(variable);
      return map != null ? map : new HashMap<>(0);
    } catch (Exception e) {
      log.warn("[Flow] 解析变量失败: err={}", e.getMessage());
      return new HashMap<>(0);
    }
  }

  /**
   * 将任意 Map 转换为 Map<String, Object>（安全转型）。
   *
   * @param raw 原始 Map
   * @return 类型安全的 Map<String, Object>
   */
  static Map<String, Object> castToStringObjectMap(Map<?, ?> raw) {
    if (raw == null) {
      return new HashMap<>(0);
    }
    Map<String, Object> result = new HashMap<>(raw.size());
    for (Map.Entry<?, ?> entry : raw.entrySet()) {
      if (entry.getKey() instanceof String) {
        result.put((String) entry.getKey(), entry.getValue());
      }
    }
    return result;
  }
}
