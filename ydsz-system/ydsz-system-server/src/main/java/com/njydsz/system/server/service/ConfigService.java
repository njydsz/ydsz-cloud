package com.njydsz.system.server.service;

import java.util.List;

import com.njydsz.common.domain.query.PageResponse;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;

/**
 * 系统配置 Service 接口
 *
 * <p>提供系统配置（{@code ydsz_config}）的 CRUD、按 key / group 查询、公开配置查询等能力。
 * 集成 Redis 缓存、Micrometer 指标、缓存穿透防护和配置变更事件。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>缓存读</b>：{@link #getConfigValue} / {@link #getConfigsByGroup} — 走 Redis 二级缓存</li>
 *   <li><b>公开配置</b>：{@link #listPublicConfigs} — 前端「公开配置」接口数据源</li>
 *   <li><b>变更广播</b>：通过 {@code ApplicationEventPublisher} 发布 {@code ConfigChangeEvent}，
 *       订阅者可监听 {@code ydsz.workflow.sla-default-hours} 等关键配置变更</li>
 * </ul>
 *
 * <p><b>缓存策略：</b>
 * <ul>
 *   <li>Redis 二级缓存（{@code ydsz:config:{group}:{key}}），TTL 30min</li>
 *   <li>本地 Caffeine 一级缓存（{@code configListByGroup}），TTL 5min</li>
 *   <li><b>缓存穿透防护</b>：DB 不存在的 key 缓存「null」哨兵值 1min，避免恶意刷不存在 key</li>
 *   <li>写操作通过 {@code @CacheEvict} 主动失效</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see com.njydsz.system.domain.entity.Config 系统配置实体
 * @see com.njydsz.system.domain.enums.ConfigValueType 值类型枚举
 */
public interface ConfigService {

    /**
     * 分页查询系统配置
     *
     * <p>支持按 {@code configGroup} 精确匹配 / {@code configKey} 模糊匹配 / {@code status} 过滤。
     *
     * @param query 分页查询参数
     * @return 分页结果
     */
    PageResponse<ConfigVO> page(ConfigPageQuery query);

    /**
     * 按 ID 查询配置
     *
     * @param id 主键 ID
     * @return 配置 VO；不存在返回 {@code null}
     */
    ConfigVO getById(String id);

    /**
     * 创建配置
     *
     * <p>写入前校验 {@code (tenantId, configGroup, configKey)} 唯一性；
     * 自动校验 {@code valueType}（{@link com.njydsz.system.domain.enums.ConfigValueType}）。
     *
     * @param dto 配置 DTO
     * @return 新建配置主键 ID
     */
    String save(ConfigDTO dto);

    /**
     * 更新配置
     *
     * <p>更新后失效 Redis 缓存并发布 {@code ConfigChangeEvent}。
     *
     * @param dto 配置 DTO（{@code id} 必填）
     * @return 是否成功
     */
    boolean updateById(ConfigDTO dto);

    /**
     * 删除配置
     *
     * <p>删除后失效 Redis 缓存。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);

    /**
     * 按配置键查询配置值（走缓存）
     *
     * <p>典型用法：{@code configService.getConfigValue("ydsz.workflow.sla-default-hours")}。
     * 注意：当前仅支持<b>租户内</b>唯一 key；跨租户 key 通过 {@code tenantId} 区分缓存空间。
     *
     * @param configKey 配置键
     * @return 配置值；不存在返回 {@code null}（已用「null 哨兵」防穿透）
     */
    String getConfigValue(String configKey);

    /**
     * 按配置分组批量查询启用的配置项
     *
     * <p>典型用法：{@code configService.getConfigsByGroup("ydsz.workflow")} 返回工作流相关所有配置。
     * 走本地 Caffeine 一级缓存（5min TTL）。
     *
     * @param configGroup 配置分组
     * @return 配置列表（按 {@code sortOrder} 升序）
     */
    List<ConfigVO> getConfigsByGroup(String configGroup);

    /**
     * 查询所有公开配置（{@code isPublic=1}）
     *
     * <p>供前端「公开配置」接口（{@code /api/v1/system/config/public}）使用，
     * 包含 feature flag、限流阈值、UI 文案等前端可见配置。
     *
     * @return 公开配置列表
     */
    List<ConfigVO> listPublicConfigs();
}
