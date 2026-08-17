package com.njydsz.system.domain.common;

import java.util.List;

/**
 * 树形节点接口。
 *
 * <p>定义树形结构的通用契约，任何需要支持树形展示的实体 VO 可实现此接口。
 *
 * <p>典型实现：
 *
 * <pre>{@code
 * public class DictItemVO implements TreeNode<String> {
 *     private String id;
 *     private String parentId;
 *     private List<DictItemVO> children;
 *     // ...
 * }
 * }</pre>
 *
 * @param <ID> 节点 ID 类型（通常为 String 或 Long）
 * @author ydsz-team
 * @since 1.0.0
 */
public interface TreeNode<ID> {

  /** 获取节点唯一标识 */
  ID getId();

  /** 获取父级节点 ID，根节点返回 null 或 "0" */
  ID getParentId();

  /** 获取子节点列表 */
  List<? extends TreeNode<ID>> getChildren();

  /** 设置子节点列表 */
  void setChildren(List<? extends TreeNode<ID>> children);
}
