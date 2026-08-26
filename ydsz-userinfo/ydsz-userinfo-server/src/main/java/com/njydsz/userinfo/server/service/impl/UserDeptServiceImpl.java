package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.UserDeptDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserDeptRepository;
import com.njydsz.userinfo.domain.vo.UserDeptVO;
import com.njydsz.userinfo.server.service.UserDeptService;

/**
 * 用户-部门关联服务实现。
 *
 * <p>维护用户-部门的多对多关联 ({@code ydsz_acct_user_dept})：
 *
 * <p>支持主部门（{@code isPrimary}）标识，辅助部门（兼任）多选。
 *
 * <p>用户权限合并时按主部门优先 + 辅助部门叠加。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserDeptServiceImpl implements UserDeptService {

  private final UserDeptRepository userDeptRepository;

  /**
   * {@inheritDoc}
   *
   * @throws BusinessException 当关联不存在时抛出
   */
  @Override
  public UserDeptVO getById(String id) {
    return userDeptRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.USER_DEPT_NOT_FOUND));
  }

  /**
   * {@inheritDoc}
   *
   * <p>新 Repository API 不直接支持无参全量查询，此方法返回空列表。
   * 请使用 {@code UserDeptRepository.findByUserId(userId)} 等具体查询方法。
   */
  @Override
  public List<UserDeptVO> list() {
    return List.of();
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 {@link UserDeptRepository#create(UserDeptDTO)} 插入记录。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(UserDeptDTO dto) {
    UserDeptVO vo = userDeptRepository.create(dto);
    return vo.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 {@link UserDeptRepository#update(UserDeptDTO)} 更新记录。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(UserDeptDTO dto) {
    UserDeptVO vo = userDeptRepository.update(dto);
    return vo != null;
  }

  /**
   * {@inheritDoc}
   *
   * <p>通过 {@link UserDeptRepository#deleteById(String)} 逻辑删除记录。
   *
   * @throws BusinessException 当关联不存在或删除失败时抛出
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    if (!userDeptRepository.deleteById(id)) {
      throw new BusinessException(UserInfoExceptionCode.USER_DEPT_NOT_FOUND);
    }
    return true;
  }
}
