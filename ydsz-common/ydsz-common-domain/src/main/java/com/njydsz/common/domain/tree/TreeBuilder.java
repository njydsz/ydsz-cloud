package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 树形结构构建器（轻量版）。
 *
 * <p>提供两类能力：
 * <ul>
 *   <li>{@link #build()}：将继承 {@link TreeNode} 的实体列表构建为树（O(n)，HashMap 索引）</li>
 *   <li>{@link #buildSimple(List, Function, Function, BiConsumer, Function)}：静态便捷方法，
 *       支持不继承 {@link TreeNode} 的 VO 类构建树（业务模块 Menu/Department 使用）</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
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
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.4.0 大幅精简：移除缓存（DCL/ReentrantLock/dirty）、链式配置
 *              （autoCalcLevel/autoBuildPath/multiRoot/idExtractor）、统计 API
 *              （countNodes/getTreeDepth/countLeafNodes 等）与复制/移动 API。
 *              业务侧仅需 build() 与 buildSimple()，复杂度与 ROI 严重不匹配。
 *
 * @see TreeNode
 */
public class TreeBuilder<T extends TreeNode<T, ID>, ID extends Serializable> {

    /** 排序比较器：默认 sort 字段升序，null 值排在最后（作为默认值使用） */
    private static final Comparator<TreeNode<?, ?>> DEFAULT_SORT_COMPARATOR = Comparator.comparing(
            TreeNode::getSort,
            Comparator.nullsLast(Integer::compareTo));

    @SuppressWarnings("unchecked")
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
     * @param rootId   虚拟根节点 ID；为 null 时以 {@code parentId == null} 判定根节点
     * @param nodeList 扁平节点列表，非空
     */
    public TreeBuilder(ID rootId, List<T> nodeList) {
        this(rootId, nodeList, null);
    }

    /**
     * 构造树构建器（自定义排序比较器）。
     *
     * <p>允许调用方指定自定义排序逻辑（如按 orderNum 排序、按 name 排序等），
     * 不局限于 {@link TreeNode#getSort()} 字段。
     *
     * @param rootId   虚拟根节点 ID；为 null 时以 {@code parentId == null} 判定根节点
     * @param nodeList 扁平节点列表，非空
     * @param sortComparator 排序比较器（可为 null，则使用默认 sort 字段升序 + nullsLast）
     * @since 1.6.0
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
     * @since 1.6.0
     */
    public TreeBuilder(List<T> nodeList, Comparator<T> sortComparator) {
        this(null, nodeList, sortComparator);
    }

    /**
     * 构建树形结构（O(n)）。
     *
     * <p>仅对节点列表做只读处理（不修改传入列表），返回新构建的根节点列表。
     * 每次调用独立构建，不维护缓存。
     *
     * @return 构建完成的根节点列表（无节点时返回空列表）
     */
    public List<T> build() {
        if (nodeList.isEmpty()) {
            return new ArrayList<>();
        }

        // 1. 建立 ID -> 节点 索引（跳过 null ID 节点）
        Map<ID, T> nodeMap = new HashMap<>(nodeList.size());
        for (T node : nodeList) {
            ID id = node.getId();
            if (id != null) {
                nodeMap.put(id, node);
            }
        }

        // 2. 构建父子关系 + 自动层级计算；
        //    同时收集所有已知的 nodeId 到 Set，isRootNode 使用 O(1) 查找替代 O(n) 遍历
        Set<ID> knownIds = new HashSet<>(nodeMap.keySet());
        for (T node : nodeList) {
            attachOrPromoteToRoot(node, nodeMap);
        }

        // 3. 筛选根节点并排序（isRootNode 使用 O(1) knownIds 查询）
        //    注意：必须收集到可变 ArrayList——Stream.toList() 返回不可变列表，
        //    后续 sortSubTree 对其 sort() 会抛 UnsupportedOperationException。
        List<T> roots = nodeList.stream()
                .filter(node -> node.getId() != null && isRootNode(node, knownIds))
                .collect(Collectors.toCollection(ArrayList::new));
        sortSubTree(roots);
        return new ArrayList<>(roots);
    }

    /**
     * 将节点挂到父节点下（含子节点列表懒初始化与层级计算）；父节点缺失时按根节点处理（多根容错）。
     *
     * @param node    当前节点
     * @param nodeMap ID -> 节点 索引
     */
    private void attachOrPromoteToRoot(T node, Map<ID, T> nodeMap) {
        ID parentId = node.getParentId();
        T parent = parentId == null ? null : nodeMap.get(parentId);
        if (parent == null) {
            node.setLevel(TreeNode.ROOT_LEVEL);
            return;
        }
        List<T> children = parent.getChildren();
        if (children == null) {
            children = new ArrayList<>();
            parent.setChildren(children);
        }
        if (!children.contains(node)) {
            children.add(node);
            // 一旦挂上子节点即为非叶子（children 字段默认为空 ArrayList，非 null，
            // 因此不能依赖 children == null 判断，需在真正添加子节点时置位）
            parent.setLeaf(false);
        }
        Integer parentLevel = parent.getLevel();
        node.setLevel(parentLevel != null ? parentLevel + 1 : TreeNode.ROOT_LEVEL + 1);
    }

    /**
     * 判断节点是否为根节点（O(1) 复杂度，基于预构建的 knownIds 集合）。
     *
     * <p>根判定规则（与 {@link #build()} 步骤2 的多根容错语义一致）：
     * <ul>
     *   <li>{@code parentId == null}：天然根</li>
     *   <li>指定了 {@link #rootId} 且 {@code parentId == rootId}：虚拟根下的根</li>
     *   <li>父节点 ID 不在已知节点集合中（孤儿节点）：视为根，避免节点被静默丢弃</li>
     * </ul>
     *
     * @param node      待判断节点
     * @param knownIds  所有已知节点 ID 集合（用于 O(1) 父节点存在性判断）
     * @return 是根节点返回 true
     */
    private boolean isRootNode(T node, Set<ID> knownIds) {
        ID parentId = node.getParentId();
        if (rootId == null) {
            return parentId == null || !knownIds.contains(parentId);
        }
        return parentId == null || Objects.equals(rootId, parentId)
                || !knownIds.contains(parentId);
    }

    /**
     * 根据 ID 查找节点
     *
     * @param id 目标节点 ID
     * @return 找到返回节点，否则返回 null
     */
    public T findById(ID id) {
        if (id == null) {
            return null;
        }
        for (T node : nodeList) {
            if (Objects.equals(node.getId(), id)) {
                return node;
            }
        }
        return null;
    }

    /**
     * 获取指定节点的所有后代节点（迭代式，避免递归栈溢出）
     *
     * @param node 起始节点
     * @return 后代节点列表（不含起始节点）
     */
    public List<T> getDescendants(T node) {
        List<T> descendants = new ArrayList<>();
        if (node == null || node.getChildren() == null || node.getChildren().isEmpty()) {
            return descendants;
        }
        Deque<T> stack = new ArrayDeque<>(node.getChildren());
        while (!stack.isEmpty()) {
            T current = stack.pop();
            descendants.add(current);
            List<T> currentChildren = current.getChildren();
            if (currentChildren != null && !currentChildren.isEmpty()) {
                stack.addAll(currentChildren);
            }
        }
        return descendants;
    }

    /**
     * 获取指定节点的所有祖先节点（从父节点到根节点）
     *
     * @param node 起始节点
     * @return 祖先节点列表
     */
    public List<T> getAncestors(T node) {
        List<T> ancestors = new ArrayList<>();
        if (node == null) {
            return ancestors;
        }
        ID parentId = node.getParentId();
        int depth = 0;
        while (parentId != null && depth++ < nodeList.size()) {
            T parent = findById(parentId);
            if (parent == null) {
                break;
            }
            ancestors.add(parent);
            parentId = parent.getParentId();
        }
        return ancestors;
    }

    /**
     * 将当前树形结构扁平化为列表（深度优先）
     *
     * @return 扁平节点列表
     */
    public List<T> flatten() {
        List<T> roots = build();
        return flattenInternal(roots);
    }

    /**
     * 扁平化指定树形结构（深度优先，迭代式避免栈溢出）。
     *
     * @param <T>   节点类型（TreeNode 子类）
     * @param roots 根节点列表
     * @return 扁平节点列表（深度优先顺序）
     */
    public static <T extends TreeNode<T, ?>> List<T> flatten(List<T> roots) {
        return flattenInternal(roots);
    }

    /**
     * 内部扁平化实现（迭代式，深度优先）。
     *
     * @param <T>   节点类型（TreeNode 子类）
     * @param roots 根节点列表
     * @return 扁平节点列表（深度优先顺序）
     */
    private static <T extends TreeNode<T, ?>> List<T> flattenInternal(List<T> roots) {
        List<T> result = new ArrayList<>();
        Deque<T> stack = new ArrayDeque<>();
        // 逆序压栈保证顺序
        for (int i = roots.size() - 1; i >= 0; i--) {
            stack.push(roots.get(i));
        }
        while (!stack.isEmpty()) {
            T node = stack.pop();
            result.add(node);
            List<T> children = node.getChildren();
            if (children != null) {
                for (int i = children.size() - 1; i >= 0; i--) {
                    stack.push(children.get(i));
                }
            }
        }
        return result;
    }

    /**
     * 迭代排序子树（避免递归栈溢出）
     *
     * @param nodes 待排序的节点列表
     */
    private void sortSubTree(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        // 使用自定义比较器（如果有），否则使用默认比较器
        Comparator<T> comparator = sortComparator != null ? sortComparator : defaultSortComparator();
        Deque<List<T>> stack = new ArrayDeque<>();
        stack.push(nodes);
        while (!stack.isEmpty()) {
            List<T> current = stack.pop();
            current.sort(comparator);
            for (T node : current) {
                List<T> children = node.getChildren();
                if (children != null && !children.isEmpty()) {
                    stack.push(children);
                }
            }
        }
    }

    // ==================== 静态便捷方法（无需实现 TreeNode 接口） ====================

    /**
     * 从扁平列表构建树形结构（静态便捷方法，无需实现 {@link TreeNode} 接口）。
     *
     * <p>适用于不希望或无法让 VO 类继承 {@link TreeNode} 的场景（如已有 VO 结构不便修改）。
     * 通过函数式接口传入 ID/parentId/children/sort 的获取和设置方式。
     *
     * <p>算法：O(n) 时间复杂度，通过 HashMap 分组实现。
     * 根节点约定：parentId 为 null 或等于 "0" 的节点视为根节点。
     *
     * @param flatList       扁平列表
     * @param idGetter       ID 获取函数
     * @param parentIdGetter 父 ID 获取函数
     * @param childrenSetter children 设置函数
     * @param sortGetter     排序字段获取函数（可为 null，null 表示不排序）
     * @param <T>            节点类型
     * @return 树形结构根节点列表
     */
    public static <T> List<T> buildSimple(
            List<T> flatList,
            Function<T, String> idGetter,
            Function<T, String> parentIdGetter,
            BiConsumer<T, List<T>> childrenSetter,
            Function<T, Integer> sortGetter) {

        if (flatList == null || flatList.isEmpty()) {
            return List.of();
        }

        String effectiveRootParentId = "0";
        Map<String, List<T>> parentIdMap = flatList.stream()
                .collect(Collectors.groupingBy(item -> {
                    String pid = parentIdGetter.apply(item);
                    return pid == null ? effectiveRootParentId : pid;
                }));

        List<T> roots = new ArrayList<>(parentIdMap.getOrDefault(effectiveRootParentId, Collections.emptyList()));
        for (T item : flatList) {
            List<T> children = parentIdMap.get(idGetter.apply(item));
            if (children != null) {
                if (sortGetter != null) {
                    children.sort(Comparator.comparingInt(sortGetter::apply));
                }
                childrenSetter.accept(item, children);
            }
        }

        if (sortGetter != null) {
            roots.sort(Comparator.comparingInt(sortGetter::apply));
        }

        return roots;
    }
}
