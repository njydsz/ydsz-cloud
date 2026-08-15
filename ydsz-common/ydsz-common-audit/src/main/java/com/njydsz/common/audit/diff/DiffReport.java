package com.njydsz.common.audit.diff;

import java.io.Serializable;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.tree.JsonNode;

import lombok.Getter;

/**
 * 差异报告
 *
 * <p>封装一次更新操作中所有字段的变更差异，支持生成 JSON 和可读文本格式。
 *
 * <p><b>P3-1/P3-4 增强：</b>
 * <ul>
 *   <li>{@link #toJson()} 使用 YdszJson 引擎序列化，替代手动 StringBuilder</li>
 *   <li>{@link #toJsonPatch()} 输出 RFC 6902 JsonPatch 格式</li>
 *   <li>{@link #queryByPointer(String)} 使用 JSON Pointer 定位特定字段变更</li>
 * </ul>
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
     * <p>格式：[{@code "field":"username","label":"用户名","old":"张三","new":"李四","sensitive":false}]
     *
     * <p>P3-1: 使用 YdszJson 引擎序列化，替代手动 StringBuilder 拼接。
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
     * P3-1: 生成 RFC 6902 JsonPatch 格式的差异报告。
     *
     * <p>每个变更字段输出一个 {@code replace} 操作：
     * <pre>
     * [
     *   {"op":"replace","path":"/username","value":"李四"},
     *   {"op":"replace","path":"/email","value":"lisi@example.com"}
     * ]
     * </pre>
     *
     * @return JsonPatch 格式 JSON 字符串
     */
    public String toJsonPatch() {
        if (diffs.isEmpty()) {
            return "[]";
        }
        List<Map<String, Object>> patchOps = diffs.stream()
                .map(diff -> {
                    Map<String, Object> op = new LinkedHashMap<>();
                    op.put("op", "replace");
                    op.put("path", "/" + diff.getFieldName());
                    op.put("value", diff.getNewValue());
                    return op;
                })
                .collect(Collectors.toList());
        return YdszJson.toJson(patchOps);
    }

    /**
     * 使用 JSON Pointer 定位特定字段的变更值。
     *
     * <p>从 {@link #toJson()} 生成的 JSON 数组中，通过 JSON Pointer 路径
     * 定位特定字段的新值或旧值（基于 YdszJson 树解析实现，等价 RFC 6901）。
     *
     * <p>使用示例：
     * <pre>
     * DiffReport report = DiffCalculator.INSTANCE.calculate(oldUser, newUser);
     * // 获取第一个变更字段的新值
     * String newValue = report.queryByPointer("/0/new");
     * // 获取字段名为 "username" 的旧值（需先找到索引）
     * </pre>
     *
     * @param pointer JSON Pointer 路径（如 {@code "/0/new"}、{@code "/1/old"}）
     * @return 路径对应的值字符串，路径不存在返回 {@code null}
     */
    public String queryByPointer(String pointer) {
        if (diffs.isEmpty() || pointer == null || pointer.isBlank()) {
            return null;
        }
        try {
            String json = toJson();
            JsonNode root = YdszJson.readTree(json);
            JsonNode target = root.path(pointer.startsWith("/") ? pointer.substring(1) : pointer);
            if (target.isMissing() || target.isNull()) {
                return null;
            }
            Object value = target.asValue();
            return value == null ? null : String.valueOf(value);
        } catch (Exception e) {
            return null;
        }
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
        return diffs.stream()
                .map(FieldDiff::toReadableString)
                .collect(Collectors.joining("; "));
    }
}
