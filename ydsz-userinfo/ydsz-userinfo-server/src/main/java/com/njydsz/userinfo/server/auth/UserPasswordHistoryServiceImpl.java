package com.njydsz.userinfo.server.auth;

import java.util.List;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.dto.UserPasswordHistoryDTO;
import com.njydsz.userinfo.domain.repository.UserPasswordHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserPasswordHistoryVO;

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
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPasswordHistoryServiceImpl implements UserPasswordHistoryService {

  private final UserPasswordHistoryRepository passwordHistoryRepository;
  private final PasswordEncoder passwordEncoder;

  @Override
  public boolean isPasswordReused(String userId, String newPassword, int historyCount) {
    if (userId == null || newPassword == null || historyCount <= 0) {
      return false;
    }

    // 查询最近 N 条历史密码（按创建时间倒序）
    List<UserPasswordHistoryVO> historyList =
        passwordHistoryRepository.findRecentByUserId(userId, historyCount);

    // 逐条比对（BCrypt matches）
    for (UserPasswordHistoryVO history : historyList) {
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
    UserPasswordHistoryDTO dto = new UserPasswordHistoryDTO();
    dto.setUserId(userId);
    dto.setPasswordHash(passwordHash);
    passwordHistoryRepository.create(dto);

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
    passwordHistoryRepository.deleteByUserId(userId);
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
    // 查询该用户全部密码历史
    List<UserPasswordHistoryVO> allRecords = passwordHistoryRepository.findByUserId(userId);

    if (allRecords.size() <= keepCount) {
      return;
    }

    // 跳过最近 keepCount 条，收集需要删除的 ID
    List<String> idsToDelete =
        allRecords.stream()
            .skip(keepCount)
            .map(UserPasswordHistoryVO::getId)
            .collect(Collectors.toList());

    if (!idsToDelete.isEmpty()) {
      passwordHistoryRepository.deleteByIds(idsToDelete);
      log.debug("Cleaned up {} old password records for user: {}", idsToDelete.size(), userId);
    }
  }
}
