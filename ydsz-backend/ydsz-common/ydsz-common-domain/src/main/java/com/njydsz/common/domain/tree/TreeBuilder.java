package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.BiConsumer;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
/**
 * 树形结构核心构建器
 *
 * <p>整合了构建、查询、扁平化、排序、校验、路径生成等全部能力，提供统一的对外 API。
 * 内部维护缓存机制（cachedRoots + dirty 标记），避免重复构建。
 *
 * <p><b>核心能力：</b>
 * <ul>
 *   <li>父子关系绑定、层级计算、路径生成</li>
 *   <li>按 ID 查找、获取后代祖先节点</li>
 *   <li>节点统计（总数、叶子数、深度）</li>
 *   <li>扁平化、按层级筛选</li>
 *   <li>循环引用检测</li>
 *   <li>懒加载树构建</li>
 * </ul>
 *
 * <p><b>算法：</b>O(n) 时间复杂度，通过 HashMap 缓存实现。
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * List<Menu> tree = new TreeBuilder<>(0L, allMenus).build();
 * // 查询
 * Menu node = treeBuilder.findById(1L);
 * // 扁平化
 * List<Menu> flat = treeBuilder.flatten();
 * }</pre>
 *
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see TreeNode
 */
public class TreeBuilder<T extends TreeNode<T, ID>, ID extends Serializable> {

    private static final Logger log = LoggerFactory.getLogger(TreeBuilder.class);

    /** 节点数量阈值，超过此值时使用迭代模式替代递归以防止栈溢出 */
    private static final int ITERATIVE_MODE_THRESHOLD = 10000;

    /** 排序比较器，。sort 字段升序排列，null 值排在最后*/
    private static final Comparator<TreeNode<?, ?>> SORT_COMPARATOR = Comparator.comparing(
            TreeNode::getSort,
            Comparator.nullsLast(Integer::compareTo)
    );

    private static <T extends TreeNode<T, ?>> Comparator<T> getSortComparator() {
        return (Comparator<T>) SORT_COMPARATOR;
    }

    // ── 配置字段 ────────────────────────────────────────────────────────────────

    private ID rootId;
    private final List<T> nodeList;
    private Function<T, ID> idExtractor = TreeNode::getId;
    private Function<T, ID> parentIdExtractor = TreeNode::getParentId;
    private boolean autoCalcLevel = true;
    private boolean autoBuildPath = true;
    private boolean multiRoot = false;

    // ── 缓存字段 ────────────────────────────────────────────────────────────────

    /** 缓存的根节点列表 */
    private volatile List<T> cachedRoots;
    /** 缓存的节点映射（ID -> Node）*/
    private volatile Map<ID, T> cachedNodeMap;
    /** 缓存的全量扁平节点列表*/
    private volatile List<T> cachedAllNodes;
    /** 脏标记：true 表示需要重新构建*/
    private volatile boolean dirty = true;
    /** 构建锁，替代 synchronized(this) 以避免JDK21 虚拟线程 Pinning */
    private final ReentrantLock buildLock = new ReentrantLock();

    // ── 构造方向────────────────────────────────────────────────────────────────

    public TreeBuilder(ID rootId, List<T> nodeList) {
        this.rootId = rootId;
        this.nodeList = Objects.requireNonNull(nodeList, "nodeList不能为null");
    }

    public TreeBuilder(List<T> nodeList) {
        this(null, nodeList);
    }

    // ── 链式配置 ────────────────────────────────────────────────────────────────

    public TreeBuilder<T, ID> rootId(ID rootId) {
        this.rootId = rootId;
        this.dirty = true;
        return this;
    }

    public TreeBuilder<T, ID> autoCalcLevel(boolean autoCalcLevel) {
        this.autoCalcLevel = autoCalcLevel;
        this.dirty = true;
        return this;
    }

    public TreeBuilder<T, ID> autoCalcLevel() {
        return autoCalcLevel(true);
    }

    public TreeBuilder<T, ID> autoBuildPath(boolean autoBuildPath) {
        this.autoBuildPath = autoBuildPath;
        this.dirty = true;
        return this;
    }

    public TreeBuilder<T, ID> autoBuildPath() {
        return autoBuildPath(true);
    }

    public TreeBuilder<T, ID> multiRoot(boolean multiRoot) {
        this.multiRoot = multiRoot;
        this.dirty = true;
        return this;
    }

    public TreeBuilder<T, ID> multiRoot() {
        return multiRoot(true);
    }

    public TreeBuilder<T, ID> idExtractor(Function<T, ID> idExtractor) {
        this.idExtractor = idExtractor;
        this.dirty = true;
        return this;
    }

    public TreeBuilder<T, ID> parentIdExtractor(Function<T, ID> parentIdExtractor) {
        this.parentIdExtractor = parentIdExtractor;
        this.dirty = true;
        return this;
    }

    /** 强制标记缓存为脏，下次调用build() 时重。*/
    public void markDirty() {
        this.dirty = true;
    }

    // ── 核心构建 ────────────────────────────────────────────────────────────────

    /**
     * 构建树形结构（带缓存。
     *
     * <p>使用双重检查锁（DCL）确保多线程环境下的缓存安全。
     * 每次调用返回独立的列表副本，防止外部篡改内部缓存。
     *
     * @return 构建完成的根节点列表
     */
    public List<T> build() {
        if (!dirty && cachedRoots != null) {
            return new ArrayList<>(cachedRoots);
        }
        buildLock.lock();
        try {
            if (!dirty && cachedRoots != null) {
                return new ArrayList<>(cachedRoots);
            }
            rebuild();
        } finally {
            buildLock.unlock();
        }
        return new ArrayList<>(cachedRoots);
    }

    /** 重新构建树并刷新缓存 */
    private void rebuild() {
        if (nodeList.isEmpty()) {
            this.cachedRoots = new ArrayList<>();
            this.cachedNodeMap = new HashMap<>();
            this.cachedAllNodes = new ArrayList<>();
            this.dirty = false;
            return;
        }

        // 1. 初始化节点映射
        Map<ID, T> nodeMap = new HashMap<>(nodeList.size());
        for (T node : nodeList) {
            ID id = idExtractor.apply(node);
            if (id != null) {
                nodeMap.put(id, node);
            }
        }

        // 2. 循环引用检测
        detectCyclicReference(nodeList, idExtractor, parentIdExtractor, nodeMap);

        // 3. 构建父子关系
        if (nodeList.size() > ITERATIVE_MODE_THRESHOLD) {
            log.warn("节点数量 {} 超过阈。{}，切换到迭代模式构建树形结构",
                    nodeList.size(), ITERATIVE_MODE_THRESHOLD);
        }
        buildRelations(nodeList, nodeMap);

        // 4. 筛选根节点并排序
        List<T> roots = nodeList.stream()
                .filter(this::isRootNode)
                .toList();
        sortSubTree(roots);

        // 5. 生成路径（如果需要）
        if (autoBuildPath) {
            generatePaths(nodeList, nodeMap);
        }

        // 6. 缓存结果
        this.cachedRoots = new ArrayList<>(roots);
        this.cachedNodeMap = nodeMap;
        this.cachedAllNodes = flattenInternal(roots);
        this.dirty = false;
    }

    /** 构建父子关系 */
    private void buildRelations(List<T> nodeList, Map<ID, T> nodeMap) {
        Map<ID, Set<ID>> addedChildrenIndex = new HashMap<>();
        for (T node : nodeList) {
            ID parentId = parentIdExtractor.apply(node);
            if (parentId == null && rootId != null) {
                continue;
            }
            T parent = nodeMap.get(parentId);
            if (parent != null) {
                List<T> children = parent.getChildren();
                if (children == null) {
                    children = new ArrayList<>();
                    parent.setChildren(children);
                    parent.setLeaf(false);
                }

                ID nodeId = idExtractor.apply(node);
                Set<ID> addedChildren = addedChildrenIndex.computeIfAbsent(
                        ((TreeNode<T, ID>) parent).getId(), k -> new HashSet<>());
                if (nodeId != null && !addedChildren.contains(nodeId)) {
                    children.add(node);
                    addedChildren.add(nodeId);
                }

                if (autoCalcLevel) {
                    int parentLevel = ((TreeNode<T, ID>) parent).getLevel() != null
                            ? ((TreeNode<T, ID>) parent).getLevel()
                            : TreeNode.ROOT_LEVEL;
                    node.setLevel(parentLevel + 1);
                }
            }
        }
    }

    /** 判断节点是否为根节点 */
    private boolean isRootNode(T node) {
        ID parentId = parentIdExtractor.apply(node);
        if (multiRoot) {
            if (parentId == null) {
                return true;
            }
            return !cachedNodeMap.containsKey(parentId);
        }
        if (rootId == null) {
            return parentId == null;
        }
        return Objects.equals(rootId, parentId);
    }

    // ── 查询能力 ────────────────────────────────────────────────────────────────

    /** 确保树已构建 */
    private void ensureBuilt() {
        if (dirty || cachedRoots == null) {
            build();
        }
    }

    /** 获取节点映射的不可变视图 */
    public Map<ID, T> getNodeMap() {
        ensureBuilt();
        return cachedNodeMap != null ? Collections.unmodifiableMap(cachedNodeMap) : Collections.emptyMap();
    }

    /** 根据ID查找节点 */
    public T findById(ID id) {
        ensureBuilt();
        return cachedNodeMap != null ? cachedNodeMap.get(id) : null;
    }

    /** 获取指定节点的所有后代节点*/
    public List<T> getDescendants(T node) {
        ensureBuilt();
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

    /** 获取指定节点的所有祖先节点*/
    public List<T> getAncestors(T node) {
        ensureBuilt();
        List<T> ancestors = new ArrayList<>();
        ID parentId = node.getParentId();
        while (parentId != null) {
            T parent = cachedNodeMap != null ? cachedNodeMap.get(parentId) : null;
            if (parent != null) {
                ancestors.add(parent);
                parentId = parent.getParentId();
            } else {
                break;
            }
        }
        return ancestors;
    }

    /**
     * 判断节点是否为根节点
     *
     * @param node   待判断的节点
     * @param rootId 根节点ID（为 null 时使用构建器配置的 rootId）
     * @return 是根节点返回 true
     */
    public boolean isRoot(T node, ID rootId) {
        ensureBuilt();
        ID effectiveRootId = rootId != null ? rootId : this.rootId;
        if (effectiveRootId == null) {
            return node.getParentId() == null;
        }
        return Objects.equals(effectiveRootId, node.getParentId());
    }

    // ── 统计能力 ────────────────────────────────────────────────────────────────

    /** 统计节点总数 */
    public int countNodes() {
        ensureBuilt();
        return cachedAllNodes != null ? cachedAllNodes.size() : 0;
    }

    /** 统计根节点数量*/
    public int countRootNodes() {
        ensureBuilt();
        return cachedRoots != null ? cachedRoots.size() : 0;
    }

    /** 获取树的深度 */
    public int getTreeDepth() {
        ensureBuilt();
        if (cachedAllNodes == null || cachedAllNodes.isEmpty()) {
            return 0;
        }
        return cachedAllNodes.stream()
                .mapToInt(this::getNodeDepth)
                .max()
                .orElse(0);
    }

    /** 计算指定节点的深度（从该节点到根节点的路径长度） */
    private int getNodeDepth(T node) {
        int depth = 1;
        T current = node;
        while (current != null) {
            ID parentId = current.getParentId();
            T parent = parentId != null && cachedNodeMap != null ? cachedNodeMap.get(parentId) : null;
            if (parent == null) {
                break;
            }
            depth++;
            current = parent;
        }
        return depth;
    }

    /** 统计叶子节点数量 */
    public int countLeafNodes() {
        ensureBuilt();
        return (int) cachedAllNodes.stream()
                .filter(node -> {
                    List<T> children = node.getChildren();
                    return children == null || children.isEmpty();
                })
                .count();
    }

    /** 获取所有叶子节点*/
    public List<T> getLeafNodes() {
        ensureBuilt();
        return cachedAllNodes.stream()
                .filter(node -> {
                    List<T> children = node.getChildren();
                    return children == null || children.isEmpty();
                })
                .toList();
    }

    // ── 扁平化与筛选────────────────────────────────────────────────────────────

    /** 将当前缓存的树形结构扁平化为列表 */
    public List<T> flatten() {
        ensureBuilt();
        return new ArrayList<>(cachedAllNodes);
    }

    /** 将指定树形结构扁平化为列表*/
    public static <T extends TreeNode<T, ?>> List<T> flatten(List<T> roots) {
        return flattenInternal(roots);
    }

    /** 内部扁平化实体*/
    private static <T extends TreeNode<T, ?>> List<T> flattenInternal(List<T> roots) {
        List<T> result = new ArrayList<>();
        for (T root : roots) {
            result.add(root);
            List<T> children = root.getChildren();
            if (children != null && !children.isEmpty()) {
                result.addAll(flattenInternal(children));
            }
        }
        return result;
    }

    /** 根据层级筛选节点*/
    public List<T> filterByLevel(int level) {
        ensureBuilt();
        return cachedAllNodes.stream()
                .filter(node -> {
                    Integer nodeLevel = node.getLevel();
                    return nodeLevel != null && nodeLevel == level;
                })
                .toList();
    }

    // ── 排序 ────────────────────────────────────────────────────────────────────

    /** 对节点列表进行排序，并迭代排序子树（避免递归栈溢出） */
    private static <T extends TreeNode<T, ?>> void sortSubTree(List<T> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        Deque<List<T>> stack = new ArrayDeque<>();
        stack.push(nodes);
        while (!stack.isEmpty()) {
            List<T> current = stack.pop();
            current.sort(getSortComparator());
            for (T node : current) {
                List<T> children = node.getChildren();
                if (children != null && !children.isEmpty()) {
                    stack.push(children);
                }
            }
        }
    }

    // ── 校验 ────────────────────────────────────────────────────────────────────

    /** 检测节点间是否存在循环引用 */
    private static <T extends TreeNode<T, ID>, ID extends Serializable> void detectCyclicReference(
            List<T> nodeList,
            Function<T, ID> idExtractor,
            Function<T, ID> parentIdExtractor,
            Map<ID, T> nodeMap) {
        for (T node : nodeList) {
            ID nodeId = idExtractor.apply(node);
            if (nodeId == null) {
                continue;
            }
            Set<ID> visited = new HashSet<>();
            ID currentId = nodeId;
            visited.add(currentId);
            ID parentId = parentIdExtractor.apply(node);
            int depth = 0;
            int maxDepth = nodeList.size();
            while (parentId != null && nodeMap.containsKey(parentId)) {
                depth++;
                if (depth > maxDepth) {
                    throw new IllegalStateException(
                            "检测到树中存在循环引用，涉及节点 " + nodeId);
                }
                if (!visited.add(parentId)) {
                    throw new IllegalStateException(
                            "检测到树中存在循环引用，涉及节点 " + nodeId);
                }
                T parent = nodeMap.get(parentId);
                parentId = parent != null ? parentIdExtractor.apply(parent) : null;
            }
        }
    }

    // ── 路径生成 ────────────────────────────────────────────────────────────────

    /** 自动构建所有节点的路径 */
    private void generatePaths(List<T> nodeList, Map<ID, T> nodeMap) {
        // 按 level 升序排序，确保父节点路径先生成（P2-5 修复：消除对 nodeList 顺序的隐含依赖）
        List<T> sorted = new ArrayList<>(nodeList);
        sorted.sort(Comparator.comparingInt(n -> {
            Integer level = n.getLevel();
            return level != null ? level : TreeNode.ROOT_LEVEL;
        }));

        for (T node : sorted) {
            ID parentId = parentIdExtractor.apply(node);
            if (parentId != null) {
                T parent = nodeMap.get(parentId);
                if (parent != null) {
                    String parentPath = parent.getPath();
                    if (parentPath == null) {
                        parentPath = TreeNode.PATH_SEPARATOR;
                    }
                    String nodeIdStr = idExtractor.apply(node) != null
                            ? idExtractor.apply(node).toString()
                            : "";
                    node.setPath(parentPath + nodeIdStr + TreeNode.PATH_SEPARATOR);
                }
            } else {
                node.setPath(TreeNode.PATH_SEPARATOR);
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
     * 默认根父ID 为 "0"，可使用 {@link #buildSimple(List, String, Function, Function, BiConsumer, Function)} 指定。
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
        return buildSimple(flatList, "0", idGetter, parentIdGetter, childrenSetter, sortGetter);
    }

    /**
     * 从扁平列表构建树形结构（指定根父ID）。
     *
     * <p>与 {@link #buildSimple(List, Function, Function, BiConsumer, Function)} 相同，
     * 但允许调用方指定根父 ID（如 null、"0"、"-1" 等）。
     *
     * @param flatList       扁平列表
     * @param rootParentId   根节点的父 ID 值（如 "0"、null、"-1" 等）
     * @param idGetter       ID 获取函数
     * @param parentIdGetter 父 ID 获取函数
     * @param childrenSetter children 设置函数
     * @param sortGetter     排序字段获取函数（可为 null，null 表示不排序）
     * @param <T>            节点类型
     * @return 树形结构根节点列表
     */
    public static <T> List<T> buildSimple(
            List<T> flatList,
            String rootParentId,
            Function<T, String> idGetter,
            Function<T, String> parentIdGetter,
            BiConsumer<T, List<T>> childrenSetter,
            Function<T, Integer> sortGetter) {

        if (flatList == null || flatList.isEmpty()) {
            return List.of();
        }

        String effectiveRootParentId = rootParentId != null ? rootParentId : "0";
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
