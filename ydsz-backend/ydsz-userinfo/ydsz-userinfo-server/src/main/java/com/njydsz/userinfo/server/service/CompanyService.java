package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.CompanyDO;

/**
 * 公司 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface CompanyService {
    CompanyDO getById(String id);
    List<CompanyDO> list();
    String save(CompanyDO entity);
    boolean updateById(CompanyDO entity);
    boolean removeById(String id);
}
