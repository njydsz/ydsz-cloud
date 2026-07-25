package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserPostDO;

/**
 * 用户岗位 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserPostService {

    UserPostDO getById(String id);
    List<UserPostDO> list();
    String save(UserPostDO entity);
    boolean updateById(UserPostDO entity);
    boolean removeById(String id);
}
