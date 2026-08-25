package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户-岗位关联实体
 *
 * <p>对应数据库表 {@code ydsz_user_post}，是连接用户与岗位的多对多中间表。 一个用户可同时担任多个岗位（PM + SA），一个岗位可被多个用户承担。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>采用「关系实体」模式（带审计字段），便于追溯岗位分配历史
 *   <li>主岗位逻辑由 {@link UserAccount#getPositionCode()} 维护（非本中间表）， 避免主岗位/兼岗位混淆
 *   <li>由 {@code UserPostController.assignPosts} 接口维护（批量分配）
 * </ul>
 *
 * <p><b>与 {@link UserAccount#positionCode} 的关系：</b>
 *
 * <ul>
 *   <li>{@code UserAccount.positionCode}：单值字段，记录用户的「主岗位」（{@code PM}）
 *   <li>{@code UserPost} 中间表：多值关系，记录用户的「所有岗位」（{@code PM} + {@code SA}）
 * </ul>
 *
 * 实际审批人展开时按 {@code position:PM} 触发时，匹配 {@code positionCode} 或中间表任一岗位。
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 查询用户所有岗位 ID
 * List<String> postIds = userPostMapper.selectList(
 *     new LambdaQueryWrapper<UserPost>().eq(UserPost::getUserId, userId)
 * ).stream().map(UserPost::getPostId).collect(Collectors.toList());
 * }</pre>
 *
 * <p><b>索引设计：</b>普通索引 {@code idx_user_id}（{@code user_id}）、 {@code idx_post_id}（{@code post_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserAccount 用户实体
 * @see Post 岗位实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_user_post")
@SuppressWarnings("unchecked")
public class UserPost extends MpBaseEntity<String> {

  /** 用户 ID，关联 {@link UserAccount#getId()} */
  private String userId;

  /** 岗位 ID，关联 {@link Post#getId()} */
  private String postId;
}
