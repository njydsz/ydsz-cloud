package com.njydsz.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.entity.ConfigDO;
import com.njydsz.system.domain.vo.ConfigVO;

/**
 * 系统配置 Service。
 *
 * <p>提供配置的 CRUD、按 key 查询、分页查询等能力，集成 Redis 缓存和 Micrometer 指标。
 *
 * @author ydsz-team
 */
public interface ConfigService {

    /**
     * 按 ID 查询配置。
     *
     * @param id 主键 ID
     * @return 配置 VO
     */
    ConfigVO getById(String id);

    /**
     * 按配置键查询配置值（走缓存）。
     *
     * @param configKey 配置键
     * @return 配置值，不存在返回 null
     */
    String getConfigValue(String configKey);

    /**
     * 分页查询配置列表。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @return 分页结果
     */
    IPage<ConfigDO> page(int pageNum, int pageSize);

    /**
     * 查询全部配置（仅内部使用，慎用）。
     *
     * @return 配置列表
     */
    List<ConfigDO> list();

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
     * 删除配置（逻辑删除）。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
