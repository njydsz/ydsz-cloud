package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserFieldDO;

/**
 * 用户字段 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserFieldService {

    UserFieldDO getById(String id);
    List<UserFieldDO> list();
    String save(UserFieldDO entity);
    boolean updateById(UserFieldDO entity);
    boolean removeById(String id);
}
