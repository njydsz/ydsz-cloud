package com.njydsz.userinfo.infra.repository.impl;

import java.util.Collection;
import java.util.List;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.userinfo.domain.entity.Post;
import com.njydsz.userinfo.infra.mapper.PostMapper;
import com.njydsz.userinfo.infra.repository.PostRepository;

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
  public Post findById(String id) {
    return postMapper.selectById(id);
  }

  @Override
  public Post findByPostCode(String postCode) {
    LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Post::getPostCode, postCode);
    return postMapper.selectOne(wrapper);
  }

  @Override
  public List<Post> list(LambdaQueryWrapper<Post> wrapper) {
    return postMapper.selectList(wrapper);
  }

  @Override
  public List<Post> listByIds(Collection<String> ids) {
    return postMapper.selectBatchIds(ids);
  }

  @Override
  public int insert(Post entity) {
    return postMapper.insert(entity);
  }

  @Override
  public int updateById(Post entity) {
    return postMapper.updateById(entity);
  }

  @Override
  public int deleteById(String id) {
    return postMapper.deleteById(id);
  }

  @Override
  public long count(LambdaQueryWrapper<Post> wrapper) {
    return postMapper.selectCount(wrapper);
  }
}
