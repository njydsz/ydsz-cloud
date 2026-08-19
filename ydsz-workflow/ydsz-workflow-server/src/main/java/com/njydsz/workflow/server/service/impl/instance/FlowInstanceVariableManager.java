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
 * @since 1.0.0
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
          .message("error.workflow.msg_fae06125")
          .build();
    }
    FlowInstanceVO instance = instanceRepository.findById(instanceId).orElse(null);
    if (instance == null) {
      throw SysException.builder()
          .resultCode(YdszResultCode.NOT_FOUND)
          .key("error.workflow.msg_67a10717")
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
          .key("error.workflow.msg_67a10717")
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
      return new HashMap<>();
    }
    try {
      Map<String, Object> map = YdszJson.parseMap(variable);
      return map == null ? new HashMap<>() : map;
    } catch (Exception e) {
      log.warn("[Flow] 解析 variable JSON 失败，返回空 Map: {}", e.getMessage());
      return new HashMap<>();
    }
  }

  /**
   * 将 {@code Map<?,?>} 强转为 {@code Map<String, Object>}。
   *
   * <p>ext JSON 由业务方配置（节点扩展字段），运行时信任其结构为 Map&lt;String,Object&gt;， 因此这里的强转是安全的。该方法仅用于抑制 unchecked
   * cast 编译警告。
   *
   * @param m 原始 Map
   * @return 强转后的 Map
   */
  static Map<String, Object> castToStringObjectMap(Map<?, ?> m) {
    return MapUtils.toStringObjectMap(m);
  }
}
