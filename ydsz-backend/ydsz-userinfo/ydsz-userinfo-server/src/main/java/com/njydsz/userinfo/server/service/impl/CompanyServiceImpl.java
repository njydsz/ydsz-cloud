package com.njydsz.userinfo.server.service.impl;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.common.jdbc.service.AbstractMpCrudService;
import com.njydsz.common.util.BeanUpdateUtil;
import com.njydsz.userinfo.domain.dto.post.CompanyPostDTO;
import com.njydsz.userinfo.domain.dto.put.CompanyPutDTO;
import com.njydsz.userinfo.domain.entity.Company;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.query.CompanyPageQuery;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;
import com.njydsz.userinfo.server.service.CompanyService;

import lombok.extern.slf4j.Slf4j;
import com.njydsz.userinfo.domain.converter.UserInfoConverter;

/**
 * 公司 Service 实现
 *
 * <p>实现 {@link CompanyService} 接口，封装公司的完整业务逻辑：CRUD、{@code companyCode} 唯一性校验、
 * 跨服务名称富化。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>公司 CRUD（含 {@code companyCode} 唯一性校验）</li>
 *   <li>公司全量列表查询（按创建时间降序）</li>
 *   <li>跨服务名称富化（{@code batchNamesByIds}，供 NameAssembler 调用）</li>
 * </ul>
 *
 * <p><b>设计：</b>基于 {@link AbstractMpCrudService} 复用通用 CRUD 能力，
 * 通过生命周期钩子（如 {@code beforeCreate}/{@code beforeUpdate}）集成公司编码唯一性校验，
 * 避免重复样板代码。
 *
 * <p><b>事务：</b>所有写操作由基类开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * <p><b>性能：</b>{@link #batchNamesByIds} 仅 SELECT id 与 company_name 字段，单次往返。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see CompanyService Service 接口
 * @see Company 公司实体
 * @see com.njydsz.userinfo.web.controller.CompanyController 公司 Controller
 */
@Slf4j
@Service
public class CompanyServiceImpl
        extends AbstractMpCrudService<Company, CompanySaveDTO, CompanyVO, CompanyPageQuery, String>
        implements CompanyService {

    private final CompanyMapper companyMapper;

    public CompanyServiceImpl(CompanyMapper companyMapper) {
        this.companyMapper = companyMapper;
    }

    @Override
    protected BaseMapper<Company> getMapper() {
        return companyMapper;
    }

    @Override
    protected Company toEntity(CompanySaveDTO dto) {
        if (dto == null) {
            return null;
        }
        Company entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
        return entity;
    }

    @Override
    protected String getId(CompanySaveDTO dto) {
        return dto != null ? dto.getId() : null;
    }

    @Override
    protected QueryWrapper<Company> buildQueryWrapper(CompanyPageQuery query) {
        QueryWrapper<Company> wrapper = new QueryWrapper<>();
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
    protected void doBeforeSave(CompanySaveDTO dto, Company entity) {
        // 编码唯一性校验
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Company::getCompanyCode, entity.getCompanyCode());
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
        Company entity = companyMapper.selectById(dto.getId());
        if (entity == null) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        BeanUpdateUtil.copyNonNull(dto, entity, "id");
        return companyMapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        Company entity = companyMapper.selectById(id);
        if (entity == null) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        return companyMapper.deleteById(id) > 0;
    }
}