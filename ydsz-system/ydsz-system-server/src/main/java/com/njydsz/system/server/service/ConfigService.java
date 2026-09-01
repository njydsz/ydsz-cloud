package com.njydsz.system.server.service;
import java.io.InputStream;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.common.json.YdszJson;
import com.njydsz.system.domain.dto.ConfigDTO;
import com.njydsz.system.domain.query.ConfigPageQuery;
import com.njydsz.system.domain.vo.ConfigVO;
import com.njydsz.system.domain.vo.ImportResultVO;



/**
 * 系统配置 Service 接口
 *
 * <p>提供系统配置（{@code ydsz_sys_config}）的 CRUD、按 key / group 查询、公开配置查询、导入导出等能力。 集成 Redis 缓存、Micrometer
 * 指标、缓存穿透防护和配置变更事件。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #page} / {@link #getById} / {@link #save} / {@link #updateById} / {@link
 *       #removeById}
 *   <li><b>缓存读</b>：{@link #getConfigValue} / {@link #getConfigsByGroup} — 走 Redis 二级缓存
 *   <li><b>公开配置</b>：{@link #listPublicConfigs} — 前端「公开配置」接口数据源
 *   <li><b>变更广播</b>：通过 {@code ApplicationEventPublisher} 发布 {@code ConfigChangeEvent}， 订阅者可监听
 *       {@code ydsz.workflow.sla-default-hours} 等关键配置变更
 *   <li><b>导入导出</b>：{@link #exportConfigs} / {@link #importConfigs} — 环境迁移能力
 *   <li><b>游标分页</b>：{@link #pageByCursor} — 大数据量连续翻页场景
 * </ul>
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>Redis 二级缓存（{@code ydsz:config:{group}:{key}}），TTL 30min
 *   <li>本地 Caffeine 一级缓存（{@code configListByGroup}），TTL 5min
 *   <li><b>缓存穿透防护</b>：DB 不存在的 key 缓存「null」哨兵值 1min，避免恶意刷不存在 key
 *   <li>写操作通过 {@code @CacheEvict} 主动失效
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离，租户过滤由 MyBatis 拦截器注入。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigDTO 系统配置 DTO
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
  PageResponse<List<ConfigVO>> page(ConfigPageQuery query);

  /**
   * 游标分页查询系统配置
   *
   * <p>基于 ID 的 seek method，避免深度分页 offset 扫描。游标值为上一页最后一条记录的 ID。
   *
   * @param configGroup 配置分组（可选）
   * @param configKey 配置键模糊匹配（可选）
   * @param pageSize 每页条数（最大 500）
   * @param cursor 游标（上一页最后一条记录 ID，首次查询传 null）
   * @return 游标分页响应
   */
  PageResponse<List<ConfigVO>> pageByCursor(String configGroup, String configKey, int pageSize, String cursor);

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
   * <p>写入前校验 {@code (tenantId, configGroup, configKey)} 唯一性； 自动校验 {@code valueType}（{@link
   * com.njydsz.system.domain.enums.ConfigValueType}）。
   *
   * @param dto 配置 DTO（命令入参）
   * @return 新建配置主键 ID
   */
  String save(ConfigDTO dto);

  /**
   * 更新配置
   *
   * <p>更新后失效 Redis 缓存并发布 {@code ConfigChangeEvent}。
   *
   * @param dto 配置 DTO（命令入参，{@code id} 必填）
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
   * <p>典型用法：{@code configService.getConfigsByGroup("ydsz.workflow")} 返回工作流相关所有配置。 走本地 Caffeine
   * 一级缓存（5min TTL）。
   *
   * @param configGroup 配置分组
   * @return 配置列表（按 {@code sortOrder} 升序）
   */
  List<ConfigVO> getConfigsByGroup(String configGroup);

  /**
   * 查询所有公开配置（{@code isPublic=1}）
   *
   * <p>供前端「公开配置」接口（{@code /api/v1/system/config/public}）使用， 包含 feature flag、限流阈值、UI 文案等前端可见配置。
   *
   * @return 公开配置列表
   */
  List<ConfigVO> listPublicConfigs();

  /**
   * 回滚配置到指定版本
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在
   *   <li>查询当前配置项作为回滚前快照（用于审计）
   *   <li>从目标快照更新配置项
   *   <li>创建新版本记录（标记回滚来源）
   *   <li>失效缓存并发布变更事件
   * </ol>
   *
   * @param resourceKey 配置键
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID
   * @return 新创建的回滚版本 ID
   */
  String rollbackTo(String resourceKey, String targetVersion, String operatorId);

  /**
   * 导出配置为 Excel 字节数组
   *
   * <p>按配置分组导出，使用 ydsz-common-excel 实现。
   *
   * @param configGroup 配置分组（为 null 时导出全部配置）
   * @return Excel 文件字节数组
   */
  byte[] exportConfigs(String configGroup);

  /**
   * 从 Excel 导入配置
   *
   * <p>使用 ydsz-common-excel 读取 Excel 文件，逐条校验后批量插入。 导入前校验 (configGroup, configKey) 唯一性，重复时跳过。
   *
   * @param inputStream Excel 文件输入流
   * @return 导入结果（成功数、失败数、跳过数）
   */
  ImportResultVO importConfigs(InputStream inputStream);

  // ============================== 强类型配置读取 ==============================

  /**
   * 获取字符串配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在时返回默认值
   */
  default String getString(String configKey, String defaultValue) {
    String value = getConfigValue(configKey);
    return value != null ? value : defaultValue;
  }

  /**
   * 获取整数配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  default Integer getInt(String configKey, Integer defaultValue) {
    String value = getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Integer.parseInt(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * 获取长整数配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  default Long getLong(String configKey, Long defaultValue) {
    String value = getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return Long.parseLong(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * 获取布尔配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  default Boolean getBoolean(String configKey, Boolean defaultValue) {
    String value = getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    String trimmed = value.trim().toLowerCase();
    if ("true".equals(trimmed) || "1".equals(trimmed) || "yes".equals(trimmed)) {
      return Boolean.TRUE;
    }
    if ("false".equals(trimmed) || "0".equals(trimmed) || "no".equals(trimmed)) {
      return Boolean.FALSE;
    }
    return defaultValue;
  }

  /**
   * 获取数值配置值
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return 配置值，不存在或解析失败时返回默认值
   */
  default BigDecimal getDecimal(String configKey, BigDecimal defaultValue) {
    String value = getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return new BigDecimal(value.trim());
    } catch (NumberFormatException e) {
      return defaultValue;
    }
  }

  /**
   * 获取 JSON 配置值并反序列化为指定类型
   *
   * @param configKey 配置键
   * @param clazz 目标类型
   * @param defaultValue 默认值
   * @param <T> 目标类型泛型
   * @return 配置值，不存在或解析失败时返回默认值
   */
  default <T> T getJson(String configKey, Class<T> clazz, T defaultValue) {
    String value = getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return YdszJson.fromJson(value, clazz);
    } catch (Exception e) {
      return defaultValue;
    }
  }

  /**
   * 获取 JSON 配置值并转换为 Map
   *
   * @param configKey 配置键
   * @param defaultValue 默认值
   * @return Map 类型配置值，不存在或解析失败时返回默认值
   */
  default Map<String, Object> getJsonAsMap(String configKey, Map<String, Object> defaultValue) {
    String value = getConfigValue(configKey);
    if (value == null || value.isBlank()) {
      return defaultValue;
    }
    try {
      return YdszJson.parseMap(value);
    } catch (Exception e) {
      return defaultValue;
    }
  }
}
