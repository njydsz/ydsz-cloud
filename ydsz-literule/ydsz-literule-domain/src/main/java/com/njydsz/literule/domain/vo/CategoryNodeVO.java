package com.njydsz.literule.domain.vo;

import lombok.Data;

/**
 * 规则分类树节点视图对象（VO）。
 *
 * <p>用于前端以树形结构展示规则分类（如按行业/模块分组）。 每个节点包含名称、层级路径、是否根节点及下属规则数量。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
public class CategoryNodeVO {

  /** 分类节点名称（展示用） */
  private String name;

  /** 分类层级路径（如 /行业/模块/子模块，用于回溯父级） */
  private String path;

  /** 节点深度（根节点为 0 或 1，逐级递增） */
  private int depth;

  /** 是否根节点（true=顶层分类） */
  private boolean root;

  /** 该分类下的规则数量（含下级或仅本级，取决于聚合口径） */
  private int ruleCount;
}
