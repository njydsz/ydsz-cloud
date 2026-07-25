package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.RoleDO;

/**
 * Role service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface RoleService {

    RoleDO getById(String id);
    List<RoleDO> list();
    String save(RoleDO entity);
    boolean updateById(RoleDO entity);
    boolean removeById(String id);
}
