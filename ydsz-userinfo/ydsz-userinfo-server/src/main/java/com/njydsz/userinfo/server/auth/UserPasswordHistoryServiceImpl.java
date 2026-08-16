package com.njydsz.userinfo.server.auth;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.njydsz.common.util.id.SnowflakeIdGenerator;
import com.njydsz.userinfo.domain.entity.UserPasswordHistory;
import com.njydsz.userinfo.infra.mapper.UserPasswordHistoryMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 密码历史服务实现
 *
 * <p>提供密码历史记录的持久化和比对逻辑。
 *
 * <p><b>性能考虑：</b>
 *
 * <ul>
 *   <li>BCrypt 比对耗时约 100ms（cost=10），历史密码比对相当于 N 次 BCrypt 校验</li>
 *   <li>建议 historyCount ≤ 5，避免影响用户体验</li>
 *   <li>比对场景仅在密码修改/重置时触发，非高频接口</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPasswordHistoryServiceImpl implements UserPasswordHistoryService {

  private final UserPasswordHistoryMapper passwordHistoryMapper;
  private final PasswordEncoder passwordEncoder;
  private final SnowflakeIdGenerator snowflakeIdGenerator;

  @Override
  public boolean isPasswordReused(String userId, String newPassword, int historyCount) {
    if (userId == null || newPassword == null || historyCount <= 0) {
      return false;
    }

    // 查询最近 N 条历史密码（按创建时间倒序）
    LambdaQueryWrapper<UserPasswordHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper
        .eq(UserPasswordHistory::getUserId, userId)
        .orderByDesc(UserPasswordHistory::getCreatedAt)
        .last("LIMIT " + historyCount);

    List<UserPasswordHistory> historyList = passwordHistoryMapper.selectList(wrapper);

    // 逐条比对（BCrypt matches）
    for (UserPasswordHistory history : historyList) {
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
    UserPasswordHistory record = new UserPasswordHistory();
    record.setId(String.valueOf(snowflakeIdGenerator.nextId()));
    record.setUserId(userId);
    record.setPasswordHash(passwordHash);
    record.setCreatedAt(LocalDateTime.now());
    record.setDeleted(0);
    passwordHistoryMapper.insert(record);

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
    LambdaQueryWrapper<UserPasswordHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistory::getUserId, userId);
    passwordHistoryMapper.delete(wrapper);
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
    LambdaQueryWrapper<UserPasswordHistory> countWrapper = new LambdaQueryWrapper<>();
    countWrapper.eq(UserPasswordHistory::getUserId, userId);
    int totalCount = Math.toIntExact(passwordHistoryMapper.selectCount(countWrapper));

    if (totalCount <= keepCount) {
      return;
    }

    // 查询需要删除的 ID（超出 keepCount 的旧记录）
    LambdaQueryWrapper<UserPasswordHistory> deleteWrapper = new LambdaQueryWrapper<>();
    deleteWrapper
        .eq(UserPasswordHistory::getUserId, userId)
        .orderByDesc(UserPasswordHistory::getCreatedAt)
        .last("LIMIT 100 OFFSET " + keepCount);

    List<UserPasswordHistory> oldRecords = passwordHistoryMapper.selectList(deleteWrapper);
    if (!oldRecords.isEmpty()) {
      List<String> idsToDelete =
          oldRecords.stream()
              .map(UserPasswordHistory::getId)
              .collect(Collectors.toList());
      passwordHistoryMapper.deleteBatchIds(idsToDelete);
      log.debug("Cleaned up {} old password records for user: {}", oldRecords.size(), userId);
    }
  }
}
