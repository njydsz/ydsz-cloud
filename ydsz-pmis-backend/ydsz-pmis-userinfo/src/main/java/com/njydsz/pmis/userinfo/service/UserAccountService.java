package com.njydsz.pmis.userinfo.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.userinfo.dto.LoginRequest;
import com.njydsz.pmis.userinfo.dto.LoginResult;
import com.njydsz.pmis.userinfo.dto.UserQueryDTO;
import com.njydsz.pmis.userinfo.entity.UserAccountDO;
import com.njydsz.pmis.userinfo.vo.UserVO;

import java.util.List;

/**
 * 用户账号服务
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public interface UserAccountService {

    /**
     * 根据 username 查询
     *
     * @param username 用户名
     * @return 用户账号实体，不存在时返回 null
     */
    UserAccountDO findByUsername(String username);

    /**
     * 根据 userId 查询
     *
     * @param userId 用户 ID
     * @return 用户账号实体，不存在时返回 null
     */
    UserAccountDO findById(Long userId);

    /**
     * 根据 userId 查询并转换为 VO（剥离 password/salt 等敏感字段）
     *
     * <p>H13.1 修复：对外接口应返回 UserVO 而非 DO。
     *
     * @param userId 用户 ID
     * @return 用户视图对象
     */
    UserVO findVoById(Long userId);

    /**
     * 分页查询
     *
     * @param query 查询条件
     * @return 分页结果（DO，内部使用）
     */
    Page<UserAccountDO> page(UserQueryDTO query);

    /**
     * 分页查询并转换为 VO
     *
     * <p>H13.1 修复：对外接口应返回 UserVO 而非 DO。
     *
     * @param query 查询条件
     * @return 分页结果（VO，已脱敏）
     */
    Page<UserVO> pageVo(UserQueryDTO query);

    /**
     * 创建用户（密码加密）
     *
     * @param user        用户信息
     * @param rawPassword 明文密码
     * @return 新建用户 ID
     */
    Long create(UserAccountDO user, String rawPassword);

    /**
     * 更新用户
     *
     * @param user 用户信息
     */
    void update(UserAccountDO user);

    /**
     * 删除用户
     *
     * @param userId 用户 ID
     */
    void delete(Long userId);

    /**
     * 重置密码
     *
     * @param userId      用户 ID
     * @param newPassword 新密码明文
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 启用/禁用
     *
     * @param userId 用户 ID
     * @param status 目标状态：ENABLED/DISABLED
     */
    void toggleStatus(Long userId, String status);

    /**
     * 为用户分配角色
     *
     * @param userId  用户 ID
     * @param roleIds 角色 ID 列表
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 查询用户角色 ID
     *
     * @param userId 用户 ID
     * @return 角色 ID 列表
     */
    List<Long> listRoleIds(Long userId);

    /**
     * 登录（带失败锁定 + 登录审计 + 2FA 校验）
     *
     * @param request 登录请求
     * @return 登录结果（含 token / 是否需要 2FA）
     */
    LoginResult login(LoginRequest request);

    /**
     * 修改自己密码（带强度校验 + 90 天强制过期）
     *
     * @param userId      用户 ID
     * @param oldPassword 旧密码明文
     * @param newPassword 新密码明文
     */
    void changePassword(Long userId, String oldPassword, String newPassword);

    /**
     * 清除登录失败计数（管理员解锁）
     *
     * @param userId 用户 ID
     */
    void clearLoginFailCount(Long userId);
}
