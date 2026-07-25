package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.CompanyDeptDO;

/**
 * 公司部门 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyDeptService {

    CompanyDeptDO getById(String id);
    List<CompanyDeptDO> list();
    String save(CompanyDeptDO entity);
    boolean updateById(CompanyDeptDO entity);
    boolean removeById(String id);
}
