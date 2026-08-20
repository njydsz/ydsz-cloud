package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.UserPasswordHistoryDTO;
import com.njydsz.userinfo.domain.repository.UserPasswordHistoryRepository;
import com.njydsz.userinfo.domain.vo.UserPasswordHistoryVO;
import com.njydsz.userinfo.infra.converter.UserInfoUserConverter;
import com.njydsz.userinfo.infra.entity.UserPasswordHistoryDO;
import com.njydsz.userinfo.infra.mapper.UserPasswordHistoryMapper;

/**
 * 密码历史 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserPasswordHistoryMapper} 实现密码历史的数据访问。
 * 所有返回值通过 {@link UserInfoUserConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserPasswordHistoryRepositoryImpl implements UserPasswordHistoryRepository {

  private final UserPasswordHistoryMapper userPasswordHistoryMapper;
  private final UserInfoUserConverter converter;

  @Override
  public UserPasswordHistoryVO create(UserPasswordHistoryDTO dto) {
    UserPasswordHistoryDO entity = converter.dtoToEntity(dto);
    userPasswordHistoryMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public List<UserPasswordHistoryVO> findRecentByUserId(String userId, int limit) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    wrapper.orderByDesc(UserPasswordHistoryDO::getCreatedAt);
    wrapper.last("LIMIT " + limit);
    List<UserPasswordHistoryDO> entities = userPasswordHistoryMapper.selectList(wrapper);
    return converter.userPasswordHistoryListToVO(entities);
  }

  @Override
  public List<UserPasswordHistoryVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    List<UserPasswordHistoryDO> entities = userPasswordHistoryMapper.selectList(wrapper);
    return converter.userPasswordHistoryListToVO(entities);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    return userPasswordHistoryMapper.delete(wrapper);
  }

  @Override
  public long countByUserId(String userId) {
    LambdaQueryWrapper<UserPasswordHistoryDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPasswordHistoryDO::getUserId, userId);
    return userPasswordHistoryMapper.selectCount(wrapper);
  }

  @Override
  public int deleteByIds(Collection<String> ids) {
    return userPasswordHistoryMapper.deleteBatchIds(ids);
  }
}
