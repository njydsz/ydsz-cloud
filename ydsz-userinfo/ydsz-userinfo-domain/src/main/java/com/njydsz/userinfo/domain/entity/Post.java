package com.njydsz.userinfo.domain.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 岗位实体
 *
 * <p>对应数据库表 {@code ydsz_post}，存储组织中的「岗位」节点（区别于部门、角色）。 岗位是「职责维度」，描述「这个人做什么事」（如 PM、DEV、QA、SA），
 * 而角色是「权限维度」，描述「这个人能做什么」。
 *
 * <p><b>岗位 vs 部门 vs 角色：</b>
 *
 * <ul>
 *   <li>部门（{@link Department}）：组织归属，回答「你属于哪个团队」
 *   <li>岗位（{@link Post}）：职责描述，回答「你负责什么工作」
 *   <li>角色（{@link Role}）：权限集合，回答「你能操作哪些功能」
 * </ul>
 *
 * 一个用户可同时属于多个部门、担任多个岗位、拥有多个角色，三者正交。
 *
 * <p><b>典型使用：</b>
 *
 * <ul>
 *   <li>审批人展开：{@code UserAccount.positionCode} + {@code position:xxx}
 *   <li>工时统计：按岗位统计工作量与产出
 *   <li>汇报关系：岗位决定审批链中的角色定位
 * </ul>
 *
 * <p><b>索引设计：</b>唯一索引 {@code uk_post_code}（{@code post_code}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserPost 用户-岗位中间表
 * @see UserAccount 用户实体（含 {@code positionCode} 字段）
 * @see com.njydsz.userinfo.web.controller.PostController 岗位 Controller
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_post")
public class Post extends MpBaseEntity<String> {

  /** 岗位名称（前端展示，如「项目经理」「后端开发工程师」） */
  private String postName;

  /**
   * 岗位编码（业务侧引用，全局唯一）。
   *
   * <p>建议使用稳定的英文枚举值（如 {@code PM}/{@code DEV}/{@code QA}/{@code SA}）， 便于跨系统交互（如审批人展开、工作流节点选择）。
   */
  private String postCode;

  /** 岗位描述（说明岗位的工作职责与任职要求） */
  private String description;

  /** 同级排序序号（升序） */
  private Integer sortOrder;

  /**
   * 启用状态（{@code "ENABLED"} / {@code "DISABLED"}）
   *
   * <p>禁用后，岗位不可再被分配给新用户，但现有用户的岗位关联不受影响。
   */
  private String status;
}
