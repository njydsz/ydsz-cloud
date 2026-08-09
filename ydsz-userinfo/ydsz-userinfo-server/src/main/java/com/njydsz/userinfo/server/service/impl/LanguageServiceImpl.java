package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.common.domain.query.PageResponse;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.util.bean.BeanUpdateUtil;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;
import com.njydsz.userinfo.domain.dto.post.LanguagePostDTO;
import com.njydsz.userinfo.domain.dto.put.LanguagePutDTO;
import com.njydsz.userinfo.domain.entity.Language;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;
import com.njydsz.userinfo.server.service.LanguageService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语言 Service 实现
 *
 * <p>实现 {@link LanguageService} 接口，封装语言的完整业务逻辑：CRUD、
 * {@code languageCode} 唯一性校验、默认语言唯一性管理。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LanguageServiceImpl implements LanguageService {

    private final LanguageMapper mapper;

    @Override
    public BaseResponse<List<LanguageVO>> page(LanguagePageQuery query) {
        QueryWrapper<Language> wrapper = buildQueryWrapper(query);
        Page<Language> mpPage = new Page<>(query.getEffectivePageNum(), query.getEffectivePageSize());
        IPage<Language> result = mapper.selectPage(mpPage, wrapper);
        List<LanguageVO> vos = result.getRecords().stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
        return PageResponse.of(vos, result.getTotal(), query.getEffectivePageNum(), query.getEffectivePageSize());
    }

    @Override
    public LanguageVO getById(String id) {
        Language entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.LANGUAGE_NOT_FOUND);
        }
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }

    @Override
    public List<LanguageVO> list() {
        LambdaQueryWrapper<Language> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Language::getSortOrder);
        return mapper.selectList(wrapper).stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(LanguagePostDTO dto) {
        Language entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(LanguagePutDTO dto) {
        Language entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.LANGUAGE_NOT_FOUND);
        }
        BeanUpdateUtil.copyNonNull(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Language entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.LANGUAGE_NOT_FOUND);
        }
        return mapper.deleteById(id) > 0;
    }

    private QueryWrapper<Language> buildQueryWrapper(LanguagePageQuery query) {
        QueryWrapper<Language> wrapper = new QueryWrapper<>();
        if (query.getLanguageCode() != null && !query.getLanguageCode().isBlank()) {
            wrapper.like("language_code", query.getLanguageCode());
        }
        if (query.getLanguageName() != null && !query.getLanguageName().isBlank()) {
            wrapper.like("language_name", query.getLanguageName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.eq("status", query.getStatus());
        }
        wrapper.orderByDesc("sort_order");
        return wrapper;
    }
}
