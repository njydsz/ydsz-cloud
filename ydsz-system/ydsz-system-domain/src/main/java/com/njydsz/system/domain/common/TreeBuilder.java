package com.njydsz.system.domain.common;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * 树形结构构建器。
 *
 * <p>将扁平列表转换为树形结构，支持任意实现了 {@link TreeNode} 接口的节点类型。
 *
 * <p>使用示例：
 *
 * <pre>{@code
 * List<DictItemVO> flatList = dictItemService.listByTypeCode("region");
 * List<DictItemVO> tree = TreeBuilder.build(flatList, "0");
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class TreeBuilder {

  private TreeBuilder() {
    // 工具类禁止实例化
  }

  /**
   * 将扁平列表构建为树形结构。
   *
   * @param flatList 扁平节点列表
   * @param rootParentId 根节点的父级 ID（如 "0" 或 null）
   * @param <ID> 节点 ID 类型
   * @param <T> 节点类型
   * @return 树形结构根节点列表
   */
  public static <ID, T extends TreeNode<ID>> List<T> build(List<T> flatList, ID rootParentId) {
    if (flatList == null || flatList.isEmpty()) {
      return new ArrayList<>();
    }

    // 按 parentId 分组
    Map<ID, List<T>> childrenMap = new LinkedHashMap<>();
    for (T node : flatList) {
      ID parentId = node.getParentId();
      childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
    }

    // 递归构建树
    return buildTree(childrenMap, rootParentId);
  }

  /**
   * 将扁平列表构建为树形结构（使用自定义父级 ID 提取器）。
   *
   * <p>适用于节点未实现 {@link TreeNode} 接口的场景，通过函数提取父级 ID 和子节点 ID。
   *
   * @param flatList 扁平列表
   * @param rootParentId 根节点的父级 ID
   * @param idExtractor 节点 ID 提取函数
   * @param parentIdExtractor 父级 ID 提取函数
   * @param childrenSetter 子节点设置函数
   * @param <T> 节点类型
   * @param <ID> 节点 ID 类型
   * @return 树形结构根节点列表
   */
  public static <T, ID> List<T> build(
      List<T> flatList,
      ID rootParentId,
      Function<T, ID> idExtractor,
      Function<T, ID> parentIdExtractor,
      BiConsumer<T, List<T>> childrenSetter) {
    if (flatList == null || flatList.isEmpty()) {
      return new ArrayList<>();
    }

    // 按 parentId 分组
    Map<ID, List<T>> childrenMap = new LinkedHashMap<>();
    for (T node : flatList) {
      ID parentId = parentIdExtractor.apply(node);
      childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>()).add(node);
    }

    // 递归构建树
    return buildTreeGeneric(childrenMap, rootParentId, idExtractor, childrenSetter);
  }

  private static <ID, T extends TreeNode<ID>> List<T> buildTree(
      Map<ID, List<T>> childrenMap, ID parentId) {
    List<T> children = childrenMap.get(parentId);
    if (children == null) {
      return new ArrayList<>();
    }

    for (T child : children) {
      List<T> subChildren = buildTree(childrenMap, child.getId());
      child.setChildren(subChildren);
    }

    return children;
  }

  private static <T, ID> List<T> buildTreeGeneric(
      Map<ID, List<T>> childrenMap,
      ID parentId,
      Function<T, ID> idExtractor,
      BiConsumer<T, List<T>> childrenSetter) {
    List<T> children = childrenMap.get(parentId);
    if (children == null) {
      return new ArrayList<>();
    }

    for (T child : children) {
      List<T> subChildren = buildTreeGeneric(childrenMap, idExtractor.apply(child), idExtractor, childrenSetter);
      childrenSetter.accept(child, subChildren);
    }

    return children;
  }

  /** 二元函数接口（Java 8 兼容）。 */
  @FunctionalInterface
  public interface BiConsumer<T, U> {
    void accept(T t, U u);
  }
}
