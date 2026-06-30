package com.njydsz.pmis.user.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njydsz.pmis.user.dto.UserQueryDTO;
import com.njydsz.pmis.user.entity.UserAccountDO;

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
     */
    UserAccountDO findByUsername(String username);

    /**
     * 根据 userId 查询
     */
    UserAccountDO findById(Long userId);

    /**
     * 分页查询
     */
    Page<UserAccountDO> page(UserQueryDTO query);

    /**
     * 创建用户（密码加密）
     */
    Long create(UserAccountDO user, String rawPassword);

    /**
     * 更新用户
     */
    void update(UserAccountDO user);

    /**
     * 删除用户
     */
    void delete(Long userId);

    /**
     * 重置密码
     */
    void resetPassword(Long userId, String newPassword);

    /**
     * 启用/禁用
     */
    void toggleStatus(Long userId, String status);

    /**
     * 为用户分配角色
     */
    void assignRoles(Long userId, List<Long> roleIds);

    /**
     * 查询用户角色 ID
     */
    List<Long> listRoleIds(Long userId);
}
