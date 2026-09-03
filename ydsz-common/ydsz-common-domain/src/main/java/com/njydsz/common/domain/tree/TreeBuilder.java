package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * 树形结构构建器（轻量版）。
 *
 * <p>提供两类能力：
 *
 * <ul>
 *   <li>{@link #build()}：将继承 {@link TreeNode} 的实体列表构建为树（O(n)，HashMap 索引）
 *   <li>{@link #buildSimple(List, Function, Function, BiConsumer, Function)}：静态便捷方法， 支持不继承 {@link
 *       TreeNode} 的 VO 类构建树（业务模块 Menu/Department 使用）
 * </ul>
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * List<Menu> allMenus = menuMapper.selectList(null);
 * List<Menu> tree = new TreeBuilder<>(0L, allMenus).build();
 *
 * // VO 类无需继承 TreeNode：
 * List<MenuVO> treeVo = TreeBuilder.buildSimple(
 *         flatList,
 *         MenuVO::getId,
 *         MenuVO::getParentId,
 *         MenuVO::setChildren,
 *         MenuVO::getSort);
 * }</pre>
 *
 * @param <T> 继承自TreeNode的具体类型
 * @param <ID> ID类型
 * @author ydsz-team
 * @since 26.09.01
 * @since 26.09.01 大幅精简：移除缓存（DCL/ReentrantLock/dirty）、链式配置
 *     （autoCalcLevel/autoBuildPath/multiRoot/idExtractor）、统计 API
 *     （countNodes/getTreeDepth/countLeafNodes 等）与复制/移动 API。 业务侧仅需 build() 与 buildSimple()，复杂度与 ROI
 *     严重不匹配。
 * @see TreeNode
 */
public class TreeBuilder<T extends TreeNode<T, ID>, ID extends Serializable> {

  /** 排序比较器：默认 sort 字段升序，null 值排在最后（作为默认值使用） */
  private static final Comparator<TreeNode<?, ?>> DEFAULT_SORT_COMPARATOR =
      Comparator.comparing(TreeNode::getSort, Comparator.nullsLast(Integer::compareTo));

  // DEFAULT_SORT_COMPARATOR 类型为 Comparator<TreeNode<?, ?>>，无法在编译期验证与 Comparator<T> 的一致性；逻辑上所有 TreeNode 子类均可比较
  private static <T extends TreeNode<T, ?>> Comparator<T> defaultSortComparator() {
    return (Comparator<T>) DEFAULT_SORT_COMPARATOR;
  }

  /** 虚拟根节点 ID（父 ID 等于该值的节点视为根） */
  private final ID rootId;

  /** 扁平节点列表 */
  private final List<T> nodeList;

  /** 排序比较器（可选）；未设置时使用默认的 sort 字段升序 + nullsLast */
  private final Comparator<T> sortComparator;

  /**
   * 构造树构建器（使用默认排序比较器 sort 字段升序 + nullsLast）。
   *
   * @param rootId 虚拟根节点 ID；为 null 时以 {@code parentId == null} 判定根节点
   * @param nodeList 扁平节点列表，非空
   */
  public TreeBuilder(ID rootId, List<T> nodeList) {
    this(rootId, nodeList, null);
  }

  /**
   * 构造树构建器（自定义排序比较器）。
   *
   * <p>允许调用方指定自定义排序逻辑（如按 orderNum 排序、按 name 排序等）， 不局限于 {@link TreeNode#getSort()} 字段。
   *
   * @param rootId 虚拟根节点 ID；为 null 时以 {@code parentId == null} 判定根节点
   * @param nodeList 扁平节点列表，非空
   * @param sortComparator 排序比较器（可为 null，则使用默认 sort 字段升序 + nullsLast）
   * @since 26.09.01
   */
  public TreeBuilder(ID rootId, List<T> nodeList, Comparator<T> sortComparator) {
    this.rootId = rootId;
    this.nodeList = Objects.requireNonNull(nodeList, "nodeList不能为null");
    this.sortComparator = sortComparator;
  }

  /**
   * 构造树构建器（单根模式：以 {@code parentId == null} 判定根节点，使用默认排序比较器）
   *
   * @param nodeList 扁平节点列表，非空
   */
  public TreeBuilder(List<T> nodeList) {
    this(null, nodeList, null);
  }

  /**
   * 构造树构建器（单根模式 + 自定义排序比较器）。
   *
   * @param nodeList 扁平节点列表，非空
   * @param sortComparator 排序比较器（可为 null，则使用默认 sort 字段升序 + nullsLast）
   * @since 26.09.01
   */
  public TreeBuilder(List<T> nodeList, Comparator<T> sortComparator) {
    this(null, nodeList, sortComparator);
  }

  /**
   * 构建树形结构（O(n)）。
   *
   * <p>仅对节点列表做只读处理（不修改传入列表），返回新构建的根节点列表。 每次调用独立构建，不维护缓存。
   *
   * @return 构建完成的根节点列表（无节点时返回空列表）
   */
  public List<T> build() {
    if (nodeList.isEmpty()) {
      return new ArrayList<>(0);
    }
    Map<ID, T> nodeMap = new HashMap<>(nodeList.size());
    for (T node : nodeList) {
      nodeMap.put(node.getId(), node);
    }
    List<T> roots = new ArrayList<>(nodeList.size());
    Comparator<T> comparator = sortComparator != null ? sortComparator : defaultSortComparator();
    for (T node : nodeList) {
      ID parentId = node.getParentId();
      if (parentId == null || parentId.equals(rootId)) {
        roots.add(node);
      } else {
        T parent = nodeMap.get(parentId);
        if (parent != null) {
          parent.addChild(node);
        }
      }
    }
    roots.sort(comparator);
    return roots;
  }

  /**
   * 静态便捷方法，支持不继承 TreeNode 的 VO 类构建树。
   *
   * @param flatList 扁平列表
   * @param idExtractor ID 提取器
   * @param parentIdExtractor 父 ID 提取器
   * @param childrenSetter 子节点设置器
   * @param sortExtractor 排序字段提取器
   * @param <T> VO 类型
   * @param <ID> ID 类型
   * @return 构建完成的根节点列表
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
    Map<ID, T> nodeMap = new HashMap<>(flatList.size());
    for (T node : flatList) {
      nodeMap.put(idExtractor.apply(node), node);
    }
    List<T> roots = new ArrayList<>(flatList.size());
    Comparator<T> comparator = Comparator.comparing(
        sortExtractor, Comparator.nullsLast(Integer::compareTo));
    for (T node : flatList) {
      ID parentId = parentIdExtractor.apply(node);
      if (parentId == null) {
        roots.add(node);
      } else {
        T parent = nodeMap.get(parentId);
        if (parent != null) {
          TreeNode<?, ?> treeNode = (TreeNode<?, ?>) parent;
          List<Object> children = treeNode.getChildren() != null
              ? new ArrayList<>(treeNode.getChildren())
              : new ArrayList<>();
          children.add(node);
          childrenSetter.accept(parent, (List<T>) (List<?>) children);
        }
      }
    }
    roots.sort(comparator);
    return roots;
  }
}
