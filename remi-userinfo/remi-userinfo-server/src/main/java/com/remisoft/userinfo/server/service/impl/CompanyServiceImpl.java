package com.remisoft.userinfo.server.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.remisoft.common.exception.custom.BusinessException;
import com.remisoft.common.util.bean.BeanUpdateUtil;
import com.remisoft.userinfo.domain.converter.UserInfoConverter;
import com.remisoft.userinfo.domain.dto.post.CompanyPostDTO;
import com.remisoft.userinfo.domain.dto.put.CompanyPutDTO;
import com.remisoft.userinfo.domain.entity.Company;
import com.remisoft.userinfo.domain.enums.UserInfoResultCode;
import com.remisoft.userinfo.domain.vo.CompanyVO;
import com.remisoft.userinfo.infra.mapper.CompanyMapper;
import com.remisoft.userinfo.server.service.CompanyService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

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
 * <p><b>事务：</b>所有写操作开启 {@code @Transactional(rollbackFor = Exception.class)}。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see CompanyService Service 接口
 * @see Company 公司实体
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper companyMapper;

    @Override
    public CompanyVO getById(String id) {
        Company entity = companyMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        return UserInfoConverter.INSTANT.entityToVO(entity);
    }

    @Override
    public List<CompanyVO> list() {
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(Company::getCreatedAt);
        return companyMapper.selectList(wrapper).stream()
                .map(UserInfoConverter.INSTANT::entityToVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CompanyPostDTO dto) {
        LambdaQueryWrapper<Company> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(Company::getCompanyCode, dto.getCompanyCode());
        if (companyMapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.COMPANY_CODE_DUPLICATE);
        }

        Company entity = UserInfoConverter.INSTANT.postDtoToEntity(dto);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        companyMapper.insert(entity);
        log.info("Company created: code={}, id={}", entity.getCompanyCode(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(CompanyPutDTO dto) {
        Company entity = companyMapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        BeanUpdateUtil.copyNonNull(dto, entity, "id");
        return companyMapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        Company entity = companyMapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        return companyMapper.deleteById(id) > 0;
    }

    @Override
    public Map<String, String> batchNamesByIds(Collection<String> companyIds) {
        if (companyIds == null || companyIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<String> distinctIds = companyIds.stream()
                .filter(id -> id != null && !id.isBlank())
                .distinct()
                .collect(Collectors.toList());
        if (distinctIds.isEmpty()) {
            return Collections.emptyMap();
        }
        List<Company> companies = companyMapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(companies.size());
        for (Company company : companies) {
            if (company.getCompanyName() != null && !company.getCompanyName().isBlank()) {
                result.put(company.getId(), company.getCompanyName());
            }
        }
        return result;
    }
}
