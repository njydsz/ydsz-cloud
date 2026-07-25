package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.entity.UserAccountDO;

/**
 * UserAccount service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserAccountService {

    UserAccountDO getById(String id);
    List<UserAccountDO> list();
    String save(UserAccountDO entity);
    boolean updateById(UserAccountDO entity);
    boolean removeById(String id);
}
