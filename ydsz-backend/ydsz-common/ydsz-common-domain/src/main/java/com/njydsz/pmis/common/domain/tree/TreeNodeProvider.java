package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.List;

/**
 * 树节点懒加载提供者接口
 *
 * <p>定义了懒加载树节点子节点的契约，用于按需加载树结构数据。
 * 适用于数据量较大、不适合一次性加载全部节点的场景。
 *
 * <p><b>批量加载支持：</b>
 * 实现类可选择性覆写 {@link #getChildrenBatch(Serializable, int, int)} 和
 * {@link #countChildren(Serializable)} 方法以支持分批加载。
 * 默认实现退化为全量加载后截取，不推荐用于大数据量场景。
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
 *
 *     // 可选：支持批量加载
 *     public List<Menu> getChildrenBatch(Long parentId, int offset, int limit) {
 *         return menuMapper.selectByParentId(parentId, offset, limit);
 *     }
 *
 *     public int countChildren(Long parentId) {
 *         return menuMapper.countByParentId(parentId);
 *     }
 * };
 *
 * List<LazyTreeNode<Menu, Long>> tree = TreeBuilder.buildLazy(provider);
 * }</pre>
 *
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
public interface TreeNodeProvider<T extends TreeNode<T, ID>, ID extends Serializable> {

    /**
     * 获取根节点列表
     *
     * <p>返回树的最顶层节点（即没有父节点的节点）。
     *
     * @return 根节点列表
     */
    List<T> getRootNodes();

    /**
     * 根据父节点ID获取子节点列表
     *
     * <p>按需加载指定父节点的直接子节点。
     *
     * @param parentId 父节点ID
     * @return 子节点列表，如果没有子节点返回空列表
     */
    List<T> getChildren(ID parentId);

    /**
     * 根据父节点ID分批获取子节点列表
     *
     * <p>默认实现为全量加载后截取，性能较差。
     * 支持批量加载的实现类应覆写此方法以利用数据库分页。
     *
     * @param parentId 父节点ID
     * @param offset   偏移量（从0开始）
     * @param limit    每批最大数量
     * @return 子节点列表
     */
    default List<T> getChildrenBatch(ID parentId, int offset, int limit) {
        List<T> all = getChildren(parentId);
        if (all == null || all.isEmpty()) {
            return List.of();
        }
        int fromIndex = Math.min(offset, all.size());
        int toIndex = Math.min(offset + limit, all.size());
        return all.subList(fromIndex, toIndex);
    }

    /**
     * 统计指定父节点的子节点总数
     *
     * <p>默认实现为全量加载后取 size，性能较差。
     * 支持批量加载的实现类应覆写此方法以利用数据库 COUNT 查询。
     *
     * @param parentId 父节点ID
     * @return 子节点总数
     */
    default int countChildren(ID parentId) {
        List<T> all = getChildren(parentId);
        return all == null ? 0 : all.size();
    }
}
