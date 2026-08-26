package com.njydsz.userinfo.domain.repository;

import java.util.Collection;
import java.util.List;

import com.njydsz.userinfo.domain.dto.UserPasswordHistoryDTO;
import com.njydsz.userinfo.domain.vo.UserPasswordHistoryVO;

/**
 * 密码历史 Repository 接口
 *
 * <p>封装密码历史表（{@code ydsz_acct_password_history}）的数据访问操作。
 *
 * <p>入参为 DTO / 具体字段，返回值为 VO 类型，禁止暴露 MyBatis-Plus 类。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserPasswordHistoryRepository {

  /**
   * 保存密码历史记录（插入）。
   *
   * @param dto 密码历史 DTO
   * @return 保存后的密码历史 VO
   */
  UserPasswordHistoryVO create(UserPasswordHistoryDTO dto);

  /**
   * 查询用户最近的密码历史记录。
   *
   * @param userId 用户 ID
   * @param limit 返回记录数上限
   * @return 密码历史列表
   */
  List<UserPasswordHistoryVO> findRecentByUserId(String userId, int limit);

  /**
   * 根据用户 ID 查询全部密码历史。
   *
   * @param userId 用户 ID
   * @return 密码历史列表
   */
  List<UserPasswordHistoryVO> findByUserId(String userId);

  /**
   * 根据用户 ID 删除全部密码历史。
   *
   * @param userId 用户 ID
   * @return 删除影响的行数
   */
  int deleteByUserId(String userId);

  /**
   * 统计用户的密码历史数量。
   *
   * @param userId 用户 ID
   * @return 密码历史数量
   */
  long countByUserId(String userId);

  /**
   * 批量删除指定 ID 的密码历史记录。
   *
   * @param ids 记录 ID 列表
   * @return 删除影响的行数
   */
  int deleteByIds(Collection<String> ids);
}
