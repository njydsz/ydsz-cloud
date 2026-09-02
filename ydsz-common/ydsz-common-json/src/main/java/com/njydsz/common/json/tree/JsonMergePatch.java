package com.njydsz.common.json.tree;

import java.util.Iterator;

/**
 * JSON Merge Patch（RFC 7396）实现。
 *
 * <p>JSON Merge Patch 是一种基于 JSON 文档的增量更新协议，通过对目标文档递归合并的方式 表示修改操作。语义等价于将 patch 文档"覆盖"到目标文档上：
 *
 * <ul>
 *   <li>patch 中的 {@code null} 值表示删除目标中对应字段
 *   <li>patch 中的对象值与目标中对应对象值递归合并
 *   <li>其他 patch 值直接替换目标值
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * JsonNode target = YdszJson.readTree("{\"name\":\"John\",\"age\":30,\"address\":{\"city\":\"NY\"}}");
 * JsonNode patch = YdszJson.readTree("{\"age\":31,\"address\":{\"zip\":\"10001\"},\"temp\":null}");
 * ObjectNode result = (ObjectNode) JsonMergePatch.apply(target, patch);
 * // result: {"name":"John","age":31,"address":{"city":"NY","zip":"10001"}}
 * }</pre>
 *
 * <p>与 {@link JsonPatch RFC 6902} 相比，Merge Patch 不支持对数组的精确操作和移动/复制语义， 但其文档形式更简洁，适合 RESTful API
 * 的部分更新场景（如 HTTP PATCH 方法）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see JsonPatch
 */
public final class JsonMergePatch {

  private JsonMergePatch() {
    // 工具类，不可实例化
  }

  /**
   * 将 Merge Patch 应用到目标节点（原地修改）。
   *
   * <p>算法语义（RFC 7396 §2）：
   *
   * <ol>
   *   <li>如果 patch 不是 JSON 对象，则结果就是 patch 本身。
   *   <li>如果目标不是 JSON 对象，则用空白对象替换目标。
   *   <li>遍历 patch 的所有字段：
   *       <ul>
   *         <li>若 patch 值为 {@code null}，则删除目标中对应字段。
   *         <li>若 patch 对象为 JSON 对象且目标对应字段也为 JSON 对象，则递归合并。
   *         <li>否则直接用 patch 值替换目标字段。
   *       </ul>
   * </ol>
   *
   * @param target 目标 JSON 节点
   * @param patch Merge Patch JSON 节点
   * @return 修改后的目标节点（可能是 {@code target} 本身，也可能是 {@code patch} 本身—— 当 patch 不是对象或目标不是对象时返回 patch）
   */
  public static JsonNode apply(JsonNode target, JsonNode patch) {
    if (target == null || patch == null) {
      return patch;
    }
    if (!patch.isObject() || !target.isObject()) {
      return patch;
    }
    return mergeInto((ObjectNode) target, (ObjectNode) patch);
  }

  /**
   * 递归将 patch 合并到 target 中（原地修改）。
   *
   * @return 修改后的 target
   */
  private static ObjectNode mergeInto(ObjectNode target, ObjectNode patch) {
    Iterator<String> fieldNameIterator = patch.fieldNames();
    while (fieldNameIterator.hasNext()) {
      String fieldName = fieldNameIterator.next();
      JsonNode patchValue = patch.get(fieldName);
      if (patchValue.isNull()) {
        // RFC 7396: null 表示删除
        target.remove(fieldName);
      } else if (patchValue.isObject()
          && target.containsKey(fieldName)
          && target.get(fieldName).isObject()) {
        // 双方都是对象 → 递归
        ObjectNode targetChild = (ObjectNode) target.get(fieldName);
        mergeInto(targetChild, (ObjectNode) patchValue);
      } else {
        // 替换（patchValue 可能是数组、标量、或目标对应字段不是对象）
        target.put(fieldName, patchValue.deepCopy());
      }
    }
    return target;
  }
}
