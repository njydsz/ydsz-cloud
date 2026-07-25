package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserDeptDO;

/**
 * 用户部门 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserDeptService {

    UserDeptDO getById(String id);
    List<UserDeptDO> list();
    String save(UserDeptDO entity);
    boolean updateById(UserDeptDO entity);
    boolean removeById(String id);
}
