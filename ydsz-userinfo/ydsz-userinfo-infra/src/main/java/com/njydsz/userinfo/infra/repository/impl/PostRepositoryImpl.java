package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.repository.PostRepository;
import com.njydsz.userinfo.infra.entity.PostDO;
import com.njydsz.userinfo.infra.mapper.PostMapper;

/**
 * 岗位 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link PostMapper} 实现岗位的数据访问。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

  private final PostMapper postMapper;

  @Override
  public PostDO findById(String id) {
    return postMapper.selectById(id);
  }

  @Override
  public PostDO findByPostCode(String postCode) {
    LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(PostDO::getPostCode, postCode);
    return postMapper.selectOne(wrapper);
  }

  @Override
  public List<PostDO> list(LambdaQueryWrapper<PostDO> wrapper) {
    return postMapper.selectList(wrapper);
  }

  @Override
  public List<PostDO> listByIds(Collection<String> ids) {
    return postMapper.selectBatchIds(ids);
  }

  @Override
  public int insert(PostDO entity) {
    return postMapper.insert(entity);
  }

  @Override
  public int updateById(PostDO entity) {
    return postMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return postMapper.deleteById(id);
  }

  @Override
  public long count(LambdaQueryWrapper<PostDO> wrapper) {
    return postMapper.selectCount(wrapper);
  }
}
