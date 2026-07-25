package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.PostDO;

/**
 * 岗位 service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface PostService {

    PostDO getById(String id);
    List<PostDO> list();
    String save(PostDO entity);
    boolean updateById(PostDO entity);
    boolean removeById(String id);
}
