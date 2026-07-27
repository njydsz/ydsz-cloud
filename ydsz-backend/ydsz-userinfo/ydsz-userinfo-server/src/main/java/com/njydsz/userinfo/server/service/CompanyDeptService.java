package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.CompanyDept;

/**
 * 公司部门 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyDeptService {

    CompanyDept getById(String id);
    List<CompanyDept> list();
    String save(CompanyDept entity);
    boolean updateById(CompanyDept entity);
    boolean removeById(String id);
}
