package com.njydsz.literule.server.spi;

import java.util.List;

import com.njydsz.literule.domain.dto.RuleDefinitionDTO;

/**
 * 规则目录树提供者 SPI
 *
 * <p>由消费方（如 project 模块）提供实现，提供基于 category_path 的多级目录树构建、 按路径过滤规则、按 Owner 筛选等能力。将原有 {@code
 * RuleCategoryTreeService} 的能力抽象为 SPI， 避免 literule 模块直接依赖 project 模块。
 *
 * <p>目录树节点使用 {@link CategoryTreeNode}（基于 common-domain {@link
 * com.njydsz.common.domain.tree.TreeNode}），构建时使用 {@link com.njydsz.common.domain.tree.TreeBuilder}。
 *
 * @since 26.09.01
 * @author ydsz-team
 */
public interface RuleCategoryProvider {

  /**
   * 构建规则目录树
   *
   * <p>推荐使用 {@link com.njydsz.common.domain.tree.TreeBuilder} 构建：
   *
   * <pre>{@code
   * List<CategoryTreeNode> flatNodes = ...;
   * List<CategoryTreeNode> tree = new TreeBuilder<>("0", flatNodes).build();
   * }</pre>
   *
   * @return 树根（虚拟根，name="ROOT"），children 为一级分类
   */
  CategoryTreeNode buildTree();

  /**
   * 按分类路径前缀查询规则（返回 API Definition）
   *
   * @param pathPrefix 分类路径前缀，为空时返回全部
   * @return 规则定义列表
   */
  List<RuleDefinitionDTO> listDefinitionsByCategoryPath(String pathPrefix);

  /**
   * 按 Owner 查询规则（返回 API Definition）
   *
   * @param owner 责任人
   * @return 规则定义列表
   */
  List<RuleDefinitionDTO> listDefinitionsByOwner(String owner);
}
