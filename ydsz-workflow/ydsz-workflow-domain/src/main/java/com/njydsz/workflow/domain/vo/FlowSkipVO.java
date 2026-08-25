package com.njydsz.workflow.domain.vo;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;

import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;

/**
 * FlowSkip 视图对象。
 *
 * <p>提供 ext JSON 的懒解析 getter 方法，避免调用方重复编写解析逻辑。
 * 解析结果缓存在 {@code parsedExt} 中，同一 VO 多次调用只解析一次。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
@Slf4j
public class FlowSkipVO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  private String id;
  private String definitionId;
  private String flowCode;
  private String skipName;
  private String skipType;
  private String coordinate;
  private String skipCondition;
  private String nextNodeCode;
  private Integer nextNodeType;
  private String coordinateNext;
  private String skipList;
  private String ext;
  private String providerTraceId;
  private String createdBy;
  private LocalDateTime createdAt;
  private String updatedBy;
  private LocalDateTime updatedAt;

  /** ext JSON 懒解析缓存（不参与序列化）。 */
  private transient volatile Map<String, Object> parsedExt;

  /**
   * 获取 ext JSON 的解析结果 Map（懒解析、线程安全的 double-check 缓存）。
   *
   * @return ext 对应的 Map，无配置时返回空 Map（非 null）
   */
  public Map<String, Object> getExtMap() {
    if (parsedExt != null) {
      return parsedExt;
    }
    synchronized (this) {
      if (parsedExt != null) {
        return parsedExt;
      }
      if (ext == null || ext.isBlank()) {
        parsedExt = Collections.emptyMap();
        return parsedExt;
      }
      try {
        Map<String, Object> map = YdszJson.parseMap(ext);
        parsedExt = map != null ? map : Collections.emptyMap();
        return parsedExt;
      } catch (Exception e) {
        log.warn("[FlowSkipVO] 解析 ext JSON 失败: skipId={} err={}", id, e.getMessage());
        parsedExt = Collections.emptyMap();
        return parsedExt;
      }
    }
  }

  /**
   * 获取 ext 中的 sourceRef（源节点编码）。
   *
   * @return 源节点编码，不存在时返回 null
   */
  public String getSourceRef() {
    Object val = getExtMap().get("sourceRef");
    return val == null ? null : String.valueOf(val);
  }

  /**
   * 获取 ext 中的 sequenceFlowId。
   *
   * @return sequenceFlowId，不存在时返回 null
   */
  public String getSequenceFlowId() {
    Object val = getExtMap().get("sequenceFlowId");
    return val == null ? null : String.valueOf(val);
  }
}
