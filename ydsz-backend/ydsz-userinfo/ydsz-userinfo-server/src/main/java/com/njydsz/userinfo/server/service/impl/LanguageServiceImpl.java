package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.service.AbstractMpCrudService;
import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.entity.LanguageDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.query.LanguagePageQuery;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;
import com.njydsz.userinfo.server.service.LanguageService;

import lombok.extern.slf4j.Slf4j;

/**
 * 语言 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class LanguageServiceImpl extends AbstractMpCrudService<LanguageDO, LanguageSaveDTO, LanguageVO, LanguagePageQuery, String>
        implements LanguageService {

    private final LanguageMapper mapper;

    public LanguageServiceImpl(LanguageMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    protected BaseMapper<LanguageDO> getMapper() {
        return mapper;
    }

    @Override
    protected LanguageVO toVO(LanguageDO entity) {
        if (entity == null) {
            return null;
        }
        LanguageVO vo = new LanguageVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    @Override
    protected LanguageDO toEntity(LanguageSaveDTO dto) {
        LanguageDO entity = new LanguageDO();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    @Override
    protected String getId(LanguageSaveDTO dto) {
        return dto.getId();
    }

    @Override
    protected QueryWrapper<LanguageDO> buildQueryWrapper(LanguagePageQuery query) {
        QueryWrapper<LanguageDO> wrapper = new QueryWrapper<>();
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

    @Override
    protected void doBeforeSave(LanguageSaveDTO dto, LanguageDO entity) {
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(LanguageSaveDTO dto) {
        LanguageDO entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.LANGUAGE_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        LanguageDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.LANGUAGE_NOT_FOUND);
        }
        return mapper.deleteById(id) > 0;
    }

    @Override
    public List<LanguageVO> list() {
        LambdaQueryWrapper<LanguageDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(LanguageDO::getDeleted, 0);
        wrapper.orderByDesc(LanguageDO::getSortOrder);
        return mapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }
}