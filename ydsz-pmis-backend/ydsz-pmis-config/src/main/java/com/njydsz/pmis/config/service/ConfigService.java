package com.njydsz.pmis.config.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.config.dto.ConfigFormDTO;
import com.njydsz.pmis.config.dto.ConfigQueryDTO;
import com.njydsz.pmis.config.entity.ConfigDO;

import java.util.List;
import java.util.Map;

/**
 * 系统配置服务
 *
 * <p>提供以下能力：
 * <ul>
 *   <li>分页 CRUD</li>
 *   <li>按 group + key 查询</li>
 *   <li>公开配置（前端可见）查询</li>
 *   <li>Redis 缓存（TTL 10 分钟）</li>
 *   <li>类型安全取值（String/Number/Boolean/JSON）</li>
 *   <li>热发布：变更后通过 Redis Pub/Sub 通知所有服务节点</li>
 * </ul>
 */
public interface ConfigService {

    Page<ConfigDO> page(ConfigQueryDTO query);

    ConfigDO getById(Long id);

    ConfigDO getByKey(String group, String key);

    /**
     * 获取某组全部配置（key → value 映射）
     */
    Map<String, String> getGroupAsMap(String group);

    /**
     * 公开配置（前端可见）
     */
    List<ConfigDO> listPublic();

    Long create(ConfigFormDTO dto);

    void update(ConfigFormDTO dto);

    void delete(Long id);

    /**
     * 批量按 group 删除（清理整个分组的配置）
     */
    int deleteByGroup(String group);

    /**
     * 批量按 group 启用/停用
     */
    int updateStatusByGroup(String group, String status);

    /**
     * 刷新缓存（变更后调用）
     */
    void refreshCache();

    /**
     * 解析配置值（按 valueType 转）
     */
    <T> T parseValue(ConfigDO config, Class<T> type);
}
