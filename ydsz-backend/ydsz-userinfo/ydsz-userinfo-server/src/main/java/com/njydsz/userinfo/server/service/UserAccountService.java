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
 * 用户账号 Service 接口。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public interface UserAccountService {

    UserAccountVO getById(String id);

    Page<UserAccountVO> page(UserAccountPageQueryDTO query);

    List<UserAccountVO> list();

    String create(UserAccountCreateDTO dto);

    boolean update(UserAccountUpdateDTO dto);

    boolean removeById(String id);

    boolean changePassword(ChangePasswordDTO dto);

    boolean resetPassword(ResetPasswordDTO dto);

    boolean assignRoles(String userId, List<String> roleIds);

    List<String> getUserRoleIds(String userId);

    /**
     * 按角色编码查询用户 ID 列表（供 Feign 跨服务调用，支持工作流 role:xxx 审批人展开）。
     *
     * @param roleCode 角色编码
     * @return 用户 ID 列表（String 形式）
     */
    List<String> listUserIdsByRoleCode(String roleCode);

    /**
     * 查询用户拥有的角色编码列表（供 Feign 跨服务调用，工作流待办反查）。
     *
     * @param userId 用户 ID
     * @return 角色编码列表
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
     * @param positionCode 岗位编码
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
