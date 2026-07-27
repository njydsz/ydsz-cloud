package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserPost;

/**
 * 用户岗位 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserPostService {

    UserPost getById(String id);
    List<UserPost> list();
    String save(UserPost entity);
    boolean updateById(UserPost entity);
    boolean removeById(String id);
}
