package com.njydsz.userinfo.server.service;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.entity.UserAccount;
import com.njydsz.userinfo.domain.vo.UserAccountVO;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 用户账号 Service 接口
 *
 * <p>封装用户账号的完整业务逻辑：CRUD、密码管理、角色分配、审批人展开查询、跨服务富化。
 * 是用户中心服务（ydsz-userinfo）最核心的 Service，被各业务模块通过
 * {@code UserAccountClient}（Feign）远程调用获取用户基础信息。
 *
 * <p><b>核心职责：</b>
 * <ul>
 *   <li>用户 CRUD（含密码 BCrypt 加密存储）</li>
 *   <li>密码管理（用户自助修改 / 管理员重置）</li>
 *   <li>角色分配与查询（{@code assignRoles} / {@code getUserRoleIds}）</li>
 *   <li>审批人展开查询（按角色编码 / 岗位编码 / 直属上级查询用户 ID 列表）</li>
 *   <li>跨服务名称富化（{@code batchUserNames}，供 NameAssembler 调用）</li>
 * </ul>
 *
 * <p><b>Feign 远程调用契约：</b>
 * <ul>
 *   <li>本接口中所有 {@code listXxxByXxxId} 方法可被工作流服务（ydsz-workflow）调用，
 *       实现「角色/部门/岗位/直属上级」等审批人展开语义</li>
 *   <li>{@code batchUserNames} 供 NameAssembler 富化业务 VO 的 createdBy/updatedBy/assignee 等字段</li>
 * </ul>
 *
 * <p><b>事务：</b>所有写操作（{@code create/update/removeById/changePassword/resetPassword/assignRoles}）
 * 开启 {@code @Transactional(rollbackFor = Exception.class)}，确保任一异常触发完整回滚。
 *
 * @author ydsz-team
 * @since 1.0.0
 *
 * @see UserAccount 用户实体
 * @see com.njydsz.userinfo.web.controller.UserAccountController 用户 Controller
 * @see com.njydsz.userinfo.api.UserAccountClient Feign Client 接口
 */
public interface UserAccountService {

    /**
     * 根据 ID 查询用户详情。
     *
     * @param id 用户 ID（雪花算法字符串）
     * @return 用户 VO（脱敏后的手机号/邮箱），不存在时返回 null
     */
    UserAccountVO getById(String id);

    /**
     * 分页查询用户列表（多条件过滤：用户名/手机号/邮箱/部门/状态）。
     *
     * <p>支持字段：{@code username} 模糊、{@code phone} 精确、{@code email} 精确、
     * {@code deptId} 精确、{@code status} 精确。
     *
     * @param query 查询条件
     * @return 分页结果（{@link Page} 包含 total/records）
     */
    Page<UserAccountVO> page(UserAccountPageQueryDTO query);

    /**
     * 查询全部用户列表（无分页）。
     *
     * <p><b>性能：</b>仅适合用户数 &lt; 1000 的场景，超过请改用 {@link #page}。
     *
     * @return 用户列表（按创建时间倒序）
     */
    List<UserAccountVO> list();

    /**
     * 创建用户（含密码 BCrypt 加密）。
     *
     * <p>校验：① 用户名唯一性；② 密码强度（可选）；③ 角色 ID 列表有效性。
     *
     * @param dto 创建参数
     * @return 新用户 ID（雪花算法字符串）
     * @throws com.njydsz.common.exception.BizException 用户名已存在时抛出
     */
    String create(UserAccountCreateDTO dto);

    /**
     * 更新用户信息。
     *
     * <p>不允许通过此接口修改密码（请用 {@link #changePassword} 或 {@link #resetPassword}）。
     *
     * @param dto 更新参数
     * @return true=成功，false=用户不存在
     */
    boolean update(UserAccountUpdateDTO dto);

    /**
     * 删除用户（逻辑删除）。
     *
     * <p>业务校验：① 存在待办/审批任务时禁止删除（业务层拦截）；② 内置用户不可删除。
     *
     * @param id 用户 ID
     * @return true=成功，false=用户不存在
     */
    boolean removeById(String id);

    /**
     * 用户自助修改密码。
     *
     * <p>校验：① 旧密码匹配（BCrypt 校验）；② 新密码强度（可选）。
     *
     * @param dto 修改密码参数（oldPassword / newPassword / userId）
     * @return true=成功
     * @throws com.njydsz.common.exception.BizException 旧密码错误时抛出
     */
    boolean changePassword(ChangePasswordDTO dto);

    /**
     * 管理员重置用户密码。
     *
     * <p>生成随机密码并通过短信/邮件通知（由 {@code NotificationClient} 异步触发）。
     *
     * @param dto 重置密码参数（userId / notifyChannel）
     * @return true=成功
     */
    boolean resetPassword(ResetPasswordDTO dto);

    /**
     * 分配用户角色（覆盖式）。
     *
     * <p>事务内：① 清空旧用户-角色关联；② 批量插入新关联。任一失败回滚。
     *
     * @param userId 用户 ID
     * @param roleIds 角色 ID 列表（可空/空列表表示清空所有角色）
     * @return true=成功
     */
    boolean assignRoles(String userId, List<String> roleIds);

    /**
     * 查询用户角色 ID 列表。
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表（无角色时返回空列表）
     */
    List<String> getUserRoleIds(String userId);

    /**
     * 按角色编码查询用户 ID 列表（供 Feign 跨服务调用，支持工作流 role:xxx 审批人展开）。
     *
     * @param roleCode 角色编码（如 {@code ROLE_ADMIN}）
     * @return 用户 ID 列表（String 形式）
     */
    List<String> listUserIdsByRoleCode(String roleCode);

    /**
     * 查询用户拥有的角色编码列表（供 Feign 跨服务调用，工作流待办反查）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表（如 {@code ["ROLE_ADMIN", "ROLE_USER"]}）
     */
    List<String> listRoleCodesByUserId(String userId);

    /**
     * 查询用户所属部门 ID 列表（供 Feign 跨服务调用，工作流待办反查）。
     *
     * @param userId 用户 ID
     * @return 部门 ID 列表（String 形式）
     */
    List<String> listDeptIdsByUserId(String userId);

    /**
     * 查询用户的直属上级 ID（供 Feign 跨服务调用，支持工作流 leader:xxx 审批人展开）。
     *
     * @param userId 用户 ID
     * @return 直属上级用户 ID，未设置时返回 null
     */
    String getLeaderByUserId(String userId);

    /**
     * 按岗位编码查询用户 ID 列表（供 Feign 跨服务调用，支持工作流 position:xxx 审批人展开）。
     *
     * @param positionCode 岗位编码（如 {@code PM} / {@code DEV}）
     * @return 用户 ID 列表
     */
    List<String> listUserIdsByPositionCode(String positionCode);

    /**
     * 批量查询用户 ID → 用户真实姓名映射（供 NameAssembler 跨服务富化 userName / createdByName 等字段）。
     *
     * <p>实现：单条 SQL {@code SELECT id, real_name FROM ydsz_user_account WHERE id IN (...)}，
     * 一次往返拿到全部结果。已逻辑删除的用户不会出现在结果中。
     *
     * @param userIds 用户 ID 集合（允许 null / 空，返回空 Map）
     * @return userId → realName 映射；未命中的 userId 不出现在 Map 中
     */
    Map<String, String> batchUserNames(Collection<String> userIds);
}
