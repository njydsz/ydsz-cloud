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
}
