package com.njydsz.literule.server.spi;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 规则分类目录树节点（独立 POJO）。
 *
 * <p>基于 id/parentId/children 的纯 POJO 树节点，通过 {@link
 * com.njydsz.common.domain.tree.TreeBuilder#buildSimple} 构建，自动填充 {@code level}/{@code path} 元数据。
 *
 * <p>扩展业务字段（name/ruleCount/owners），由 {@link RuleCategoryProvider#buildTree()} 返回，供前端展示规则分类目录树。
 *
 * @since 26.10.01
 * @author ydsz-team
 * @see com.njydsz.common.domain.tree.TreeBuilder
 */
@Data
@EqualsAndHashCode(exclude = {"children"})
public class CategoryTreeNode implements Serializable {

  /** 集合初始容量 */
  private static final int COLLECTION_CAPACITY_4 = 4;

  @Serial private static final long serialVersionUID = 1L;

  /** 节点唯一标识 */
  private String id;

  /** 父节点 ID；为 null 表示根节点 */
  private String parentId;

  /** 子节点列表 */
  private List<CategoryTreeNode> children = new ArrayList<>(COLLECTION_CAPACITY_4);

  /** 排序字段（同级节点升序，null 排最后） */
  private Integer sort;

  /** 节点层级深度（根节点=1，由 TreeBuilder.buildSimple 外部填充） */
  private Integer level;

  /** 节点路径（由 TreeBuilder.buildSimple 外部填充） */
  private String path;

  /** 分类节点名称（展示用） */
  private String name;

  /** 该分类下的规则数量（含下级聚合，仅作统计展示用） */
  private int ruleCount;

  /** 该分类下的责任人列表 */
  private List<String> owners = new ArrayList<>(COLLECTION_CAPACITY_4);

  /** 是否根节点（true=顶层分类，用于前端高亮展示） */
  private boolean root;

  public CategoryTreeNode() {}

  /**
   * 添加子节点。
   *
   * @param child 子节点
   * @return 当前节点（链式调用）
   */
  public CategoryTreeNode addChild(CategoryTreeNode child) {
    if (children == null) {
      children = new ArrayList<>(COLLECTION_CAPACITY_4);
    }
    children.add(child);
    return this;
  }

  /**
   * 添加多个子节点。
   *
   * @param childList 子节点列表
   * @return 当前节点（链式调用）
   */
  public CategoryTreeNode addChildren(List<CategoryTreeNode> childList) {
    if (children == null) {
      children = new ArrayList<>(COLLECTION_CAPACITY_4);
    }
    if (childList != null && !childList.isEmpty()) {
      children.addAll(childList);
    }
    return this;
  }

  /**
   * 判断是否为叶子节点（动态计算）。
   *
   * @return children 为 null 或空时返回 true
   */
  public boolean isLeaf() {
    return children == null || children.isEmpty();
  }

  /**
   * 判断是否为根节点（parentId == null）。
   *
   * @return parentId 为 null 返回 true
   */
  public boolean isRootNode() {
    return parentId == null;
  }

  /**
   * 获取直接子节点数量。
   *
   * @return 子节点数量
   */
  public int getChildCount() {
    return children != null ? children.size() : 0;
  }

  /**
   * 判断是否包含指定 ID 的子节点。
   *
   * @param childId 子节点 ID
   * @return 包含返回 true
   */
  public boolean containsChild(String childId) {
    if (children == null) {
      return false;
    }
    return children.stream().anyMatch(child -> Objects.equals(child.getId(), childId));
  }

  /**
   * 累加本目录节点的规则计数（构建目录树时每挂接一条规则调用一次）。
   *
   * <p>仅作统计聚合，不影响规则本身的注册与评估；计数仅供前端目录树展示用。
   */
  public void increaseRuleCount() {
    this.ruleCount++;
  }

  /**
   * 迭代查找指定节点（避免递归栈溢出）。
   *
   * @param targetId 目标节点 ID
   * @return 找到返回节点，否则返回 null
   */
  public CategoryTreeNode findById(String targetId) {
    if (Objects.equals(this.id, targetId)) {
      return this;
    }
    if (children == null || children.isEmpty()) {
      return null;
    }
    Deque<CategoryTreeNode> stack = new ArrayDeque<>();
    stack.push(this);
    while (!stack.isEmpty()) {
      CategoryTreeNode node = stack.pop();
      if (Objects.equals(node.getId(), targetId)) {
        return node;
      }
      List<CategoryTreeNode> nodeChildren = node.getChildren();
      if (nodeChildren != null && !nodeChildren.isEmpty()) {
        for (int i = nodeChildren.size() - 1; i >= 0; i--) {
          stack.push(nodeChildren.get(i));
        }
      }
    }
    return null;
  }
}
