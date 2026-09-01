package com.njydsz.literule.server.model;

import java.util.LinkedHashMap;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.literule.domain.model.AbstractModelInputProvider;
import com.njydsz.literule.domain.vo.RuleContextVO;

/**
 * Mock 模型输入提供者（本地调试/联调用）
 *
 * <p>当 {@code ydsz.literule.model.mock-enabled=true} 且配置了 {@code mock-outputs} 时自动装配。
 * 返回固定配置的模型输出（如 {@code {"score": 0.9, "level": "HIGH"}}）， 用于本地无真实模型服务时的规则联调。
 *
 * <p>输出 key 无需带 "model." 前缀，由 {@link ModelInputRegistry#collectAllModelOutputs} 统一添加。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public class MockModelInputProvider extends AbstractModelInputProvider {

  /** 默认模型标识 */
  public static final String DEFAULT_MODEL_ID = "mock-model";

  /** Mock 输出快照（构造时固化，防止外部修改） */
  private final Map<String, Object> outputs;

  /** 模型标识 */
  private final String modelId;

  /** 无参构造（默认输出为空 Map） */
  public MockModelInputProvider() {
    this(Map.of());
  }

  /**
   * 构造 Mock 模型提供者（默认模型标识）
   *
   * @param outputs Mock 输出 Map（key 为模型字段名，value 为数值/字符串/布尔）
   */
  public MockModelInputProvider(Map<String, Object> outputs) {
    this(DEFAULT_MODEL_ID, outputs);
  }

  /**
   * 构造 Mock 模型提供者
   *
   * @param modelId 模型标识
   * @param outputs Mock 输出 Map（key 为模型字段名，value 为数值/字符串/布尔）
   */
  public MockModelInputProvider(String modelId, Map<String, Object> outputs) {
    this.modelId = modelId == null || modelId.isBlank() ? DEFAULT_MODEL_ID : modelId;
    this.outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    log.info("[LiteRule-Model] MockModelInputProvider 已初始化, modelId={}, mockOutputs={}",
        this.modelId, this.outputs);
  }

  @Override
  public String getModelId() {
    return modelId;
  }

    @Override
  protected Map<String, Object> doGetModelOutput(RuleContextVO context) {
    return new LinkedHashMap<>(outputs);
  }
}

