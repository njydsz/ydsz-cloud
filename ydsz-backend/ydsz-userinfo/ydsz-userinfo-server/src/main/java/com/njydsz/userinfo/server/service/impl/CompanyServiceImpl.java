package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.service.AbstractMpCrudService;
import com.njydsz.userinfo.domain.dto.CompanySaveDTO;
import com.njydsz.userinfo.domain.entity.CompanyDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;
import com.njydsz.userinfo.server.service.CompanyService;

import lombok.extern.slf4j.Slf4j;

/**
 * 公司 Service 实现。
 *
 * <p>基于 {@link AbstractMpCrudService} 复用通用 CRUD 能力，
 * 通过生命周期钩子集成 companyCode 唯一性校验。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
public class CompanyServiceImpl
        extends AbstractMpCrudService<CompanyDO, CompanySaveDTO, CompanyVO, CompanyPageQuery, String>
        implements CompanyService {

    private final CompanyMapper companyMapper;

    public CompanyServiceImpl(CompanyMapper companyMapper) {
        this.companyMapper = companyMapper;
    }

    @Override
    protected BaseMapper<CompanyDO> getMapper() {
        return companyMapper;
    }

    @Override
    protected CompanyVO toVO(CompanyDO entity) {
        if (entity == null) {
            return null;
        }
        CompanyVO vo = new CompanyVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }

    @Override
    protected CompanyDO toEntity(CompanySaveDTO dto) {
        if (dto == null) {
            return null;
        }
        CompanyDO entity = new CompanyDO();
        BeanUtils.copyProperties(dto, entity);
        return entity;
    }

    @Override
    protected String getId(CompanySaveDTO dto) {
        return dto != null ? dto.getId() : null;
    }

    @Override
    protected QueryWrapper<CompanyDO> buildQueryWrapper(CompanyPageQuery query) {
        QueryWrapper<CompanyDO> wrapper = new QueryWrapper<>();
        if (query.getCompanyCode() != null && !query.getCompanyCode().isBlank()) {
            wrapper.like("company_code", query.getCompanyCode());
        }
        if (query.getCompanyName() != null && !query.getCompanyName().isBlank()) {
            wrapper.like("company_name", query.getCompanyName());
        }
        if (query.getStatus() != null && !query.getStatus().isBlank()) {
            wrapper.like("status", query.getStatus());
        }
        wrapper.orderByDesc("created_at");
        return wrapper;
    }

    @Override
    protected void doBeforeSave(CompanySaveDTO dto, CompanyDO entity) {
        // 编码唯一性校验
        LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompanyDO::getCompanyCode, entity.getCompanyCode());
        if (companyMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.COMPANY_CODE_DUPLICATE);
        }
        // 默认状态
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
    }

    @Override
    public boolean updateById(CompanySaveDTO dto) {
        CompanyDO entity = companyMapper.selectById(dto.getId());
        if (entity == null) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return companyMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        CompanyDO entity = companyMapper.selectById(id);
        if (entity == null) {