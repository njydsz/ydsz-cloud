package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.userinfo.domain.entity.CompanyDept;
import com.njydsz.userinfo.infra.mapper.CompanyDeptMapper;
import com.njydsz.userinfo.server.service.CompanyDeptService;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 公司-部门关联服务实现。
 *
 * <p>维护公司-部门的多对多关联 ({@code ydsz_company_dept})：一个部门可隶属多个公司，
 *
 * <p>一个公司可包含多个部门。用于跨公司组织架构展示与权限合并。
 *
 * @author ydsz-team
 * @since 1.0.0
 */

@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyDeptServiceImpl implements CompanyDeptService {

    private final CompanyDeptMapper mapper;

    @Override
    public CompanyDept getById(String id) {
        CompanyDept entity = mapper.selectById(id);
        if (entity == null || entity.getDeleted() == 1) {
            return null;
        }
        return entity;
    }

    @Override
    public List<CompanyDept> list() {
        LambdaQueryWrapper<CompanyDept> wrapper = new LambdaQueryWrapper<>();
        return mapper.selectList(wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String save(CompanyDept entity) {
        mapper.insert(entity);
        return entity.getId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateById(CompanyDept entity) {
        return mapper.updateById(entity) > 0;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean removeById(String id) {
        return mapper.deleteById(id) > 0;
    }
}
