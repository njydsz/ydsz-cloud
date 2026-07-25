package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.DepartmentDO;

/**
 * Department service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface DepartmentService {

    DepartmentDO getById(String id);
    List<DepartmentDO> list();
    String save(DepartmentDO entity);
    boolean updateById(DepartmentDO entity);
    boolean removeById(String id);
}
