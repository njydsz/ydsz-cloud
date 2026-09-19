package com.njydsz.common.domain.tree;

import java.io.Serializable;
import java.util.List;

/**
 * 树节点懒加载 SPI（大数据量场景使用）。
 *
 * <p>当树的节点数量超过万级时，一次性加载全部节点构建树会导致内存压力和构建耗时增加。
 * 通过此 SPI，调用方可按需加载子节点，仅在用户展开某个节点时才加载该节点的子级数据。
 *
 * <p><b>注册方式：</b>业务模块通过 {@code @Component} 注册自定义实现。
 *
 * <p><b>使用示例：</b>
 *
 * <pre>{@code
 * // 业务模块实现：从数据库按需加载子节点
 * public class MenuTreeNodeProvider implements TreeNodeProvider<Menu, Long> {
 *     private final MenuMapper menuMapper;
 *
 *     public MenuTreeNodeProvider(MenuMapper menuMapper) {
 *         this.menuMapper = menuMapper;
 *     *
 *     @Override
 *     public List<Menu> loadChildren(Long parentId) {
 *         return menuMapper.selectByParentId(parentId);
 *     }
 * }
 *
 * // 使用懒加载构建树
 * List<Menu> roots = TreeBuilder.buildLazy(rootId, provider, maxDepth);
 * }</pre>
 *
 * @param <T> 继承自 TreeNode 的具体类型
 * @param <ID> ID 类型
 * @author ydsz-team
 * @since 26.09.19
 * @see TreeBuilder
 */
@FunctionalInterface
public interface TreeNodeProvider<T extends TreeNode<T, ID>, ID extends Serializable> {

  /**
   * 加载指定父节点下的子节点列表。
   *
   * @param parentId 父节点 ID
   * @return 子节点列表（可以为空列表，不可返回 null）
   */
  List<T> loadChildren(ID parentId);
}
