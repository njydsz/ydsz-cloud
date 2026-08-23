package com.njydsz.literule.server.spi;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.domain.tree.TreeNode;

/**
 * 规则分类目录树节点。
 *
 * <p>继承 {@link TreeNode} 获得通用树形结构能力（id/parentId/children/level/path/leaf），
 *     同时扩展业务字段（name/ruleCount/owners）。 由 {@link RuleCategoryProvider#buildTree()} 返回，供前端展示规则分类目录树。
 *
 * <p>构建时使用 {@code TreeBuilder<CategoryTreeNode, String>} 的 
  * {@link com.njydsz.common.domain.tree.TreeBuilder#build()} 方法。
 *
 * @since 1.0.0
 * @author ydsz-team
 * @see TreeNode
 * @see com.njydsz.common.domain.tree.TreeBuilder
 */
@Data
@EqualsAndHashCode(callSuper = true)
@SuperBuilder
public class CategoryTreeNode extends TreeNode<CategoryTreeNode, String> implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 分类节点名称（展示用） */
  private String name;

  /** 该分类下的规则数量（含下级聚合，仅作统计展示用） */
  private int ruleCount;

  /** 该分类下的责任人列表 */
  private List<String> owners = new ArrayList<>();

  /** 是否根节点（true=顶层分类，用于前端高亮展示） */
  private boolean root;

  public CategoryTreeNode() {}

  /**
   * 累加本目录节点的规则计数（构建目录树时每挂接一条规则调用一次）。
   *
   * <p>仅作统计聚合，不影响规则本身的注册与评估；计数仅供前端目录树展示用。
   */
  public void increaseRuleCount() {
    this.ruleCount++;
  }
}
