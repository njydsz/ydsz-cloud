package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.json.annotation.JsonIgnore;

/**
 * 树节点基础类
 *
 * <p>用于构建树形结构数据，如组织架构、菜单分类、商品分类、区域管理等场景。
 * 支持泛型继承，允许自定义扩展节点属性。
 *
 * <p><b>字段说明：</b>
 * <table>
 *   <tr><th>字段</th><th>类型</th><th>说明</th></tr>
 *   <tr><td>id</td><td>T</td><td>当前节点唯一标识</td></tr>
 *   <tr><td>parentId</td><td>T</td><td>父节点ID，顶级节点为null</td></tr>
 *   <tr><td>children</td><td>List&lt;T&gt;</td><td>子节点列表</td></tr>
 *   <tr><td>sort</td><td>Integer</td><td>排序字段，同级节点排序用</td></tr>
 *   <tr><td>level</td><td>Integer</td><td>节点层级深度，根节点为 1</td></tr>
 *   <tr><td>path</td><td>String</td><td>节点路径，如 "/1/2/3/"</td></tr>
 *   <tr><td>leaf</td><td>Boolean</td><td>是否为叶子节点</td></tr>
 * </table>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 定义菜单树节点
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
 * }</pre>
 *
 * <p><b>泛型安全说明：</b>此类使用了递归泛型模式 {@code TreeNode<T extends TreeNode<T, ID>, ID>}。
 * 内部存在 {@code (T) this} 的未经检查强转。由于 Java 类型擦除机制，{@code ClassCastException}
 * 不会在强转时立即抛出，而是在返回值被使用时才可能触发。使用时必须确保泛型参数 {@code T}
 * 与具体子类类型一致，例如 {@code class Menu extends TreeNode<Menu, Long>} 是安全的。
 *
 * @param <T>  继承自TreeNode的具体类型
 * @param <ID> ID类型
 *
 * @author ydsz-team
 * @since 1.0.0
 * @since 1.4.0 精简：移除 traverseDFS/traverseBFS/copy/cloneSubTree/moveTo/getAncestorIds/
 *              getRoot/getDescendantCount 等鲜有使用的 API，降低心智负担
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
     * 根节点层级深度
     */
    public static final int ROOT_LEVEL = 1;

    /**
     * 当前节点唯一标识
     */
    @Setter
    private ID id;

    /**
     * 父节点ID
     *
     * <p>顶级节点的父ID为null。
     * 也可使用特定值（如 0 或 -1）作为根节点标识。
     */
    @Setter
    private ID parentId;

    /**
     * 子节点列表
     *
     * <p>存储属于当前节点的所有直接子节点。
     * 空列表表示叶子节点。
     */
    @Builder.Default
    @Setter
    private List<T> children = new ArrayList<>();

    /**
     * 排序字段
     *
     * <p>用于同级别节点的排序，数值越小排序越靠前。
     * 支持 null 值，null 值排在最后。
     */
    @Setter
    private Integer sort;

    /**
     * 节点层级深度
     *
     * <p>从根节点开始计算深度：
     * <ul>
     *   <li>根节点：level = 1</li>
     *   <li>二级节点：level = 2</li>
     *   <li>以此类推...</li>
     * </ul>
     */
    @Builder.Default
    @Setter
    private Integer level = ROOT_LEVEL;

    /**
     * 节点路径
     *
     * <p>格式：从根节点到当前节点的完整路径。
     * 例如 "/1/2/3/" 表示 id=3 的节点，其父id=2，父父id=1。
     * 可用于快速判断节点归属关系。
     */
    @Setter
    private String path;

    /**
     * 是否为叶子节点
     *
     * <p>由 TreeBuilder 自动计算：
     * <ul>
     *   <li>true：无子节点</li>
     *   <li>false：有子节点</li>
     * </ul>
     */
    @Builder.Default
    @Setter
    private Boolean leaf = true;

    /**
     * 添加子节点
     *
     * @param child 子节点实体
     * @return 当前节点，支持链式调用
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
     * 添加多个子节点
     *
     * @param childList 子节点列表
     * @return 当前节点，支持链式调用
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
     * 判断是否为根节点
     *
     * @param rootId 根节点ID
     * @return 如果是根节点返回 true
     */
    public boolean isRootNode(ID rootId) {
        if (rootId == null) {
            return parentId == null;
        }
        return Objects.equals(rootId, parentId);
    }

    /**
     * 判断是否为根节点（无参数版本）
     *
     * <p>判断 parentId 是否为 null。
     *
     * @return parentId为null返回true
     */
    public boolean isRootNode() {
        return parentId == null;
    }

    /**
     * 判断是否为叶子节点
     *
     * @return 如果没有子节点返回true
     */
    public boolean isLeaf() {
        return children == null || children.isEmpty();
    }

    /**
     * 获取子节点数量
     *
     * @return 直接子节点数量
     */
    @JsonIgnore
    public int getChildCount() {
        return children != null ? children.size() : 0;
    }

    /**
     * 判断是否包含指定子节点
     *
     * @param childId 子节点ID
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
     * 迭代查找指定节点（避免递归栈溢出）
     *
     * @param targetId 目标节点ID
     * @return 找到返回节点，否则返回null
     */
    public T findById(ID targetId) {
        if (Objects.equals(this.id, targetId)) {
            return (T) this;
        }
        if (children == null || children.isEmpty()) {
            return null;
        }
        // 使用栈进行深度优先搜索，避免递归调用栈溢出
        Deque<T> stack = new ArrayDeque<>();
        stack.push((T) this);
        while (!stack.isEmpty()) {
            T node = stack.pop();
            if (Objects.equals(node.getId(), targetId)) {
                return node;
            }
            List<T> nodeChildren = node.getChildren();
            if (nodeChildren != null && !nodeChildren.isEmpty()) {
                // 逆序压栈，保证左侧子节点先被处理
                for (int i = nodeChildren.size() - 1; i >= 0; i--) {
                    stack.push(nodeChildren.get(i));
                }
            }
        }
        return null;
    }
}
