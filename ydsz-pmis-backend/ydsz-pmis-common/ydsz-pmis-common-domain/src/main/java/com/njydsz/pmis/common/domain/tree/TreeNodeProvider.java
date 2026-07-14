package com.njydsz.pmis.common.domain.tree;

import java.io.Serializable;
import java.util.List;

/**
 * 树节点懒加载提供者接�?
 *
 * <p>定义了懒加载树节点子节点的契约，用于按需加载树结构数据�?
 * 适用于数据量较大、不适合一次性加载全部节点的场景�?
 *
 * <p><b>使用示例�?/b>
 * <pre>{@code
 * TreeNodeProvider<Menu, Long> provider = new TreeNodeProvider<>() {
 *     public List<Menu> getRootNodes() {
 *         return menuMapper.selectRootNodes();
 *     }
 *
 *     public List<Menu> getChildren(Long parentId) {
 *         return menuMapper.selectByParentId(parentId);
 *     }
 * };
 *
 * List<LazyTreeNode<Menu, Long>> tree = TreeBuilder.buildLazy(provider);
 * }</pre>
 *
 * @param <T>  继承自TreeNode的具体类�?
 * @param <ID> ID类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 */
public interface TreeNodeProvider<T extends TreeNode<T, ID>, ID extends Serializable> {

    /**
     * 获取根节点列�?
     *
     * <p>返回树的最顶层节点（即没有父节点的节点）�?
     *
     * @return 根节点列�?
     */
    List<T> getRootNodes();

    /**
     * 根据父节点ID获取子节点列�?
     *
     * <p>按需加载指定父节点的直接子节点�?
     *
     * @param parentId 父节点ID
     * @return 子节点列表，如果没有子节点返回空列表
     */
    List<T> getChildren(ID parentId);
}
