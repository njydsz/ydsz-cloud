package com.njydsz.userinfo.server.auth;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.infra.entity.UserPasswordHistoryDO;
import com.njydsz.userinfo.infra.repository.UserPasswordHistoryRepository;

/**
 * 密码历史服务实现
 *
 * <p>提供密码历史记录的持久化和比对逻辑。
 *
 * <p><b>性能考虑：</b>
 *
 * <ul>
 *   <li>BCrypt 比对耗时约 100ms（cost=10），历史密码比对相当于 N 次 BCrypt 校验
 *   <li>建议 historyCount ≤ 5，避免影响用户体验
 *   <li>比对场景仅在密码修改/重置时触发，非高频接口
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPasswordHistoryServiceImpl implements UserPasswordHistoryService {

  private final UserPasswordHistoryRepository passwordHistoryRepository;
  private final PasswordEncoder passwordEncoder;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  @Override
  public boolean isPasswordReused(String userId, String newPassword, int historyCount) {
    if (userId == null || newPassword == null || historyCount <= 0) {
      return false;
    }

    // 查询最近 N 条历史密码（按创建时间倒序）
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(UserPasswordHistoryDO::getUserId, userId)
        .orderByDesc(UserPasswordHistoryDO::getCreatedAt)
        .last("LIMIT " + historyCount);

    List<UserPasswordHistoryDO> historyList = passwordHistoryRepository.list(wrapper);

    // 逐条比对（BCrypt matches）
    for (UserPasswordHistoryDO history : historyList) {
      if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
        log.info("Password reuse detected for user: {}", userId);
        return true;
      }
    }
    return false;
  }

  @Override
  public void recordPasswordHistory(String userId, String passwordHash, int historyCount) {
    if (userId == null || passwordHash == null) {
      return;
    }

    // 插入新记录
    UserPasswordHistoryDO record = new UserPasswordHistoryDO();
    record.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    record.setUserId(userId);
    record.setPasswordHash(passwordHash);
    record.setCreatedAt(LocalDateTime.now());
    record.setDeleted(0);
    passwordHistoryRepository.insert(record);

    log.info("Password history recorded for user: {}", userId);

    // 清理超出限制的旧记录（保留最近 N 条）
    if (historyCount > 0) {
      cleanupOldRecords(userId, historyCount);
    }
  }

  @Override
  public void clearHistoryByUserId(String userId) {
    if (userId == null) {
      return;
    }
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    passwordHistoryRepository.delete(wrapper);
    log.info("Password history cleared for user: {}", userId);
  }

  /**
   * 清理超出保留数量的旧密码历史记录
   *
   * <p>保留最近 {@code keepCount} 条记录，删除更早的记录。
   *
   * @param userId 用户 ID
   * @param keepCount 保留的条数
   */
  private void cleanupOldRecords(String userId, int keepCount) {
    // 查询需要删除的旧记录 ID
    LambdaQueryWrapper<UserPasswordHistoryDO> countWrapper = new LambdaQueryWrapper<>();
    countWrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    int totalCount = Math.toIntExact(passwordHistoryRepository.count(countWrapper));

    if (totalCount <= keepCount) {
      return;
    }

    // 查询需要删除的 ID（超出 keepCount 的旧记录）
    LambdaQueryWrapper<UserPasswordHistoryDO> deleteWrapper = new LambdaQueryWrapper<>();
    deleteWrapper
        .eq(UserPasswordHistoryDO::getUserId, userId)
        .orderByDesc(UserPasswordHistoryDO::getCreatedAt)
        .last("LIMIT 100 OFFSET " + keepCount);

    List<UserPasswordHistoryDO> oldRecords = passwordHistoryRepository.list(deleteWrapper);
    if (!oldRecords.isEmpty()) {
      List<String> idsToDelete =
          oldRecords.stream().map(UserPasswordHistoryDO::getId).collect(Collectors.toList());
      passwordHistoryRepository.deleteByIds(idsToDelete);
      log.debug("Cleaned up {} old password records for user: {}", oldRecords.size(), userId);
    }
  }
}
