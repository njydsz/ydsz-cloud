package com.njydsz.userinfo.infra.repository;

import java.time.LocalDateTime;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.UserLoginHistoryDTO;
import com.njydsz.userinfo.domain.repository.UserLoginHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserLoginHistoryVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.entity.UserLoginHistoryDO;
import com.njydsz.userinfo.infra.mapper.UserLoginHistoryMapper;

/**
 * 用户登录历史 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserLoginHistoryMapper} 实现登录历史的数据访问。
 * 所有返回值通过 {@link UserInfoConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserLoginHistoryRepositoryImpl implements UserLoginHistoryRepository {

  private final UserLoginHistoryMapper userLoginHistoryMapper;
  private final UserInfoConverter converter;

  @Override
  public UserLoginHistoryVO create(UserLoginHistoryDTO dto) {
    UserLoginHistoryDO entity = converter.dtoToEntity(dto);
    userLoginHistoryMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int countRecentFailures(String userId, int windowMinutes) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistoryDO::getUserId, userId);
    wrapper.eq(UserLoginHistoryDO::getLoginResult, "FAILED");
    wrapper.ge(UserLoginHistoryDO::getCreatedAt,
        java.time.LocalDateTime.now().minusMinutes(windowMinutes));
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryVO> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistoryDO::getUserId, userId);
    wrapper.orderByDesc(UserLoginHistoryDO::getCreatedAt);
    wrapper.last("LIMIT " + Math.min(limit, 100));
    List<UserLoginHistoryDO> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public List<UserLoginHistoryVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserLoginHistoryDO::getUserId, userId);
    List<UserLoginHistoryDO> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }

  @Override
  public long countByResultAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String result) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(UserLoginHistoryDO::getCreatedAt, startTime);
    wrapper.lt(UserLoginHistoryDO::getCreatedAt, endTime);
    if (result != null) {
      wrapper.eq(UserLoginHistoryDO::getLoginResult, result);
    }
    return userLoginHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int countByFailReasonAndTimeRange(
      LocalDateTime startTime, LocalDateTime endTime, String failReason) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(UserLoginHistoryDO::getCreatedAt, startTime);
    wrapper.lt(UserLoginHistoryDO::getCreatedAt, endTime);
    wrapper.eq(UserLoginHistoryDO::getLoginResult, "FAILED");
    if (failReason != null) {
      wrapper.eq(UserLoginHistoryDO::getFailReason, failReason);
    }
    return Math.toIntExact(userLoginHistoryMapper.selectCount(wrapper));
  }

  @Override
  public List<UserLoginHistoryVO> findRecentFailedLogins(LocalDateTime since, int limit) {
    LambdaQueryWrapper<UserLoginHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.ge(UserLoginHistoryDO::getCreatedAt, since);
    wrapper.eq(UserLoginHistoryDO::getLoginResult, "FAILED");
    wrapper.orderByDesc(UserLoginHistoryDO::getCreatedAt);
    wrapper.last("LIMIT " + Math.min(limit, 100));
    List<UserLoginHistoryDO> entities = userLoginHistoryMapper.selectList(wrapper);
    return converter.userLoginHistoryListToVO(entities);
  }
}
