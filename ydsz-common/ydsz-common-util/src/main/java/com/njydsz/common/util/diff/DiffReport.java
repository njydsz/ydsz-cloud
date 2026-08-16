package com.njydsz.common.util.diff;

import com.njydsz.common.json.YdszJson;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import lombok.Getter;

/**
 * 差异报告
 *
 * <p>封装一次更新操作中所有字段的变更差异，支持生成 JSON 和可读文本格式。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
public class DiffReport implements Serializable {

  private static final long serialVersionUID = 1L;

  /** 字段差异列表 */
  private final List<FieldDiff> diffs;

  /**
   * 创建差异报告
   *
   * @param diffs 字段差异列表
   */
  public DiffReport(List<FieldDiff> diffs) {
    this.diffs = diffs != null ? List.copyOf(diffs) : Collections.emptyList();
  }

  /**
   * 是否有字段变更
   *
   * @return 如果存在至少一个变更返回 true
   */
  public boolean hasChanges() {
    return !diffs.isEmpty();
  }

  /**
   * 获取变更字段数量
   *
   * @return 变更字段数
   */
  public int changeCount() {
    return diffs.size();
  }

  /**
   * 生成 JSON 格式的差异报告
   *
   * <p>格式：[{"field":"username","label":"用户名","old":"张三","new":"李四","sensitive":false}]
   *
   * @return JSON 字符串
   */
  public String toJson() {
    if (diffs.isEmpty()) {
      return "[]";
    }
    return YdszJson.toJson(diffs);
  }

  /**
   * 生成可读文本格式的差异报告
   *
   * <p>格式：每行一个变更，如 "用户名: 张三 → 李四"
   *
   * @return 可读文本
   */
  public String toReadableText() {
    if (diffs.isEmpty()) {
      return "无变更";
    }
    return diffs.stream().map(FieldDiff::toReadableString).collect(Collectors.joining("; "));
  }
}
