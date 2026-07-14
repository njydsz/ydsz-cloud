package com.njydsz.pmis.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;
import java.util.function.Function;

import com.njydsz.pmis.common.json.annotation.YdszJsonField;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

/**
 * 懒加载树节点包装。?
 *
 * <p>包装实际的树节点，提供按需加载子节点的能力。?
 * 适用于数据量较大、不适合一次性加载全部节点的场景。?
 *
 * <p><b>核心特性：</b>
 * <ul>
 *   <li>延迟加载：子节点仅在首次调用 {@code getChildren()} 时加。?/li>
 *   <li>加载状态跟踪：可通过 {@code isChildrenLoaded()} 检查加载状。?/li>
 *   <li>深度限制：防止无限递归，支持配置最大深。?/li>
 *   <li>线程安全：加载状态使。?volatile 保证可见。?/li>
 * </ul>
 *
 * <p><b>使用示例。?/b>
 * <pre>{@code
 * LazyTreeNode<Menu, Long> node = ...;
 * // 首次调用触发加载
 * List<LazyTreeNode<Menu, Long>> children = node.getChildren();
 * // 检查是否已加载
 * boolean loaded = node.isChildrenLoaded();
 * }</pre>
 *
 * @param <T>  继承自TreeNode的具体类。?
 * @param <ID> ID类型
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
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
     * 最大允许深。?
     */
    @YdszJsonField(ignore = true)
    private transient Integer maxDepth;

    /**
     * ID提取。?
     */
    @YdszJsonField(ignore = true)
    private transient Function<T, ID> idExtractor;

    /**
     * 加载锁，替代 synchronized(this) 以避。?JDK21 虚拟线程 Pinning
     */
    @YdszJsonField(ignore = true)
    @ToString.Exclude
    @EqualsAndHashCode.Exclude
    private transient ReentrantLock loadLock = new ReentrantLock();

    /**
     * 创建懒加载节点（根节点版本）
     *
     * @param node       实际节点
     * @param provider   节点提供。?
     * @param maxDepth   最大深。?
     * @param idExtractor ID提取。?
     */
    public LazyTreeNode(T node, TreeNodeProvider<T, ID> provider, int maxDepth, Function<T, ID> idExtractor) {
        this.node = node;
        this.provider = provider;
        this.maxDepth = maxDepth;
        this.depth = node.getLevel() != null ? node.getLevel() : TreeNode.ROOT_LEVEL;
        this.idExtractor = idExtractor;
        this.lazyChildren = new ArrayList<>();
    }

    /**
     * 获取子节点（触发懒加载）
     *
     * <p>首次调用时会通过 {@link TreeNodeProvider#getChildren(Object)} 加载子节点。?
     * 后续调用直接返回已加载的缓存结果。?
     *
     * @return 子节点列。?
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
     * 检查子节点是否已加。?
     *
     * @return 已加载返回true
     */
    public boolean isChildrenLoaded() {
        return childrenLoaded;
    }

    /**
     * 触发加载子节。?
     *
     * <p>通过 provider 获取子节点并包装。?LazyTreeNode。?
     * 如果当前深度已达。?maxDepth，则不会继续加载。?
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
     * 实际加载子节点的逻辑（调用方需持有 loadLock。?
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

        // 检查是否达到最大深。?
        if (depth != null && maxDepth != null && depth >= maxDepth) {
            childrenLoaded = true;
            lazyChildren = Collections.emptyList();
            node.setLeaf(true);
            return;
        }

        List<T> children = provider.getChildren(nodeId);
        if (children == null || children.isEmpty()) {
            childrenLoaded = true;
            lazyChildren = Collections.emptyList();
            node.setLeaf(true);
            return;
        }

        lazyChildren = new ArrayList<>(children.size());
        for (T child : children) {
            LazyTreeNode<T, ID> lazyChild = new LazyTreeNode<>(child, provider, maxDepth, idExtractor);
            lazyChildren.add(lazyChild);
        }

        node.setChildren(children);
        node.setLeaf(false);
        childrenLoaded = true;
    }

    /**
     * 获取加载锁，处理反序列化。?loadLock 。?null 的情。?
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
     * 判断是否为叶子节。?
     *
     * @return 叶子节点返回true
     */
    public boolean isLeaf() {
        return node.isLeaf();
    }

    /**
     * 深度优先遍历（仅遍历已加载的节点。?
     *
     * @param visitor 访问者函。?
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
     * 深度优先遍历（自动加载子节点。?
     *
     * @param visitor 访问者函。?
     */
    public void traverseDFSWithLoad(Consumer<LazyTreeNode<T, ID>> visitor) {
        visitor.accept(this);
        List<LazyTreeNode<T, ID>> children = getChildren();
        for (LazyTreeNode<T, ID> child : children) {
            child.traverseDFSWithLoad(visitor);
        }
    }
}
