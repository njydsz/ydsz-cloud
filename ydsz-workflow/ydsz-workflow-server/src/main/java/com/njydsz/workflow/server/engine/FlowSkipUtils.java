package com.njydsz.workflow.server.engine;

import java.util.Map;

import lombok.extern.slf4j.Slf4j;

import com.njydsz.common.json.YdszJson;
import com.njydsz.workflow.domain.entity.FlowSkip;

/**
 * 流程跳转边（FlowSkip）工具类
 *
 * <p>集中提供 {@link FlowSkip} 的 ext JSON 字段解析方法，避免各模块重复实现。
 *
 * <p>skip 表无 source_node_code 列，源节点编码冗余存储在 ext JSON 的 {@code sourceRef} 字段 （见
 * FlowDefinitionServiceImpl 部署逻辑）。本工具提供唯一规范的读取入口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
public final class FlowSkipUtils {

  private FlowSkipUtils() {
    throw new AssertionError("工具类禁止实例化");
  }

  /**
   * 从 FlowSkip.ext JSON 中提取 sourceRef（入边源节点编码）
   *
   * <p>统一替代 {@code DefaultFlowAdvancer#extractSourceNodeCode}、 {@code
   * FlowGraphValidator#extractSourceRef}、{@code FlowDefinitionCacheService#extractSourceRef}
   * 三处重复实现。
   *
   * @param skip 跳转边
   * @return 源节点编码，不存在或解析失败时返回 null
   */
  public static String extractSourceNodeCode(FlowSkip skip) {
    if (skip == null || skip.getExt() == null || skip.getExt().isBlank()) {
      return null;
    }
    try {
      Map<String, Object> ext = YdszJson.parseMap(skip.getExt());
      if (ext == null) {
        return null;
      }
      Object val = ext.get("sourceRef");
      return val == null ? null : String.valueOf(val);
    } catch (Exception e) {
      log.warn("[Flow] 提取 sourceRef 失败, skipId={}, err={}", skip.getId(), e.getMessage());
      return null;
    }
  }

  /**
   * 从 FlowSkip.ext JSON 中提取指定字段
   *
   * @param skip 跳转边
   * @param fieldName ext JSON 中的字段名
   * @return 字段值字符串，不存在或解析失败时返回 null
   */
  public static String extractExtField(FlowSkip skip, String fieldName) {
    if (skip == null
        || skip.getExt() == null
        || skip.getExt().isBlank()
        || fieldName == null
        || fieldName.isBlank()) {
      return null;
    }
    try {
      Map<String, Object> ext = YdszJson.parseMap(skip.getExt());
      if (ext == null) {
        return null;
      }
      Object val = ext.get(fieldName);
      return val == null ? null : String.valueOf(val);
    } catch (Exception e) {
      log.warn("[Flow] 提取 ext.{} 失败, skipId={}, err={}", fieldName, skip.getId(), e.getMessage());
      return null;
    }
  }
}
