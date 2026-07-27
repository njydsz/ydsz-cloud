package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserField;

/**
 * 用户字段 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserFieldService {

    UserField getById(String id);
    List<UserField> list();
    String save(UserField entity);
    boolean updateById(UserField entity);
    boolean removeById(String id);
}
