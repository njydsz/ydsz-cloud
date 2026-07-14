package com.njydsz.pmis.common.domain.tree;

import java.io.Serializable;
import java.util.List;

/**
 * 树节。懒加载提供者接。
 *
 * <p>定义了懒加载树节。子节。的契约，用于按需加载树结构数据。
 * 适用于数据量较大、不适合一次性加载全部节。的场景。
 *
 * <p><b>使用示例：</b>
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
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 */
public interface TreeNodeProvider<T extends TreeNode<T, ID>, ID extends Serializable> {

    /**
     * 获取根节。列表
     *
     * <p>返回树的最顶层节。（即没有父节。的节。）。
     *
     * @return 根节。列表
     */
    List<T> getRootNodes();

    /**
     * 根据父节。ID获取子节。列表
     *
     * <p>按需加载指定父节。的直接子节。。
     *
     * @param parentId 父节。ID
     * @return 子节。列表，如果没有子节。返回空列表
     */
    List<T> getChildren(ID parentId);
}
