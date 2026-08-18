package com.njydsz.userinfo.infra.repository;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.dto.UserPostDTO;
import com.njydsz.userinfo.domain.repository.UserPostRepository;
import com.njydsz.userinfo.domain.vo.UserPostVO;
import com.njydsz.userinfo.infra.converter.UserInfoConverter;
import com.njydsz.userinfo.infra.entity.UserPostDO;
import com.njydsz.userinfo.infra.mapper.UserPostMapper;

/**
 * 用户-岗位关联 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link UserPostMapper} 实现用户-岗位关联的数据访问。
 * 所有返回值通过 {@link UserInfoConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class UserPostRepositoryImpl implements UserPostRepository {

  private final UserPostMapper userPostMapper;
  private final UserInfoConverter converter;

  @Override
  public Optional<UserPostVO> findById(String id) {
    UserPostDO entity = userPostMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public List<UserPostVO> findByUserId(String userId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    List<UserPostDO> entities = userPostMapper.selectList(wrapper);
    return converter.userPostListToVO(entities);
  }

  @Override
  public List<String> findPostIdsByUserId(String userId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    return userPostMapper.selectList(wrapper).stream()
        .map(UserPostDO::getPostId)
        .collect(Collectors.toList());
  }

  @Override
  public Optional<UserPostVO> findByUserIdAndPostId(String userId, String postId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    wrapper.eq(UserPostDO::getPostId, postId);
    UserPostDO entity = userPostMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public UserPostVO create(UserPostDTO dto) {
    UserPostDO entity = converter.dtoToEntity(dto);
    userPostMapper.insert(entity);
    return converter.entityToVO(entity);
  }

  @Override
  public int deleteByUserId(String userId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    return userPostMapper.delete(wrapper);
  }

  @Override
  public int deleteByUserIdAndPostId(String userId, String postId) {
    LambdaQueryWrapper<UserPostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(UserPostDO::getUserId, userId);
    wrapper.eq(UserPostDO::getPostId, postId);
    return userPostMapper.delete(wrapper);
  }

  @Override
  public boolean deleteById(String id) {
    return userPostMapper.deleteById(id) > 0;
  }
}
