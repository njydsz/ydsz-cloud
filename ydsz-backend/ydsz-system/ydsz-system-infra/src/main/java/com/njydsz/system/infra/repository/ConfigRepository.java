package com.njydsz.system.infra.repository;

import org.springframework.stereotype.Repository;

import com.njydsz.common.jdbc.repository.BaseMapperRepository;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.infra.mapper.ConfigMapper;

/**
 * 系统配置仓储实现。
 *
 * <p>基于 {@link BaseMapperRepository} 提供默认 CRUD 能力，
 * 复杂查询可继续委托给 {@link ConfigMapper} 自定义方法。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Repository
public class ConfigRepository extends BaseMapperRepository<ConfigDO, String> {

    private final ConfigMapper configMapper;

    public ConfigRepository(ConfigMapper configMapper) {
        super(configMapper);
        this.configMapper = configMapper;
    }

    /**
     * 获取原生 Mapper（用于自定义 SQL 方法）。
     *
     * @return 系统配置 Mapper
     */
    public ConfigMapper getConfigMapper() {
        return configMapper;
    }
}
