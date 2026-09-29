package com.njydsz.common.domain.tree;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 树形结构构建器（静态工具类）。
 *
 * <p>提供统一的 {@link #buildSimple(List, Function, Function, BiConsumer, Function)} 入口，
 * 支持不继承任何框架基类的纯 POJO 构建树（O(n)，HashMap 索引）。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * List<MenuVO> treeVo = TreeBuilder.buildSimple(
 *         flatList,
 *         MenuVO::getId,
 *         MenuVO::getParentId,
 *         MenuVO::setChildren,
 *         MenuVO::getSort);
 * }</pre>
 *
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.30 移除 build() / buildLazy() / TreeNode 继承模式，仅保留 buildSimple() 静态入口（参见 YDIZ-DOMAIN-002）
 */
public final class TreeBuilder {

  private TreeBuilder() {
    // 静态工具类，禁止实例化
  }

  /**
   * 静态便捷方法，支持不继承任何框架基类的纯 POJO 构建树。
   *
   * <p>算法：O(n) 一次遍历。先用 Map 累积每个节点的子节点列表，
   * 最后统一通过 {@code childrenSetter} 设置到节点上。
   *
   * @param flatList 扁平列表
   * @param idExtractor ID 提取器
   * @param parentIdExtractor 父 ID 提取器
   * @param childrenSetter 子节点设置器
   * @param sortExtractor 排序字段提取器
   * @param <T> VO 类型
   * @param <ID> ID 类型
   * @return 构建完成的根节点列表（parentId 为 null 的节点视为根）
   */
  public static <T, ID> List<T> buildSimple(
      List<T> flatList,
      Function<T, ID> idExtractor,
      Function<T, ID> parentIdExtractor,
      BiConsumer<T, List<T>> childrenSetter,
      Function<T, Integer> sortExtractor) {
    if (flatList == null || flatList.isEmpty()) {
      return new ArrayList<>(0);
    }
    // 第一阶段：id → 节点索引
    Map<ID, T> nodeMap = new HashMap<>(flatList.size());
    for (T node : flatList) {
      nodeMap.put(idExtractor.apply(node), node);
    }
    // 第二阶段：父 id → 子节点列表 累积
    Map<ID, List<T>> childrenMap = new HashMap<>();
    List<T> roots = new ArrayList<>(flatList.size());
    for (T node : flatList) {
      ID parentId = parentIdExtractor.apply(node);
      if (parentId == null) {
        roots.add(node);
      } else {
        childrenMap.computeIfAbsent(parentId, k -> new ArrayList<>(4)).add(node);
      }
    }
    // 第三阶段：统一设置 children
    for (Map.Entry<ID, List<T>> entry : childrenMap.entrySet()) {
      T parent = nodeMap.get(entry.getKey());
      if (parent != null) {
        childrenSetter.accept(parent, entry.getValue());
      }
    }
    // 根节点排序
    Comparator<T> comparator = Comparator.comparing(
        sortExtractor, Comparator.nullsLast(Integer::compareTo));
    roots.sort(comparator);
    return roots;
  }
}
