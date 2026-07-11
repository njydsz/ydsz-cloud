package com.njydsz.pmis.common.entity.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 树节点接口 —— 配合 {@link TreeBuilder} 使用。
 * <p>
 * 对标 remi-comm TreeNode，定义树节点的通用契约。
 * </p>
 *
 * @param <ID> 节点标识类型
 * @author njydsz
 * @since 1.0.0
 */
public interface TreeNode<ID extends Serializable> {

    /**
     * 获取节点标识。
     */
    ID getId();

    /**
     * 获取父节点标识（根节点返回 null）。
     */
    ID getParentId();

    /**
     * 获取子节点列表。
     */
    List<? extends TreeNode<ID>> getChildren();

    /**
     * 添加子节点。
     *
     * @param child 子节点
     */
    @SuppressWarnings("unchecked")
    default void addChild(TreeNode<ID> child) {
        if (getChildren() == null) {
            // 子类需确保 children 可变列表可初始化
            throw new UnsupportedOperationException("TreeNode.addChild requires a mutable children list. Override getChildren() to return a mutable list.");
        }
        ((List<TreeNode<ID>>) getChildren()).add(child);
    }
}
