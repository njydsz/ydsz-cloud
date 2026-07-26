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

import com.njydsz.userinfo.domain.dto.CompanySaveDTO;
import com.njydsz.userinfo.domain.entity.CompanyDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.vo.CompanyVO;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;
import com.njydsz.userinfo.server.service.CompanyService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 公司 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper mapper;

    @Override
    public CompanyVO getById(String id) {
        CompanyDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        return toVO(entity);
    }

    @Override
    public List<CompanyVO> list() {
        LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompanyDO::getDeleted, 0);
        wrapper.orderByDesc(CompanyDO::getCreatedAt);
        return mapper.selectList(wrapper).stream()
                .map(this::toVO)
                .collect(Collectors.toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String create(CompanySaveDTO dto) {
        // 编码唯一性校验
        LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompanyDO::getCompanyCode, dto.getCompanyCode());
        wrapper.eq(CompanyDO::getDeleted, 0);
        if (mapper.selectCount(wrapper) > 0) {
            throw new BusinessException(UserInfoResultCode.COMPANY_CODE_DUPLICATE);
        }

        CompanyDO entity = new CompanyDO();
        BeanUtils.copyProperties(dto, entity);
        if (entity.getStatus() == null) {
            entity.setStatus("ENABLED");
        }
        mapper.insert(entity);
        log.info("Company created: code={}, id={}", entity.getCompanyCode(), entity.getId());
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean update(CompanySaveDTO dto) {
        CompanyDO entity = mapper.selectById(dto.getId());
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        BeanUtils.copyProperties(dto, entity, "id");
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        CompanyDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        return mapper.deleteById(id) > 0;
    }

    /**
     * 批量查询公司 ID → 公司名映射。
     *
     * <p>实现：{@link com.baomidou.mybatisplus.core.mapper.BaseMapper#selectBatchIds(Collection)}
     * 单条 SQL 完成（已自动追加 {@code deleted = 0} 条件，因 {@link CompanyDO#getDeleted()} 标注了 {@link com.baomidou.mybatisplus.annotation.TableLogic}）。
     */
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
        List<CompanyDO> companies = mapper.selectBatchIds(distinctIds);
        Map<String, String> result = new LinkedHashMap<>(companies.size());
        for (CompanyDO company : companies) {
            if (company.getCompanyName() != null && !company.getCompanyName().isBlank()) {
                result.put(company.getId(), company.getCompanyName());
            }
        }
        return result;
    }

    private CompanyVO toVO(CompanyDO entity) {
        CompanyVO vo = new CompanyVO();
        BeanUtils.copyProperties(entity, vo);
        return vo;
    }
}
