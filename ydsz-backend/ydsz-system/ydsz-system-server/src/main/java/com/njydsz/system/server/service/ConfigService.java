package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.domain.query.PageResult;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;

/**
 * 系统配置 Service。
 *
 * <p>提供配置 CRUD、按 key 查询、按 group 批量查询、公开配置查询等能力，
 * 集成 Redis 缓存、Micrometer 指标、缓存穿透防护和配置变更事件。
 *
 * @author ydsz-team
 */
public interface ConfigService {

    /**
     * 分页查询系统配置。
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResult<ConfigVO> page(ConfigPageQuery query);

    /**
     * 按 ID 查询配置。
     *
     * @param id 主键 ID
     * @return 配置 VO
     */
    ConfigVO getById(String id);

    /**
     * 创建配置。
     *
     * @param dto 配置 DTO
     * @return 主键 ID
     */
    String save(ConfigDTO dto);

    /**
     * 更新配置。
     *
     * @param dto 配置 DTO
     * @return 是否成功
     */
    boolean updateById(ConfigDTO dto);

    /**
     * 删除配置。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);

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
