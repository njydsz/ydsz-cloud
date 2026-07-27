package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserDept;

/**
 * 用户部门 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserDeptService {

    UserDept getById(String id);
    List<UserDept> list();
    String save(UserDept entity);
    boolean updateById(UserDept entity);
    boolean removeById(String id);
}
