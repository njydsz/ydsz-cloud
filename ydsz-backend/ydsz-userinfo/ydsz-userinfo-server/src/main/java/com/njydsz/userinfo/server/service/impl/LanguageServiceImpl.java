package com.njydsz.userinfo.server.service.impl;

import java.util.List;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.dto.LanguageSaveDTO;
import com.njydsz.userinfo.domain.entity.LanguageDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.domain.vo.LanguageVO;
import com.njydsz.userinfo.infra.mapper.LanguageMapper;
import com.njydsz.userinfo.server.service.LanguageService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 语言 Service 实现。
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
    public LanguageVO getById(String id) {
        LanguageDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.LANGUAGE_NOT_FOUND);
        }
        return toVO(entity);
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(LanguageSaveDTO dto) {
        LanguageDO entity = new LanguageDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        mapper.insert(entity);
        log.info("Language created: code={}, id={}", entity.getLanguageCode(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(LanguageSaveDTO dto) {
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

    private LanguageVO toVO(LanguageDO entity) {
        LanguageVO vo = new LanguageVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
