package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.PostSaveDTO;
import com.njydsz.userinfo.domain.entity.Post;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.PostVO;
import com.njydsz.userinfo.infra.mapper.PostMapper;
import com.njydsz.userinfo.server.service.PostService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 岗位 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PostServiceImpl implements PostService {

    /** 岗位 Mapper */
    private final PostMapper mapper;

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当岗位不存在或已删除时抛出
     */
    @Override
    public PostVO getById(String id) {
        Post entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        return toVO(entity);
    }

    /**
     * {@inheritDoc}
     *
     * @return 全部未删除岗位列表（按 sortOrder 降序）
     */
    @Override
    public List<PostVO> list() {
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getDeleted, 0);
        wrapper.orderByDesc(Post::getSortOrder);
        return mapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    /**
     * {@inheritDoc}
     * <p>执行 postCode 唯一性校验后插入，status 默认 ENABLED。
     *
     * @throws BusinessException 当 postCode 已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(PostSaveDTO dto) {
        // 编码唯一性校验
        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Post::getPostCode, dto.getPostCode());
        wrapper.eq(Post::getDeleted, 0);
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.POST_CODE_DUPLICATE);
        }

        Post entity = new Post();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        mapper.insert(entity);
        log.info("Post created: code={}, id={}", entity.getPostCode(), entity.getId());
        return entity.getId();
    }

    /**
     * {@inheritDoc}
     * <p>使用 BeanUtils.copyProperties 更新字段，排除 id。
     *
     * @throws BusinessException 当岗位不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PostSaveDTO dto) {
        Post entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    /**
     * {@inheritDoc}
     *
     * @throws BusinessException 当岗位不存在或已删除时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Post entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        return mapper.deleteById(id) > 0;
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

        LambdaQueryWrapper<Post> wrapper = new LambdaQueryWrapper<>();
        wrapper.in(Post::getId, postIds);
        wrapper.eq(Post::getDeleted, 0);
        wrapper.select(Post::getId, Post::getPostName);

        return mapper.selectList(wrapper).stream()
                .collect(Collectors.toMap(
                        Post::getId,
                        Post::getPostName,
                        (v1, v2) -> v1,
                        LinkedHashMap::new
                ));
    }

    /**
     * 将 DO 转换为 VO，使用 BeanUtils.copyProperties 进行属性拷贝。
     *
     * @param entity 数据库实体
     * @return 视图对象
     */
    private PostVO toVO(Post entity) {
        PostVO vo = new PostVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
