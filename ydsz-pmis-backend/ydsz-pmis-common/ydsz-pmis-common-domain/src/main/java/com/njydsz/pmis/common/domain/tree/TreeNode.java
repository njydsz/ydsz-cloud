package com.njydsz.pmis.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import com.njydsz.pmis.common.json.annotation.JsonField;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * 树节。基础类
 *
 * <p>用于构建树形结构数据，如组织架构、菜单分类、商品分类、区域管理等场景。
 * 支持泛型继承，允许自定义扩展节。属性。
 *
 * <p><b>设计原则：</b>
 * <ul>
 *   <li>组合优于继承：通过组合方式扩展功能</li>
 *   <li>不可变设计：核心属性通过 Builder 模式初始。</li>
 *   <li>流式API：支持链式调用</li>
 * </ul>
 *
 * <p><b>字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>T</td><td>当前节。唯一标识</td></tr>
 *   <tr><td>parentId</td><td>T</td><td>父节。ID，顶级节。为null</td></tr>
 *   <tr><td>children</td><td>List&lt;T&gt;</td><td>子节。列表</td></tr>
 *   <tr><td>sort</td><td>Integer</td><td>排序字段，同级节。排序用</td></tr>
 *   <tr><td>level</td><td>Integer</td><td>节。层级深度，根节。为</td></tr>
 *   <tr><td>path</td><td>String</td><td>节。路径，如 "/1/2/3/"</td></tr>
 *   <tr><td>leaf</td><td>Boolean</td><td>是否为叶子节。</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 定义菜单树节。
 * &#64;Data
 * &#64;EqualsAndHashCode(callSuper = true)
 * public class Menu extends TreeNode<Menu, Long> {
 *     private String menuName;
 *     private String menuIcon;
 *     private String routePath;
 * }
 *
 * // 构建树形结构
 * List<Menu> allMenus = menuMapper.selectList();
 * List<Menu> tree = new TreeBuilder<>(0L, allMenus).build();
 *
 * // 遍历树节。
 * tree.forEach(node -> {
 *     log.info("{} - {}", node.getLevel(), node.getMenuName());
 * });
 * }</pre>
 *
 * <p><b>。泛型安全警告：</b>此类使用了递归泛型模式 {@code TreeNode<T extends TreeNode<T, ID>, ID>}。
 * 内部多处存在 {@code (T) this} 的未经检查强转。由。Java 类型擦除机制，{@code ClassCastException}
 * 不会在强转时立即抛出，而是在返回值被使用时才可能触发。使用时必须确保泛型参数 {@code T}
 * 与具体子类类型一致，例如 {@code class Menu extends TreeNode<Menu, Long>} 是安全的。
 * 为 {@code class Menu extends TreeNode<OtherType, Long>} 将在运行时抛为 {@code ClassCastException}。
 *
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @see TreeBuilder
 */
@Getter
@EqualsAndHashCode(exclude = {"children"})
@SuperBuilder
@NoArgsConstructor
@AllArgsConstructor(access = AccessLevel.PROTECTED)
public class TreeNode<T extends TreeNode<T, ID>, ID extends Serializable> implements Serializable {

    private static final long serialVersionUID = 8676131899637805509L;
    /**
     * 根节。层级深。
     */
    public static final int ROOT_LEVEL = 1;

    /**
     * 根节。路径前缀
     */
    public static final String PATH_SEPARATOR = "/";

    /**
     * 当前节。唯一标识
     *
     * <p>唯一标识树中的一个节。。
     */
    @Setter
    private ID id;

    /**
     * 父节。ID
     *
     * <p>顶级节。的父ID为null。
     * 也可使用特定值（。。1）作为根节。标识。
     */
    @Setter
    private ID parentId;

    /**
     * 子节。列表
     *
     * <p>存储属于当前节。的所有直接子节。。
     * 空列表表示叶子节。。
     */
    @Builder.Default
    @Setter
    private transient List<T> children = new ArrayList<>();

    /**
     * 排序字段
     *
     * <p>用于同级别节。的排序，数值越小排序越靠前。
     * 支持 null 值，null 值排在最后。
     */
    @Setter
    private Integer sort;

    /**
     * 节。层级深度
     *
     * <p>从根节。开始计算深度：
     * <ul>
     *   <li>根节。：level = 1</li>
     *   <li>二级节。：level = 2</li>
     *   <li>以此类推...</li>
     * </ul>
     */
    @Builder.Default
    @Setter
    private Integer level = ROOT_LEVEL;

    /**
     * 节。路径
     *
     * <p>格式：从根节。到当前节。的完整路径。
     * 例如。/1/2/3/" 表示 id=3 的节。，其父id=2，父父id=1。
     * 可用于快速判断节。归属关系。
     */
    @Setter
    private String path;

    /**
     * 是否为叶子节。
     *
     * <p>。TreeBuilder 自动计算法
     * <ul>
     *   <li>true：无子节。</li>
     *   <li>false：有子节。</li>
     * </ul>
     */
    @Builder.Default
    @Setter
    private Boolean leaf = true;

    /**
     * 添加子节。
     *
     * <p>将指定子节。添加到当前节。的子列表中。
     *
     * @param child 子节。实体
     * @return 当前节。，支持链式调用
     */
    public T addChild(T child) {
        if (children == null) {
            children = new ArrayList<>();
        }
        children.add(child);
        this.leaf = false;
        return (T) this;
    }

    /**
     * 添加多个子节。
     *
     * @param childList 子节。列表
     * @return 当前节。，支持链式调用
     */
    public T addChildren(List<T> childList) {
        if (children == null) {
            children = new ArrayList<>();
        }
        if (childList != null && !childList.isEmpty()) {
            children.addAll(childList);
            this.leaf = false;
        }
        return (T) this;
    }

    /**
     * 判断是否为根节。
     *
     * @param rootId 根节。ID。
     * @return 如果是根节。返回true
     */
    public boolean isRootNode(ID rootId) {
        if (rootId == null) {
            return parentId == null;
        }
        return Objects.equals(rootId, parentId);
    }

    /**
     * 判断是否为根节。（无参数版本号
     *
     * <p>判断 parentId 是否。null。
     *
     * @return parentId为null返回true
     */
    public boolean isRootNode() {
        return parentId == null;
    }

    /**
     * 判断是否为叶子节。
     *
     * @return 如果没有子节。返回true
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /**
     * 获取子节。数量
     *
     * @return 直接子节。数量
     */
    @JsonField(ignore = true)
    public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    /**
     * 获取所有后代节。数量
     *
     * <p>包括所有层级的子节。数量总和。
     *
     * @return 后代节。总数
     */
    @JsonField(ignore = true)
    public int getDescendantCount() {
        int count = 0;
        if (children == null || children.isEmpty()) {
            return count;
        }
        Deque<T> stack = new ArrayDeque<>(children);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            count++;
            List<T> nodeChildren = node.getChildren();
            if (nodeChildren != null && !nodeChildren.isEmpty()) {
                stack.addAll(nodeChildren);
            }
        }
        return count;
    }

    /**
     * 深度优先遍历
     *
     * <p>先访问当前节。，再遍历子节。。
     *
     * @param visitor 访问者函数
     */
    public void traverseDFS(Consumer<T> visitor) {
        Deque<T> stack = new ArrayDeque<>();
        stack.push((T) this);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            visitor.accept(node);
            List<T> nodeChildren = node.getChildren();
            if (nodeChildren != null && !nodeChildren.isEmpty()) {
                // Push in reverse order so leftmost child is processed first
                for (int i = nodeChildren.size() - 1; i >= 0; i--) {
                    stack.push(nodeChildren.get(i));
                }
            }
        }
    }

    /**
     * 广度优先遍历
     *
     * <p>先访问当前层级所有节。，再访问下一层级。
     *
     * @param visitor 访问者函数
     */
    public void traverseBFS(Consumer<T> visitor) {
        visitor.accept((T) this);
        List<T> currentLevel = new ArrayList<>(List.of((T) this));
        while (!currentLevel.isEmpty()) {
            List<T> nextLevel = new ArrayList<>();
            for (T node : currentLevel) {
                if (node.getChildren() != null) {
                    nextLevel.addAll(node.getChildren());
                }
            }
            nextLevel.forEach(visitor);
            currentLevel = nextLevel;
        }
    }

    /**
     * 构建当前节。的路。
     *
     * <p>从根节。到当前节。的完整路径字符串。
     *
     * @param rootPath 根路径前缀
     * @return 路径字符。
     */
    public String buildPath(String rootPath) {
        if (rootPath == null) {
            rootPath = PATH_SEPARATOR;
        }
        return rootPath + (id != null ? id.toString() : "") + PATH_SEPARATOR;
    }

    /**
     * 判断是否包含指定子节。
     *
     * @param childId 子节。ID
     * @return 包含返回true
     */
    public boolean containsChild(ID childId) {
        if (children == null) {
            return false;
        }
        return children.stream()
                .anyMatch(child -> Objects.equals(child.getId(), childId));
    }

    /**
     * 迭代查找指定节。（避免递归栈溢出）
     *
     * @param targetId 目标节。ID
     * @return 找到返回节。，否则返回null
     */
    public T findById(ID targetId) {
        if (Objects.equals(this.id, targetId)) {
            return (T) this;
        }
        if (children == null || children.isEmpty()) {
            return null;
        }
        // 使用栈进行深度优先搜索，避免递归调用栈溢。
        Deque<T> stack = new ArrayDeque<>();
        stack.push((T) this);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            if (Objects.equals(node.getId(), targetId)) {
                return node;
            }
            List<T> nodeChildren = node.getChildren();
            if (nodeChildren != null && !nodeChildren.isEmpty()) {
                // 逆序压栈，保证左侧子节。先被处理
                for (int i = nodeChildren.size() - 1; i >= 0; i--) {
                    stack.push(nodeChildren.get(i));
                }
            }
        }
        return null;
    }

    /**
     * 获取所有祖先节。ID列表
     *
     * <p>从父节。到根节。的ID列表。
     *
     * @param nodeList 所有节。列表（用于查找。
     * @return 祖先节。ID列表
     */
    public List<ID> getAncestorIds(List<T> nodeList) {
        // 构建 ID -> Node 索引，将 O(n²) 降为 O(n)
        Map<ID, T> nodeMap = new HashMap<>();
        if (nodeList != null) {
            for (T node : nodeList) {
                if (node != null && node.getId() != null) {
                    nodeMap.put(node.getId(), node);
                }
            }
        }
        
        List<ID> ancestors = new ArrayList<>();
        T current = (T) this;
        while (current.getParentId() != null) {
            ancestors.add(current.getParentId());
            current = nodeMap.get(current.getParentId());
            if (current == null) {
                break;
            }
        }
        return ancestors;
    }

    /**
     * 复制当前节。（浅拷贝。
     *
     * @return 复制的新节。
     */
    public T copy() {
        try {
            T newInstance = (T) this.getClass().getDeclaredConstructor().newInstance();
            copyFieldsTo(newInstance);
            return newInstance;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(
                "Failed to copy TreeNode. Subclass must have a public no-arg constructor: "
                + this.getClass().getName(), e);
        }
    }
    
    /**
     * 复制字段到目标节。（浅拷贝辅助方法）
     *
     * @param target 目标节。
     */
    protected void copyFieldsTo(T target) {
        target.setId(this.id);
        target.setParentId(this.parentId);
        target.setSort(this.sort);
        target.setLevel(this.level);
        target.setPath(this.path);
        target.setLeaf(this.leaf);
        target.setChildren(new ArrayList<>());
    }

    /**
     * 获取当前节。所在树的根节。
     *
     * @param nodeList 所有节。列表
     * @return 根节。
     */
    public T getRoot(List<T> nodeList) {
        if (isRootNode()) {
            return (T) this;
        }
        // 构建 ID -> Node 索引，将 O(n²) 降为 O(n)
        Map<ID, T> nodeMap = new HashMap<>();
        if (nodeList != null) {
            for (T node : nodeList) {
                if (node != null && node.getId() != null) {
                    nodeMap.put(node.getId(), node);
                }
            }
        }
        T current = (T) this;
        while (!((TreeNode<T, ID>) current).isRootNode()) {
            final ID parentId = current.getParentId();
            T parent = nodeMap.get(parentId);
            if (parent == null || ((TreeNode<T, ID>) parent).isRootNode()) {
                current = parent != null ? parent : current;
                break;
            }
            current = parent;
        }
        return current;
    }
}