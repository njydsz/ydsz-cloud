package com.njydsz.userinfo.infra.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.userinfo.domain.converter.UserInfoOrgConverter;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.entity.Post;
import com.njydsz.userinfo.domain.query.PostPageQuery;
import com.njydsz.userinfo.domain.repository.PostRepository;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.infra.mapper.PostMapper;

/**
 * 岗位 Repository 实现
 *
 * <p>基于 MyBatis-Plus 的 {@link PostMapper} 实现岗位的数据访问。
 * 所有返回值通过 {@link UserInfoOrgConverter} 从 DO 转换为 VO，对调用方屏蔽持久化细节。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Repository
@RequiredArgsConstructor
public class PostRepositoryImpl implements PostRepository {

  private final PostMapper postMapper;
  private final UserInfoOrgConverter converter;

  @Override
  public Optional<PostVO> findById(String id) {
    Post entity = postMapper.selectById(id);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public Optional<PostVO> findByPostCode(String postCode) {
    LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
    wrapper.eq(Post::getPostCode, postCode);
    Post entity = postMapper.selectOne(wrapper);
    return Optional.ofNullable(entity).map(converter::entityToVO);
  }

  @Override
  public PageResponse<List<PostVO>> page(PostPageQuery query) {
    Page<Post> page = new Page<>(query.getPageNum(), query.getPageSize());
    LambdaQueryWrapper<Post> wrapper = buildWrapper(query);
    Page<Post> result = postMapper.selectPage(page, wrapper);
    List<PostVO> vos = converter.postListToVO(result.getRecords());
    return PageResponse.success(
        result.getTotal(),
        (long) query.getPageNum(),
        (long) query.getPageSize(),
        vos);
  }

  @Override
  public List<PostVO> list(PostPageQuery query) {
    LambdaQueryWrapper<Post> wrapper = buildWrapper(query);
    List<Post> entities = postMapper.selectList(wrapper);
    return converter.postListToVO(entities);
  }

  @Override
  public List<PostVO> listByIds(Collection<String> ids) {
    List<Post> entities = postMapper.selectBatchIds(ids);
    return converter.postListToVO(entities);
  }

  @Override
  public PostVO save(PostDTO dto) {
    if (dto.getId() == null || dto.getId().isBlank()) {
      Post entity = converter.dtoToEntity(dto);
      postMapper.insert(entity);
      return converter.entityToVO(entity);
    } else {
      Post entity = converter.dtoToEntityWithId(dto);
      postMapper.updateById(entity);
      return converter.entityToVO(entity);
    }
  }

  @Override
  public boolean deleteById(String id) {
    return postMapper.deleteById(id) > 0;
  }

  @Override
  public long countByQuery(PostPageQuery query) {
    LambdaQueryWrapper<Post> wrapper = buildWrapper(query);
    return postMapper.selectCount(wrapper);
  }

  private LambdaQueryWrapper<Post> buildWrapper(PostPageQuery query) {
    LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
    if (query.getPostCode() != null && !query.getPostCode().isBlank()) {
      wrapper.like(Post::getPostCode, query.getPostCode());
    }
    if (query.getPostName() != null && !query.getPostName().isBlank()) {
      wrapper.like(Post::getPostName, query.getPostName());
    }
    if (query.getStatus() != null && !query.getStatus().isBlank()) {
      wrapper.eq(Post::getStatus, query.getStatus());
    }
    return wrapper;
  }
}
