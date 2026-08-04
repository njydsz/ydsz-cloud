package com.remisoft.system.server.service;

import java.util.List;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.remisoft.system.domain.dto.AppInfoDTO;
import com.remisoft.system.domain.vo.AppInfoVO;

/**
 * 应用注册 Service 接口
 *
 * <p>提供应用（{@code remi_app_info}）的 CRUD、密钥校验、分页查询等能力。
 * 集成 BCrypt 密钥加密和 Micrometer 指标。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li><b>CRUD</b>：{@link #getById} / {@link #save} / {@link #updateById} / {@link #removeById}</li>
 *   <li><b>密钥校验</b>：{@link #validateClient} — OAuth 2.0 客户端凭证模式核心入口，
 *       使用 BCrypt 匹配（不可逆）</li>
 *   <li><b>分页查询</b>：{@link #page} — 管理后台「应用管理」列表</li>
 *   <li><b>全量查询</b>：{@link #list()} — 内部使用，<b>不对前端暴露</b></li>
 * </ul>
 *
 * <p><b>安全约束（关键）：</b>
 * <ul>
 *   <li>{@code appSecret} 在 Service 层 BCrypt 加密后存储，<b>明文永远不落库</b></li>
 *   <li>VO 中<b>不暴露</b> {@code appSecret} 字段（即便 BCrypt 哈希也不应泄露）</li>
 *   <li>{@link #validateClient} 调用方需做 <b>QPS 限流</b>（{@code @RateLimit}）防爆破</li>
 *   <li>连续失败 N 次后锁定账号（{@code AppInfo.lockedUntil} 字段）</li>
 * </ul>
 *
 * <p><b>多租户：</b>所有方法自动按当前 {@code TenantContext} 隔离。
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.system.domain.entity.AppInfo 应用注册实体
 */
public interface AppInfoService {

    /**
     * 按 ID 查询应用
     *
     * <p>返回 VO 中<b>不包含</b> {@code appSecret} 字段，避免密钥哈希泄露。
     *
     * @param id 主键 ID
     * @return 应用 VO（不含 appSecret）；不存在返回 {@code null}
     */
    AppInfoVO getById(String id);

    /**
     * 校验应用密钥（BCrypt）
     *
     * <p>OAuth 2.0 客户端凭证模式核心入口；调用方需配合 <b>QPS 限流</b>（{@code @RateLimit}）和
     * <b>失败锁定</b>策略防爆破。
     *
     * @param appKey    应用 Key（{@code client_id}）
     * @param appSecret 应用密钥明文（{@code client_secret}）
     * @return 校验通过返回 {@code true}；失败返回 {@code false}（不抛异常，便于调用方统计失败次数）
     */
    boolean validateClient(String appKey, String appSecret);

    /**
     * 分页查询应用列表（支持搜索过滤）
     *
     * <p>管理后台「应用管理」列表数据源。
     *
     * @param pageNum  当前页码
     * @param pageSize 每页记录数
     * @param appName  应用名称模糊搜索（可选）
     * @param status   状态过滤（可选）
     * @return 分页结果（VO）
     */
    IPage<AppInfoVO> page(int pageNum, int pageSize, String appName, String status);

    /**
     * 查询全部应用（仅内部使用）
     *
     * <p>仅供 OAuth 2.0 token 端点内部校验使用，<b>不对前端暴露</b>。
     *
     * @return 应用列表（VO）
     */
    List<AppInfoVO> list();

    /**
     * 创建应用（密钥自动 BCrypt 加密）
     *
     * <p>写入前校验 {@code (tenantId, appCode)} 唯一性；{@code appSecret} 字段 BCrypt 加密后存储。
     *
     * @param dto 应用 DTO
     * @return 新建应用主键 ID
     */
    String save(AppInfoDTO dto);

    /**
     * 更新应用（密钥非空时 BCrypt 加密，为空时保留原密钥）
     *
     * @param dto 应用 DTO（{@code id} 必填）
     * @return 是否成功
     */
    boolean updateById(AppInfoDTO dto);

    /**
     * 删除应用（逻辑删除）
     *
     * <p>基于 MyBatis-Plus 逻辑删除（{@code @TableLogic}），不物理删除。
     * 删除后已签发的 access_token 在 Redis 黑名单 TTL 到期前仍可使用。
     *
     * @param id 主键 ID
     * @return 是否成功
     */
    boolean removeById(String id);
}
