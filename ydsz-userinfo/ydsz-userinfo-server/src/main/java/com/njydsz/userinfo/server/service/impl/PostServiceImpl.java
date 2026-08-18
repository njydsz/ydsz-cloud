package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.domain.dto.PostCreateDTO;
import com.njydsz.userinfo.domain.dto.PostDTO;
import com.njydsz.userinfo.domain.dto.PostUpdateDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.query.PostPageQuery;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.domain.repository.PostRepository;
import com.njydsz.userinfo.server.service.PostService;

/**
 * 岗位 Service 实现
 *
 * <p>实现 {@link PostService} 接口，封装岗位的完整业务逻辑：CRUD、{@code postCode} 唯一性校验、 跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li>岗位 CRUD（含 {@code postCode} 唯一性校验）
 *   <li>岗位全量列表查询（按 {@code sortOrder} 倒序）
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById}） 开启 {@code @Transactional(rollbackFor =
 * Exception.class)}，确保任一异常触发完整回滚。
 *
 * <p><b>性能：</b>{@link #batchNamesByIds} 仅 SELECT id 与 post_name 字段，单次往返。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see PostService Service 接口
 * @see com.njydsz.userinfo.web.controller.PostController 岗位 Controller
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

  /** 岗位 Repository */
  private final PostRepository postRepository;

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当岗位不存在或已删除时抛出
   */
  @Override
  public PostVO getById(String id) {
    return postRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.POST_NOT_FOUND));
  }

  /**
   * {@inheritDoc}
   *
   * @return 全部未删除岗位列表（按 sortOrder 降序）
   */
  @Override
  public List<PostVO> list() {
    PostPageQuery query = new PostPageQuery();
    return postRepository.list(query);
  }

  /**
   * {@inheritDoc}
   *
   * <p>执行 postCode 唯一性校验后插入，status 默认 ENABLED。
   *
   * @throws BusinessException 当 postCode 已存在时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String create(PostDTO dto) {
    // 编码唯一性校验
    PostPageQuery query = new PostPageQuery();
    query.setPostCode(dto.getPostCode());
    if (postRepository.countByQuery(query) > 0) {
      throw new BusinessException(UserInfoExceptionCode.POST_CODE_DUPLICATE);
    }

    PostCreateDTO createDTO = new PostCreateDTO();
    BeanUtils.copyProperties(dto, createDTO);
    PostVO vo = postRepository.create(createDTO);
    log.info("Post created: code={}, id={}", dto.getPostCode(), vo.getId());
    return vo.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>使用 MapStruct 转换（更新操作暂保留 BeanUtils）
   *
   * @throws BusinessException 当岗位不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean update(PostDTO dto) {
    postRepository.findById(dto.getId())
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.POST_NOT_FOUND));
    PostUpdateDTO updateDTO = new PostUpdateDTO();
    BeanUtils.copyProperties(dto, updateDTO);
    PostVO vo = postRepository.update(updateDTO);
    return vo != null;
  }

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当岗位不存在或已删除时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    postRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.POST_NOT_FOUND));
    return postRepository.deleteById(id);
  }

  /**
   * {@inheritDoc}
   *
   * @return postId → postName 映射；未命中的 postId 不出现在 Map 中
   */
  @Override
  public Map<String, String> batchNamesByIds(Collection<String> postIds) {
    if (postIds == null || postIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<String> distinctIds =
        postIds.stream()
            .filter(id -> id != null && !id.isBlank())
            .distinct()
            .collect(Collectors.toList());
    if (distinctIds.isEmpty()) {
      return Collections.emptyMap();
    }
    List<PostVO> posts = postRepository.listByIds(distinctIds);
    Map<String, String> result = new LinkedHashMap<>(posts.size());
    for (PostVO post : posts) {
      if (post.getPostName() != null && !post.getPostName().isBlank()) {
        result.put(post.getId(), post.getPostName());
      }
    }
    return result;
  }
}
