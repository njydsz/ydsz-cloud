package com.njydsz.userinfo.server.auth;

/**
 * 密码历史服务接口
 *
 * <p>提供密码历史记录的管理能力，防止用户短期内重复使用旧密码。
 *
 * <p><b>核心能力：</b>
 *
 * <ul>
 *   <li>校验新密码是否与最近 N 条历史密码重复
 *   <li>记录新密码到历史表
 *   <li>清理超出保留数量的历史记录
 *   <li>删除用户时同步清理历史记录
 * </ul>
 *
 * @author ydsz-team
 * @since 26.09.01
 */
public interface UserPasswordHistoryService {

  /**
   * 校验密码是否与历史密码重复
   *
   * <p>查询用户最近 {@code historyCount} 条历史密码记录，使用 BCrypt 逐条比对。 BCrypt 每次加密结果不同，因此无法通过字符串相等比较，需使用 {@code
   * matches} 校验。
   *
   * @param userId 用户 ID
   * @param newPassword 新密码（明文）
   * @param historyCount 需要检查的历史密码条数
   * @return true 表示与历史密码重复；false 表示未重复
   */
  boolean isPasswordReused(String userId, String newPassword, int historyCount);

  /**
   * 记录新密码到历史表
   *
   * <p>修改密码成功后调用，将新密码的 BCrypt 哈希存入历史表，并清理超出限制的旧记录。
   *
   * @param userId 用户 ID
   * @param passwordHash 密码的 BCrypt 哈希值
   * @param historyCount 保留的历史密码条数上限
   */
  void recordPasswordHistory(String userId, String passwordHash, int historyCount);

  /**
   * 清理用户的历史密码记录
   *
   * <p>用户删除时调用，物理删除所有历史记录以避免敏感数据残留。
   *
   * @param userId 用户 ID
   */
  void clearHistoryByUserId(String userId);
}
