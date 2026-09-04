package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.domain.converter.UserInfoUserConverter;
import com.njydsz.userinfo.domain.entity.UserLoginHistory;
import com.njydsz.userinfo.infra.mapper.UserLoginHistoryMapper;

/**
 * 用户登录历史 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserLoginHistoryMapper} 实现登录历史的数据访问。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class UserLoginHistoryRepositoryImpl implements UserLoginHistoryRepository {

  private final UserLoginHistoryMapper userLoginHistoryMapper;
  private final UserInfoUserConverter converter;

  @Override
  public UserLoginHistoryVO create(UserLoginHistoryDTO dto) {
    UserLoginHistory entity = converter.dtoToEntity(dto);
    userLoginHistoryMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int countRecentFailures(String userId, int windowMinutes) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    wrapper.ge(UserLoginHistory::getCreatedAt,
        LocalDateTime.now().minusMinutes(windowMinutes));
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryVO> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    wrapper.orderByDesc(UserLoginHistory::getCreatedAt);
    wrapper.last("LIMIT " + Math.min(limit, 100));
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public List<UserLoginHistoryVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistory::getUserId, userId);
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public long countByResultAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String result) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(UserLoginHistory::getCreatedAt, startTime);
    wrapper.lt(UserLoginHistory::getCreatedAt, endTime);
    if (result != null) {
      wrapper.eq(UserLoginHistory::getLoginResult, result);
    }
    return userLoginHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int countByFailReasonAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String failReason) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(UserLoginHistory::getCreatedAt, startTime);
    wrapper.lt(UserLoginHistory::getCreatedAt, endTime);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    if (failReason != null) {
      wrapper.eq(UserLoginHistory::getFailReason, failReason);
    }
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryVO> findRecentFailedLogins(LocalDateTime since, int limit) {
    LambdaQueryWrapper<UserLoginHistory> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(UserLoginHistory::getCreatedAt, since);
    wrapper.eq(UserLoginHistory::getLoginResult, "FAILED");
    wrapper.orderByDesc(UserLoginHistory::getCreatedAt);
    wrapper.last("LIMIT " + Math.min(limit, 100));
    List<UserLoginHistory> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public long countDistinctUsersWithFailures(LocalDateTime startTime, LocalDateTime endTime) {
    return userLoginHistoryMapper.countDistinctUsersWithFailures(startTime, endTime);
  }
}
