package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.domain.service.BaseCrudService;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;

/**
 * 系统配置 Service。
 *
 * <p>继承通用 CRUD 能力，并提供配置缓存、按 key/group 查询等扩展能力。
 *
 * @author ydsz-team
 */
public interface ConfigService extends BaseCrudService<ConfigDO, ConfigDTO, ConfigVO, ConfigPageQuery, String> {

    /**
     * 按配置键查询配置值（走缓存）。
     *
     * @param configKey 配置键
     * @return 配置值，不存在返回 null
     */
    String getConfigValue(String configKey);

    /**
     * 按配置分组批量查询启用的配置项。
     *
     * @param configGroup 配置分组
     * @return 配置列表
     */
    List<ConfigVO> getConfigsByGroup(String configGroup);

    /**
     * 查询所有公开配置（is_public=1）。
     *
     * @return 公开配置列表
     */
    List<ConfigVO> listPublicConfigs();
}
