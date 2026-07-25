package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.entity.AppInfoDO;

/**
 * AppInfo service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface AppInfoService {

    AppInfoDO getById(String id);
    List<AppInfoDO> list();
    String save(AppInfoDO entity);
    boolean updateById(AppInfoDO entity);
    boolean removeById(String id);
}
