package com.njydsz.pmis.common.entity.tree;

import java.io.Serializable;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 树形构建器 —— 将扁平列表构建为树形结构。
 * <p>
 * 对标 remi-comm TreeBuilder，支持：
 * <ul>
 *   <li>通用树构建（基于 ID / parentId 映射）</li>
 *   <li>排序（按 order 字段或自定义 Comparator）</li>
 *   <li>过滤（构建前过滤节点）</li>
 *   <li>查找路径（从根到指定节点）</li>
 * </ul>
 * </p>
 *
 * @param <T>  树节点类型
 * @param <ID> 节点标识类型
 * @author njydsz
 * @since 1.0.0
 */
public class TreeBuilder<T extends TreeNode<ID>, ID extends Serializable> {

    private final Function<T, ID> idExtractor;
    private final Function<T, ID> parentIdExtractor;
    private Comparator<T> comparator;

    public TreeBuilder(Function<T, ID> idExtractor, Function<T, ID> parentIdExtractor) {
        this.idExtractor = idExtractor;
        this.parentIdExtractor = parentIdExtractor;
    }

    /**
     * 设置排序比较器。
     */
    public TreeBuilder<T, ID> sorted(Comparator<T> comparator) {
        this.comparator = comparator;
        return this;
    }

    /**
     * 构建树形结构。
     *
     * @param nodes 扁平节点列表
     * @return 根节点列表（每个根节点包含其子树）
     */
    public List<T> build(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }

        // 按 ID 索引
        Map<ID, T> idMap = nodes.stream()
                .collect(Collectors.toMap(idExtractor, Function.identity(), (a, b) -> a));

        List<T> roots = new ArrayList<>();

        for (T node : nodes) {
            ID parentId = parentIdExtractor.apply(node);
            if (parentId == null || !idMap.containsKey(parentId)) {
                roots.add(node);
            } else {
                T parent = idMap.get(parentId);
                parent.addChild(node);
            }
        }

        // 排序
        if (comparator != null) {
            sortTree(roots, comparator);
        }

        return roots;
    }

    /**
     * 查找从根到指定节点的路径。
     *
     * @param nodes    扁平节点列表
     * @param targetId 目标节点 ID
     * @return 路径列表（从根到目标），如果未找到返回空列表
     */
    public List<T> findPath(List<T> nodes, ID targetId) {
        if (nodes == null || targetId == null) {
            return Collections.emptyList();
        }

        Map<ID, T> idMap = nodes.stream()
                .collect(Collectors.toMap(idExtractor, Function.identity(), (a, b) -> a));

        Map<ID, ID> parentMap = new HashMap<>();
        for (T node : nodes) {
            ID nodeId = idExtractor.apply(node);
            ID parentId = parentIdExtractor.apply(node);
            parentMap.put(nodeId, parentId);
        }

        // 回溯路径
        LinkedList<T> path = new LinkedList<>();
        ID currentId = targetId;
        Set<ID> visited = new HashSet<>();

        while (currentId != null && idMap.containsKey(currentId) && visited.add(currentId)) {
            path.addFirst(idMap.get(currentId));
            currentId = parentMap.get(currentId);
        }

        return path;
    }

    private void sortTree(List<T> nodes, Comparator<T> cmp) {
        nodes.sort(cmp);
        for (T node : nodes) {
            if (node.getChildren() != null && !node.getChildren().isEmpty()) {
                sortTree(node.getChildren(), cmp);
            }
        }
    }
}
