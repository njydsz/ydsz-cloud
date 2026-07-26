package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.PostSaveDTO;
import com.njydsz.userinfo.domain.entity.PostDO;
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

    private final PostMapper mapper;

    @Override
    public PostVO getById(String id) {
        PostDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public List<PostVO> list() {
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostDO::getDeleted, 0);
        wrapper.orderByDesc(PostDO::getSortOrder);
        return mapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(PostSaveDTO dto) {
        // 编码唯一性校验
        LambdaQueryWrapper<PostDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(PostDO::getPostCode, dto.getPostCode());
        wrapper.eq(PostDO::getDeleted, 0);
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.POST_CODE_DUPLICATE);
        }

        PostDO entity = new PostDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        mapper.insert(entity);
        log.info("Post created: code={}, id={}", entity.getPostCode(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(PostSaveDTO dto) {
        PostDO entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        PostDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.POST_NOT_FOUND);
        }
        return mapper.deleteById(id) > 0;
    }

    private PostVO toVO(PostDO entity) {
        PostVO vo = new PostVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
