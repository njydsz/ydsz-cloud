package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.system.domain.entity.ConfigDO;

/**
 * Config service interface.
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface ConfigService {

    ConfigDO getById(String id);
    List<ConfigDO> list();
    String save(ConfigDO entity);
    boolean updateById(ConfigDO entity);
    boolean removeById(String id);
}
