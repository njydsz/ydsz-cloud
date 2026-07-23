package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import com.njydsz.common.json.annotation.YdszJsonField;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 懒加载树节点包装器
 *
 * <p>包装实际的树节点，提供按需加载子节点的能力。
 * 适用于数据量较大、不适合一次性加载全部节点的场景。
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>延迟加载：子节点仅在首次调用 {@code getChildren()} 时加载</li>
 *   <li>加载状态跟踪：可通过 {@code isChildrenLoaded()} 检查加载状态</li>
 *   <li>深度限制：防止无限递归，支持配置最大深度</li>
 *   <li>批量加载：支持分批加载子节点，避免单次加载过多数据</li>
 *   <li>线程安全：加载状态使用 volatile 保证可见性，使用 ReentrantLock 保证互斥</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * LazyTreeNode<Menu, Long> node = ...;
 * // 首次调用触发加载
 * List<LazyTreeNode<Menu, Long>> children = node.getChildren();
 * // 检查是否已加载
 * boolean loaded = node.isChildrenLoaded();
 * // 检查是否还有更多子节点
 * if (node.hasMoreChildren()) {
 *     node.loadMoreChildren(); // 加载下一批
 * }
 * }</pre>
 *
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class LazyTreeNode<T extends TreeNode<T, ID>, ID extends Serializable> implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 被包装的实际节点
     */
    private T node;

    /**
     * 节点提供者，用于懒加载子节点
     */
    @YdszJsonField(ignore = true)
    private transient TreeNodeProvider<T, ID> provider;

    /**
     * 子节点懒加载列表
     */
    private transient volatile List<LazyTreeNode<T, ID>> lazyChildren;

    /**
     * 子节点是否已加载
     */
    private volatile boolean childrenLoaded = false;

    /**
     * 当前节点深度（从1开始）
     */
    private Integer depth;

    /**
     * 最大允许深度
     */
    @YdszJsonField(ignore = true)
    private transient Integer maxDepth;

    /**
     * ID提取器
     */
    @YdszJsonField(ignore = true)
    private transient Function<T, ID> idExtractor;

    /**
     * 每批加载的子节点数量（0 表示全量加载）
     */
    @YdszJsonField(ignore = true)
    private transient int batchSize = 0;

    /**
     * 子节点总数量（用于分批加载判断是否还有更多）
     */
    private transient volatile int totalChildrenCount = -1;

    /**
     * 当前已加载的子节点偏移量
     */
    private transient volatile int loadedOffset = 0;

    /**
     * 加载锁，替代 synchronized(this) 以避免JDK21 虚拟线程 Pinning
     */
    @YdszJsonField(ignore = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private transient ReentrantLock loadLock = new ReentrantLock();

    /**
     * 创建懒加载节点（根节点版本）
     *
     * @param node       实际节点
     * @param provider   节点提供者
     * @param maxDepth   最大深度
     * @param idExtractor ID提取器
     */
    public LazyTreeNode(T node, TreeNodeProvider<T, ID> provider, int maxDepth, Function<T, ID> idExtractor) {
        this(node, provider, maxDepth, idExtractor, 0);
    }

    /**
     * 创建懒加载节点（支持批量加载）
     *
     * @param node        实际节点
     * @param provider    节点提供者
     * @param maxDepth    最大深度
     * @param idExtractor ID提取器
     * @param batchSize   每批加载的子节点数量（0 表示全量加载）
     */
    public LazyTreeNode(T node, TreeNodeProvider<T, ID> provider, int maxDepth,
                        Function<T, ID> idExtractor, int batchSize) {
        this.node = node;
        this.provider = provider;
        this.maxDepth = maxDepth;
        this.depth = node.getLevel() != null ? node.getLevel() : TreeNode.ROOT_LEVEL;
        this.idExtractor = idExtractor;
        this.batchSize = batchSize;
        this.lazyChildren = new ArrayList<>();
    }

    /**
     * 获取子节点（触发懒加载）
     *
     * <p>首次调用时会通过 {@link TreeNodeProvider#getChildren(Object)} 加载子节点。
     * 后续调用直接返回已加载的缓存结果。
     *
     * @return 子节点列表
     */
    public List<LazyTreeNode<T, ID>> getChildren() {
        if (!childrenLoaded) {
            ReentrantLock lock = getLoadLock();
            lock.lock();
            try {
                if (!childrenLoaded) {
                    loadChildren();
                }
            } finally {
                lock.unlock();
            }
        }
        return lazyChildren != null ? lazyChildren : Collections.emptyList();
    }

    /**
     * 检查子节点是否已加载
     *
     * @return 已加载返回true
     */
    public boolean isChildrenLoaded() {
        return childrenLoaded;
    }

    /**
     * 判断是否还有更多子节点未加载（分批加载模式）
     *
     * @return 有更多子节点返回true
     */
    public boolean hasMoreChildren() {
        if (!childrenLoaded || batchSize <= 0 || totalChildrenCount < 0) {
            return false;
        }
        return loadedOffset < totalChildrenCount;
    }

    /**
     * 加载下一批子节点
     *
     * <p>仅在分批加载模式下有效。如果已无更多子节点，此方法不做任何操作。
     */
    public void loadMoreChildren() {
        if (!childrenLoaded || batchSize <= 0 || !hasMoreChildren()) {
            return;
        }
        ReentrantLock lock = getLoadLock();
        lock.lock();
        try {
            if (!hasMoreChildren()) {
                return;
            }
            ID nodeId = idExtractor.apply(node);
            if (nodeId == null || provider == null) {
                return;
            }
            List<T> batch = provider.getChildrenBatch(nodeId, loadedOffset, batchSize);
            if (batch != null && !batch.isEmpty()) {
                for (T child : batch) {
                    LazyTreeNode<T, ID> lazyChild = createChildNode(child);
                    lazyChildren.add(lazyChild);
                }
                loadedOffset += batch.size();
            }
        } finally {
            lock.unlock();
        }
    }

    /**
     * 触发加载子节点
     *
     * <p>通过 provider 获取子节点并包装为 LazyTreeNode。
     * 如果当前深度已达 maxDepth，则不会继续加载。
     */
    public void loadChildren() {
        ReentrantLock lock = getLoadLock();
        lock.lock();
        try {
            doLoadChildren();
        } finally {
            lock.unlock();
        }
    }

    /**
     * 实际加载子节点的逻辑（调用方需持有 loadLock）
     */
    private void doLoadChildren() {
        if (childrenLoaded) {
            return;
        }

        ID nodeId = idExtractor.apply(node);
        if (nodeId == null || provider == null) {
            childrenLoaded = true;
            lazyChildren = Collections.emptyList();
            return;
        }

        // 检查是否达到最大深度
        if (depth != null && maxDepth != null && depth >= maxDepth) {
            childrenLoaded = true;
            lazyChildren = Collections.emptyList();
            node.setLeaf(true);
            return;
        }

        // 分批加载模式
        if (batchSize > 0) {
            totalChildrenCount = provider.countChildren(nodeId);
            if (totalChildrenCount == 0) {
                childrenLoaded = true;
                lazyChildren = Collections.emptyList();
                node.setLeaf(true);
                return;
            }
            List<T> firstBatch = provider.getChildrenBatch(nodeId, 0, batchSize);
            lazyChildren = new ArrayList<>(Math.min(totalChildrenCount, batchSize));
            for (T child : firstBatch) {
                LazyTreeNode<T, ID> lazyChild = createChildNode(child);
                lazyChildren.add(lazyChild);
            }
            loadedOffset = firstBatch.size();
            node.setChildren(firstBatch);
            node.setLeaf(false);
            childrenLoaded = true;
            return;
        }

        // 全量加载模式
        List<T> children = provider.getChildren(nodeId);
        if (children == null || children.isEmpty()) {
            childrenLoaded = true;
            lazyChildren = Collections.emptyList();
            node.setLeaf(true);
            return;
        }

        lazyChildren = new ArrayList<>(children.size());
        for (T child : children) {
            LazyTreeNode<T, ID> lazyChild = createChildNode(child);
            lazyChildren.add(lazyChild);
        }

        node.setChildren(children);
        node.setLeaf(false);
        childrenLoaded = true;
    }

    /**
     * 创建子懒加载节点
     */
    private LazyTreeNode<T, ID> createChildNode(T child) {
        LazyTreeNode<T, ID> lazyChild = new LazyTreeNode<>(child, provider, maxDepth, idExtractor, batchSize);
        lazyChild.depth = (this.depth != null ? this.depth : TreeNode.ROOT_LEVEL) + 1;
        return lazyChild;
    }

    /**
     * 获取加载锁，处理反序列化时 loadLock 为 null 的情况
     */
    private ReentrantLock getLoadLock() {
        ReentrantLock lock = loadLock;
        if (lock == null) {
            loadLock = lock = new ReentrantLock();
        }
        return lock;
    }

    /**
     * 获取被包装的实际节点
     *
     * @return 实际节点
     */
    public T getNode() {
        return node;
    }

    /**
     * 获取节点ID
     *
     * @return 节点ID
     */
    public ID getId() {
        return node.getId();
    }

    /**
     * 获取父节点ID
     *
     * @return 父节点ID
     */
    public ID getParentId() {
        return node.getParentId();
    }

    /**
     * 获取当前深度
     *
     * @return 节点深度
     */
    public Integer getDepth() {
        return depth;
    }

    /**
     * 判断是否为叶子节点
     *
     * @return 叶子节点返回true
     */
    public boolean isLeaf() {
        return node.isLeaf();
    }

    /**
     * 深度优先遍历（仅遍历已加载的节点）
     *
     * @param visitor 访问者函数
     */
    public void traverseDFS(Consumer<LazyTreeNode<T, ID>> visitor) {
        visitor.accept(this);
        if (lazyChildren != null) {
            for (LazyTreeNode<T, ID> child : lazyChildren) {
                child.traverseDFS(visitor);
            }
        }
    }

    /**
     * 深度优先遍历（自动加载子节点）
     *
     * @param visitor 访问者函数
     */
    public void traverseDFSWithLoad(Consumer<LazyTreeNode<T, ID>> visitor) {
        visitor.accept(this);
        List<LazyTreeNode<T, ID>> children = getChildren();
        for (LazyTreeNode<T, ID> child : children) {
            child.traverseDFSWithLoad(visitor);
        }
    }
}
