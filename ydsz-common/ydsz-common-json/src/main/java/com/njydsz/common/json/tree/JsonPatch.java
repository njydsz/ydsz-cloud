package com.njydsz.common.json.tree;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.njydsz.common.json.YdszJson;
import com.njydsz.common.json.exception.JsonException;
import com.njydsz.common.json.type.JsonType;

/**
 * JSON Patch (RFC 6902) 与 JSON Merge Patch (RFC 7396) 实现。
 *
 * <p>对标 Jackson 的 JsonPatch.java，提供 REST API PATCH 方法对资源做局部更新的能力。
 *
 * <p><b>JSON Patch (RFC 6902) 示例：</b>
 *
 * <pre>
 * String patchJson = "[{\"op\":\"replace\",\"path\":\"/name\",\"value\":\"newName\"}," +
 *                    "{\"op\":\"remove\",\"path\":\"/age\"}," +
 *                    "{\"op\":\"add\",\"path\":\"/email\",\"value\":\"a@b.com\"}]";
 *
 * User patched = YdszJson.applyPatch(patchJson, existingUser, User.class);
 * </pre>
 *
 * <p><b>JSON Merge Patch (RFC 7396) 示例：</b>
 *
 * <pre>
 * String mergeJson = "{\"name\":\"newName\",\"age\":null,\"email\":\"a@b.com\"}";
 *
 * User patched = YdszJson.applyMergePatch(mergeJson, existingUser, User.class);
 * </pre>
 *
 * <p><b>路径语法 (JSON Pointer RFC 6901)：</b>
 *
 * <ul>
 *   <li>{@code /name} - 对象字段
 *   <li>{@code /items/0} - 数组第 1 个元素
 *   <li>{@code /items/-} - 数组末尾（add 操作专用）
 *   <li>{@code /address/city} - 嵌套对象字段
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 * @see <a href="https://tools.ietf.org/html/rfc6902">RFC 6902 - JSON Patch</a>
 * @see <a href="https://tools.ietf.org/html/rfc7396">RFC 7396 - JSON Merge Patch</a>
 */
public final class JsonPatch {

  private JsonPatch() {
    throw new UnsupportedOperationException("JsonPatch is a utility class");
  }

  // ==================== Patch 操作枚举 ====================

  /** JSON Patch 操作类型 */
  public enum Operation {
    ADD,
    REMOVE,
    REPLACE,
    MOVE,
    COPY,
    TEST
  }

  /** 单个 Patch 操作 */
  public static class PatchOp {
/** op */
/** op */
    public final Operation op;
/** path */
/** path */
    public final String path;
/** value */
    public final Object value; // 用于 add / replace / test
/** from */
    public final String from; // 用于 move / copy

    public PatchOp(Operation op, String path, Object value, String from) {
      this.op = op;
      this.path = path;
      this.value = value;
      this.from = from;
    }
  }

  // ==================== 公共 API ====================

  /**
   * 解析 Patch 操作列表。
   *
   * @param patchJson Patch JSON 数组字符串，如 [{"op":"replace","path":"/name","value":"x"}]
   * @return Patch 操作列表
   */
  public static List<PatchOp> parse(String patchJson) {
    if (patchJson == null || patchJson.isBlank()) {
      throw new JsonException("Patch JSON must not be null or empty");
    }

    List<Map<String, Object>> ops =
        YdszJson.fromJson(patchJson, new JsonType<List<Map<String, Object>>>() {});

    if (ops == null || ops.isEmpty()) {
      throw new JsonException("Patch JSON must be a non-empty array");
    }

    List<PatchOp> result = new ArrayList<>(ops.size());
    for (Map<String, Object> opMap : ops) {
      result.add(parseSingleOp(opMap));
    }
    return result;
  }

  /**
   * 应用 Patch 到目标对象，返回新对象。
   *
   * @param patchJson Patch JSON 数组字符串
   * @param target 目标对象
   * @param clazz 目标类型
   * @param <T> 目标类型参数
   * @return Patch 后的新对象
   */
  public static <T> T apply(String patchJson, T target, Class<T> clazz) {
    ObjectNode tree = applyToTree(patchJson, (ObjectNode) YdszJson.valueToTree(target));
    return YdszJson.convertValue(tree, clazz);
  }

  /**
   * 将 RFC 6902 Patch 直接应用到 JsonNode 树（原地修改），返回同一棵树。
   *
   * <p>P-2 优化：供树模型调用方直接操作，避免 {code Map} 中转 （原先 {@code YdszJson.patch} 走 parseMap → Map → tree → Map
   * → String 的多重转换）。
   *
   * @param patchJson Patch JSON 数组字符串
   * @param tree 目标树（必须是 ObjectNode）
   * @return 应用 Patch 后的同一棵树（原地修改）
   */
  public static ObjectNode applyToTree(String patchJson, ObjectNode tree) {
    List<PatchOp> ops = parse(patchJson);
    for (PatchOp op : ops) {
      applyOp(tree, op);
    }
    return tree;
  }

  /**
   * 应用 Merge Patch (RFC 7396) 到目标对象。
   *
   * <p>Merge Patch 语义：
   *
   * <ul>
   *   <li>目标对象中存在的字段：如果 patch 值为 null 则删除，否则替换
   *   <li>目标对象中不存在的字段：添加
   *   <li>patch 中未提及的字段：保持不变
   * </ul>
   *
   * @param mergeJson Merge Patch JSON 字符串
   * @param target 目标对象
   * @param clazz 目标类型
   * @param <T> 目标类型参数
   * @return Patch 后的新对象
   */
  public static <T> T applyMerge(String mergeJson, T target, Class<T> clazz) {
    if (mergeJson == null || mergeJson.isBlank()) {
      return target;
    }

    ObjectNode tree = (ObjectNode) YdszJson.valueToTree(target);
    Map<String, Object> patchMap =
        YdszJson.fromJson(mergeJson, new JsonType<Map<String, Object>>() {});

    if (patchMap == null) {
      return target;
    }

    applyMergePatch(tree, patchMap);
    return YdszJson.convertValue(tree, clazz);
  }

  // ==================== 内部实现 ====================

  /** 解析单个 Patch 操作。 */
  private static PatchOp parseSingleOp(Map<String, Object> opMap) {
    String opStr = getString(opMap, "op");
    String path = getString(opMap, "path");

    if (opStr == null || opStr.isEmpty()) {
      throw new JsonException("Patch operation must have 'op' field");
    }
    if (path == null || path.isEmpty()) {
      throw new JsonException("Patch operation must have 'path' field");
    }

    Operation op;
    try {
      op = Operation.valueOf(opStr.toUpperCase());
    } catch (IllegalArgumentException e) {
      throw new JsonException("Unknown patch operation: " + opStr);
    }

    Object value = opMap.get("value");
    String from = getString(opMap, "from");

    return new PatchOp(op, path, value, from);
  }

  /** 对 ObjectNode 应用单个 Patch 操作。 */
  private static void applyOp(ObjectNode tree, PatchOp op) {
    String[] segments = parsePath(op.path);
    PathResolution resolution = resolvePath(tree, segments, op.op);

    switch (op.op) {
      case ADD -> applyAdd(resolution, segments[segments.length - 1], op.value);
      case REMOVE -> applyRemove(resolution, segments[segments.length - 1]);
      case REPLACE -> applyReplace(resolution, segments[segments.length - 1], op.value);
      case TEST -> applyTest(resolution, segments[segments.length - 1], op.value);
      case MOVE -> applyMove(tree, op);
      case COPY -> applyCopy(tree, op);
    }
  }

  /** Add 操作：在指定路径添加值。 */
  private static void applyAdd(PathResolution resolution, String lastSegment, Object value) {
    if (resolution.parent == null) {
      throw new JsonException("Cannot add to non-existent parent: " + lastSegment);
    }
    if (resolution.parent instanceof ObjectNode objNode) {
      objNode.put(lastSegment, value);
    } else if (resolution.parent instanceof ArrayNode arrNode) {
      if ("-".equals(lastSegment)) {
        arrNode.add(value);
      } else {
        int idx = parseIndex(lastSegment, arrNode.size() + 1);
        if (idx > arrNode.size()) {
          throw new JsonException("Array index out of bounds: " + lastSegment);
        }
        arrNode.insert(idx, value);
      }
    } else {
      throw new JsonException(
          "Cannot add to type: " + resolution.parent.getClass().getSimpleName());
    }
  }

  /** Remove 操作：删除指定路径的值。 */
  private static void applyRemove(PathResolution resolution, String lastSegment) {
    if (resolution.parent == null) {
      throw new JsonException("Cannot remove from non-existent path");
    }
    if (resolution.parent instanceof ObjectNode objNode) {
      objNode.remove(lastSegment);
    } else if (resolution.parent instanceof ArrayNode arrNode) {
      int idx = parseIndex(lastSegment, arrNode.size());
      if (idx >= arrNode.size()) {
        throw new JsonException("Cannot remove out-of-bounds index: " + lastSegment);
      }
      arrNode.remove(idx);
    } else {
      throw new JsonException(
          "Cannot remove from type: " + resolution.parent.getClass().getSimpleName());
    }
  }

  /** Replace 操作：替换指定路径的值。 */
  private static void applyReplace(PathResolution resolution, String lastSegment, Object value) {
    if (resolution.parent == null) {
      throw new JsonException("Cannot replace on non-existent path");
    }
    if (resolution.parent instanceof ObjectNode objNode) {
      if (!objNode.has(lastSegment)) {
        throw new JsonException("Cannot replace non-existent field: " + lastSegment);
      }
      objNode.put(lastSegment, value);
    } else if (resolution.parent instanceof ArrayNode arrNode) {
      int idx = parseIndex(lastSegment, arrNode.size());
      if (idx >= arrNode.size()) {
        throw new JsonException("Cannot replace out-of-bounds index: " + lastSegment);
      }
      arrNode.set(idx, value);
    } else {
      throw new JsonException(
          "Cannot replace on type: " + resolution.parent.getClass().getSimpleName());
    }
  }

  /** Test 操作：验证指定路径的值是否匹配。 */
  private static void applyTest(PathResolution resolution, String lastSegment, Object expected) {
    Object actual = getValueAt(resolution, lastSegment);
    if (!java.util.Objects.equals(actual, expected)) {
      throw new JsonException(
          "TEST failed at '" + lastSegment + "': expected " + expected + " but got " + actual);
    }
  }

  /** Move 操作：将值从 from 路径移动到 path 路径。 */
  private static void applyMove(ObjectNode tree, PatchOp op) {
    String[] fromSegments = parsePath(op.from);
    PathResolution fromResolution = resolvePath(tree, fromSegments, Operation.MOVE);

    // 获取 from 值
    Object value = getValueAt(fromResolution, fromSegments[fromSegments.length - 1]);
    // 删除 from 位置
    applyRemove(fromResolution, fromSegments[fromSegments.length - 1]);
    // 添加值到新位置
    PathResolution toResolution = resolvePath(tree, parsePath(op.path), Operation.ADD);
    applyAdd(toResolution, parsePath(op.path)[parsePath(op.path).length - 1], value);
  }

  /** Copy 操作：将值从 from 路径复制到 path 路径。 */
  private static void applyCopy(ObjectNode tree, PatchOp op) {
    String[] fromSegments = parsePath(op.from);
    PathResolution fromResolution = resolvePath(tree, fromSegments, Operation.COPY);

    // 获取 from 值（复制时不删除）
    Object value = getValueAt(fromResolution, fromSegments[fromSegments.length - 1]);
    // 添加值到新位置
    PathResolution toResolution = resolvePath(tree, parsePath(op.path), Operation.ADD);
    applyAdd(toResolution, parsePath(op.path)[parsePath(op.path).length - 1], value);
  }

  /** 应用 Merge Patch (RFC 7396)。 */
  private static void applyMergePatch(ObjectNode tree, Map<String, Object> patch) {
    for (Map.Entry<String, Object> entry : patch.entrySet()) {
      String key = entry.getKey();
      Object value = entry.getValue();

      if (value == null) {
        // null 表示删除字段
        tree.remove(key);
      } else if (value instanceof Map<?, ?> nestedMap) {
        // 嵌套对象：递归 Merge Patch
        JsonNode child = tree.get(key);
        if (child instanceof ObjectNode childObj) {
          @SuppressWarnings("unchecked")
          Map<String, Object> nestedPatch = (Map<String, Object>) nestedMap;
          applyMergePatch(childObj, nestedPatch);
        } else {
          tree.put(key, value);
        }
      } else {
        tree.put(key, value);
      }
    }
  }

  // ==================== 路径处理 ====================

  /** JSON Pointer 路径解析结果 */
  private static class PathResolution {
    final JsonNode parent; // 目标节点的父节点
    final String lastKey; // 最后一段路径键名

    PathResolution(JsonNode parent, String lastKey) {
      this.parent = parent;
      this.lastKey = lastKey;
    }
  }

  /** 解析 JSON Pointer 路径（去掉前导 /，分割）。 */
  private static String[] parsePath(String path) {
    if (path == null || path.isEmpty() || "/".equals(path)) {
      return new String[0];
    }
    if (!path.startsWith("/")) {
      throw new JsonException("JSON Pointer path must start with '/': " + path);
    }
    String content = path.substring(1);
    if (content.isEmpty()) {
      return new String[0];
    }
    // 解码 JSON Pointer 转义：~1 → /, ~0 → ~
    String[] segments = content.split("/");
    for (int i = 0; i < segments.length; i++) {
      segments[i] = segments[i].replace("~1", "/").replace("~0", "~");
    }
    return segments;
  }

  /** 解析路径，返回父节点和最后一段。 如果中间路径段不存在且 op 是 ADD，则创建中间节点。 */
  private static PathResolution resolvePath(ObjectNode root, String[] segments, Operation op) {
    if (segments.length == 0) {
      return new PathResolution(null, "");
    }
    if (segments.length == 1) {
      return new PathResolution(root, segments[0]);
    }

    JsonNode current = root;
    for (int i = 0; i < segments.length - 1; i++) {
      String seg = segments[i];
      JsonNode next = getChild(current, seg);

      if (next == null || next.isNull()) {
        if (op == Operation.ADD) {
          // 创建中间节点
          ObjectNode intermediate = new ObjectNode();
          ((ObjectNode) current).put(seg, intermediate);
          next = intermediate;
        } else {
          throw new JsonException("Path segment '" + seg + "' does not exist");
        }
      }
      current = next;
    }

    return new PathResolution(current, segments[segments.length - 1]);
  }

  /** 获取子节点（支持 ObjectNode 字段和 ArrayNode 索引）。 */
  private static JsonNode getChild(JsonNode node, String key) {
    if (node instanceof ObjectNode objNode) {
      return objNode.get(key);
    } else if (node instanceof ArrayNode arrNode) {
      int idx = parseIndex(key, arrNode.size());
      if (idx < arrNode.size()) {
        return arrNode.get(idx);
      }
      return null;
    }
    return null;
  }

  /** 获取指定位置的值。 */
  private static Object getValueAt(PathResolution resolution, String lastSegment) {
    if (resolution.parent instanceof ObjectNode objNode) {
      JsonNode child = objNode.get(lastSegment);
      return child != null && !child.isNull() ? child.asValue() : null;
    } else if (resolution.parent instanceof ArrayNode arrNode) {
      int idx = parseIndex(lastSegment, arrNode.size());
      JsonNode child = arrNode.get(idx);
      return child != null && !child.isNull() ? child.asValue() : null;
    }
    return null;
  }

  /** 解析数组索引。 */
  private static int parseIndex(String segment, int arraySize) {
    if ("-".equals(segment)) {
      return arraySize;
    }
    try {
      int idx = Integer.parseInt(segment);
      if (idx < 0 || idx > arraySize) {
        throw new JsonException("Array index out of bounds: " + segment);
      }
      return idx;
    } catch (NumberFormatException e) {
      throw new JsonException("Invalid array index: " + segment);
    }
  }

  // ==================== 工具方法 ====================

  private static String getString(Map<String, Object> map, String key) {
    Object val = map.get(key);
    return val != null ? val.toString() : null;
  }
}
