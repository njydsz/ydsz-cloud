package com.njydsz.userinfo.server.service;

import java.util.List;

import com.njydsz.userinfo.domain.dto.ChangePasswordDTO;
import com.njydsz.userinfo.domain.dto.ResetPasswordDTO;
import com.njydsz.userinfo.domain.dto.UserAccountCreateDTO;
import com.njydsz.userinfo.domain.dto.UserAccountPageQueryDTO;
import com.njydsz.userinfo.domain.dto.UserAccountUpdateDTO;
import com.njydsz.userinfo.domain.entity.UserAccountDO;
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
}
