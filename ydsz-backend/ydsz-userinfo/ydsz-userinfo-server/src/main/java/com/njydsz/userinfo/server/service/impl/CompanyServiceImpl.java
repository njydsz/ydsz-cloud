package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;

import com.njydsz.userinfo.domain.entity.CompanyDO;
import com.njydsz.userinfo.domain.enums.UserInfoResultCode;
import com.njydsz.userinfo.domain.exception.BusinessException;
import com.njydsz.userinfo.infra.mapper.CompanyMapper;
import com.njydsz.userinfo.server.service.CompanyService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;

/**
 * 公司 Service 实现。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Service
@RequiredArgsConstructor
public class CompanyServiceImpl implements CompanyService {

    private final CompanyMapper mapper;

    @Override
    public CompanyDO getById(String id) {
        CompanyDO entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            throw new BusinessException(UserInfoResultCode.COMPANY_NOT_FOUND);
        }
        return entity;
    }

    @Override
    public List<CompanyDO> list() {
        LambdaQueryWrapper<CompanyDO> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(CompanyDO::getDeleted, 0);
        wrapper.orderByDesc(CompanyDO::getCreatedAt);
        return mapper.selectList(wrapper);
    }

    @Override
    public String save(CompanyDO entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    public boolean updateById(CompanyDO entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
