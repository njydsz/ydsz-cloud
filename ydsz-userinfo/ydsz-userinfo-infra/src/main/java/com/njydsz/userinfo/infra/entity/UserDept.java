package com.njydsz.userinfo.infra.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import com.njydsz.common.jdbc.entity.MpBaseEntity;

/**
 * 用户-部门关联实体
 *
 * <p>对应数据库表 {@code ydsz_user_dept}，是连接用户与部门的多对多中间表。 支持用户兼职多个部门（兼岗），通过 {@link #isPrimary} 字段标识主部门。
 *
 * <p><b>设计要点：</b>
 *
 * <ul>
 *   <li>一个用户可有多个部门，但只能有 1 个主部门（{@code isPrimary=1}）
 *   <li>主部门用于：① 默认审批人；② 默认工时归属；③ 默认数据范围
 *   <li>辅部门用于：① 跨部门项目协作；② 多部门审批人展开
 * </ul>
 *
 * <p><b>主部门唯一性约束：</b>应在 Service 层校验「同一用户只允许一个主部门」， 通过事务保证 + 加锁实现。SQL 层可通过部分唯一索引实现：
 *
 * <pre>{@code
 * CREATE UNIQUE INDEX uk_user_primary_dept ON ydsz_user_dept (user_id) WHERE is_primary = 1;
 * }</pre>
 *
 * <p><b>典型使用：</b>
 *
 * <pre>{@code
 * // 查询用户主部门 ID
 * String primaryDeptId = userDeptMapper.selectOne(
 *     new LambdaQueryWrapper<UserDept>()
 *         .eq(UserDept::getUserId, userId)
 *         .eq(UserDept::getIsPrimary, 1)
 * ).getDeptId();
 * }</pre>
 *
 * <p><b>索引设计：</b>普通索引 {@code idx_user_id}（{@code user_id}）、 {@code idx_dept_id}（{@code dept_id}）。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see UserAccount 用户实体
 * @see Department 部门实体
 */
@Data
@SuperBuilder
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@TableName("ydsz_acct_user_dept")
public class UserDept extends MpBaseEntity<String> {

  /** 用户 ID，关联 {@link UserAccount#getId()} */
  private String userId;

  /** 部门 ID，关联 {@link Department#getId()} */
  private String deptId;

  /**
   * 是否主部门。
   *
   * <p>{@code 1=是}、{@code 0=否}。一个用户只能有一个主部门，由 Service 层事务保证。
   */
  private Integer isPrimary;
}
