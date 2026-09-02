package com.njydsz.common.audit.diff;.diff
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import com.njydsz.common.json.YdszJson;

/**
 * 变更 diff 快照计算器 —— 计算操作前后的字段级差异。
 *
 * <p>由审计切面在以下场景调用：
 * <ol>
 *   <li>方法执行前：查询旧值 → 序列化为 {@code diffBeforeSnapshot}</li>
 *   <li>方法执行后：捕获返回值 / 接受新对象 → 序列化为 {@code diffAfterSnapshot}</li>
 *   <li>两者都不为空时：计算 field-level diff 以结构化形式呈现</li>
 * </ol>
 *
 * <p>输出格式（存储到审计日志的 JSON）：
 * <pre>
 * {
 *   "changedFields": [
 *     { "field": "username", "old": "alice", "new": "bob" },
 *     { "field": "email", "old": null, "new": "bob@example.com" }
 *   ],
 *   "addedFields": ["newField"],
 *   "removedFields": ["oldField"]
 * }
 * </pre>
 *
 * <p>注意：diff 本质是快照级别的文本比对，不替代业务层的操作性日志；
 * 当同一字段在同一请求内被多次变更时，仅保留「第一次」和「最后一次」。
 *
 * @author ydsz-team
 * @since 4.1.0 (P2-14)
 */
public final class DiffSnapshotHelper {

  private DiffSnapshotHelper() {
    // 工具类禁止实例化
  }

  /**
   * 计算两个 JSON 字符串表示的同类对象的 field-level diff。
   *
   * @param beforeJson 变更前 JSON（为 null 时视为全新创建）
   * @param afterJson  变更后 JSON（为 null 时视为完全删除）
   * @param ignoredFields 忽略比对的字段集合（如 updateTime、version 等自动维护字段）
   * @return diff 结果（永不为 null，无变化时返回空 changedFields）
   */
  public static DiffResult diff(
      String beforeJson,
      String afterJson,
      Set<String> ignoredFields) {
    // 快速路径：完全相同
    if (beforeJson != null && beforeJson.equals(afterJson)) {
      return DiffResult.empty();
    }
    // 创建
    if (beforeJson == null || beforeJson.isBlank()) {
      return DiffResult.create(afterJson);
    }
    // 删除
    if (afterJson == null || afterJson.isBlank()) {
      return DiffResult.delete(beforeJson);
    }

    Map<String, Object> before = parseJson(beforeJson);
    Map<String, Object> after = parseJson(afterJson);
    Set<String> ignored = ignoredFields != null ? ignoredFields : Collections.emptySet();

    DiffResult result = new DiffResult();
    // before 有 after 无（删除的字段）
    for (String key : before.keySet()) {
      if (!ignored.contains(key) && !after.containsKey(key)) {
        result.getRemovedFields().add(key);
      }
    }
    // after 有 before 无（新增的字段）
    for (String key : after.keySet()) {
      if (!ignored.contains(key) && !before.containsKey(key)) {
        result.getAddedFields().add(key);
      }
    }
    // 两者都有（可能变更）
    for (String key : before.keySet()) {
      if (ignored.contains(key) || !after.containsKey(key)) {
        continue;
      }
      Object oldVal = before.get(key);
      Object newVal = after.get(key);
      if (oldVal == null && newVal == null) {
        continue;
      }
      if (oldVal != null && oldVal.equals(newVal)) {
        continue;
      }
      result.getChangedFields().add(
          new DiffResult.FieldChange(key, oldVal == null ? null : oldVal.toString(), newVal == null ? null : newVal.toString()));
    }
    return result;
  }

  /**
   * 简易 JSON → Map 解析（仅支持一层扁平 key-value；嵌套对象退化为 toString）。
   *
   * <p>生产环境应委托给 ydsz-common-json 统一反序列化，此处保留轻量实现作为 fallback。
   */
  @SuppressWarnings("unchecked")
  private static Map<String, Object> parseJson(String json) {
    if (json == null || json.isBlank()) {
      return Collections.emptyMap();
    }
    try {
      // 采用 ydsz-common-json 统一 JSON（项目禁止使用 Jackson/Fastjson2，见 §6.2）
      // 返回 Map（具体实现类由 ydsz-common-json 内部决定）
      return YdszJson.parseMap(json);
    } catch (Exception e) {
      // 解析失败时返回空 map（避免 diff 计算阻断审计主流程）
      return Collections.emptyMap();
    }
  }

  /** diff 计算结果实体（与审计日志结构化字段对应） */
  @Data
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  @Schema(description = "字段级变更 diff 结果")
  public static class DiffResult implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 发生变更的字段列表 */
    @Builder.Default
    private List<FieldChange> changedFields = new ArrayList<>(4);