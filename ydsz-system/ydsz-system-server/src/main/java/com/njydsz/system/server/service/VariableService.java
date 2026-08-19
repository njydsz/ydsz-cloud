package com.njydsz.system.server.service;

import java.io.InputStream;
import java.util.List;

import com.njydsz.common.core.response.PageResponse;
import com.njydsz.system.domain.query.VariablePageQuery;
import com.njydsz.system.domain.vo.ImportResult;
import com.njydsz.system.domain.vo.VariableVO;

/**
 * 系统变量 Service 接口
 *
 * <p>提供系统变量（{@code ydsz_variable}）的 CRUD、按 key 查询值、分页查询等能力。 集成 Redis 缓存和缓存穿透防护。
 *
 * <p><b>核心职责：</b>
 *
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}
 *   <li><b>按 key 查询</b>：{@link #getVariableValue} — 走 Redis 缓存，典型用法：
 *       {@code @Value("${ydsz.variable.xxx}")} 占位符解析
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「系统变量管理」列表
 *   <li><b>全量查询</b>：{@link #list()} — 内部使用，<b>不对前端暴露</b>
 * </ul>
 *
 * <p><b>与 ConfigService 的区别：</b>
 *
 * <ul>
 *   <li>{@code Variable} 是<b>全局无分组</b>扁平结构，适合「跨模块共享的环境变量」
 *   <li>{@code Config} 是<b>按分组</b>管理，适合「按业务域配置」
 * </ul>
 *
 * <p><b>缓存策略：</b>
 *
 * <ul>
 *   <li>Redis 缓存（{@code ydsz:variable:{key}}），TTL 30min
 *   <li><b>缓存穿透防护</b>：DB 不存在的 key 缓存「null 哨兵」1min
 *   <li>写操作通过 {@code @CacheEvict} 主动失效
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author ydsz-team
 * @since 1.0.0
 * @see ConfigService 系统配置 Service（按分组的同类结构）
 * @see com.njydsz.system.infra.entity.Variable 系统变量实体
 */
public interface VariableService {

  /**
   * 按 ID 查询系统变量
   *
   * @param id 主键 ID
   * @return 变量 VO；不存在返回 {@code null}
   */
  VariableVO getById(String id);

  /**
   * 按变量键查询变量值（走缓存）
   *
   * <p>典型用法：业务代码中通过 {@code variableService.getVariableValue("ydsz.feature.xxx")}
   * 读取运行时配置。注意：当前仅支持<b>租户内</b>唯一 key。
   *
   * @param variableKey 变量键
   * @return 变量值；不存在返回 {@code null}（已用「null 哨兵」防穿透）
   */
  String getVariableValue(String variableKey);

  /**
   * 分页查询系统变量（支持搜索过滤）
   *
   * @param query 分页查询条件（pageNum / pageSize / variableKey / status）
   * @return 分页结果（VO），统一使用 {@link PageResponse}
   */
  PageResponse<List<VariableVO>> page(VariablePageQuery query);

  /**
   * 查询全部系统变量（仅内部使用）
   *
   * <p>仅供内部同步、缓存预热等场景使用，<b>不对前端暴露</b>。
   *
   * @return 变量列表（VO）
   */
  List<VariableVO> list();

  /**
   * 创建系统变量
   *
   * <p>写入前校验 {@code (tenantId, variableKey)} 唯一性； 自动校验 {@code valueType}（{@link
   * com.njydsz.system.domain.enums.ConfigValueType}）。
   *
   * @param vo 变量 DTO
   * @return 新建变量主键 ID
   */
  String save(VariableVO vo);

  /**
   * 更新系统变量
   *
   * <p>更新后失效 Redis 缓存。
   *
   * @param vo 变量 DTO（{@code id} 必填）
   * @return 是否成功
   */
  boolean updateById(VariableVO vo);

  /**
   * 删除系统变量（逻辑删除）
   *
   * <p>基于 MyBatis-Plus 逻辑删除（{@code @TableLogic}），不物理删除。
   *
   * @param id 主键 ID
   * @return 是否成功
   */
  boolean removeById(String id);

  /**
   * 回滚变量到指定版本
   *
   * <p>执行链路：
   *
   * <ol>
   *   <li>校验目标版本是否存在
   *   <li>查询当前变量作为回滚前快照（用于审计）
   *   <li>从目标快照更新变量
   *   <li>创建新版本记录（标记回滚来源）
   *   <li>失效缓存
   * </ol>
   *
   * @param resourceKey 变量键
   * @param targetVersion 目标版本号
   * @param operatorId 操作人 ID
   * @return 新创建的回滚版本 ID
   */
  String rollbackTo(String resourceKey, String targetVersion, String operatorId);

  /**
   * 导出变量为 Excel 字节数组
   *
   * <p>使用 ydsz-common-excel 实现。
   *
   * @return Excel 文件字节数组
   */
  byte[] exportVariables();

  /**
   * 从 Excel 导入变量
   *
   * <p>使用 ydsz-common-excel 读取 Excel 文件，逐条校验后批量插入。 导入前校验 variableKey 唯一性，重复时跳过。
   *
   * @param inputStream Excel 文件输入流
   * @return 导入结果（成功数、失败数、跳过数）
   */
  ImportResult importVariables(InputStream inputStream);
}
