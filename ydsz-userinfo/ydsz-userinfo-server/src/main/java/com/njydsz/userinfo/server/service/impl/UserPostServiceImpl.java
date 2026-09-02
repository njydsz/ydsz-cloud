package com.njydsz.userinfo.server.service.impl;

import java.util.List;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.njydsz.common.exception.custom.BusinessException;
import com.njydsz.userinfo.domain.dto.UserPostDTO;
import com.njydsz.userinfo.domain.enums.UserInfoExceptionCode;
import com.njydsz.userinfo.domain.repository.UserPostRepository;
import com.njydsz.userinfo.domain.vo.UserPostVO;
import com.njydsz.userinfo.server.service.UserPostService;

/**
 * 用户-岗位关联服务实现。
 *
 * <p>维护用户-岗位的多对多关联 ({@code ydsz_acct_user_post})：支持一人多岗（主岗/兼职），
 *
 * <p>用于工作流审批人解析（按岗位找人）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserPostServiceImpl implements UserPostService {

  private final UserPostRepository userPostRepository;

  /**
   * {@inheritDoc}
   *
   * <p>通过 {@link UserPostRepository#findById} 查询，未找到或已删除时抛出 {@link BusinessException}。
   *
   * @throws BusinessException 当用户-岗位关联不存在时抛出
   */
  @Override
  public UserPostVO getById(String id) {
    return userPostRepository.findById(id)
        .orElseThrow(() -> new BusinessException(UserInfoExceptionCode.POST_NOT_FOUND));
  }

  /**
   * {@inheritDoc}
   *
   * <p>⚠️ UserPostRepository 不支持无过滤条件的全量查询，请使用 {@link UserPostRepository#findByUserId(String)}。
   */
  @Override
  public List<UserPostVO> list() {
    throw new UnsupportedOperationException(
        "UserPostRepository 不支持无过滤条件的全量列表查询，请使用 findByUserId");
  }

  /**
   * {@inheritDoc}
   *
   * <p>将 {@link UserPost} 属性拷贝到 {@link UserPostDTO} 后调用 {@link UserPostRepository#create}。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public String save(UserPostVO vo) {
    UserPostDTO dto = new UserPostDTO();
    dto.setUserId(vo.getUserId());
    dto.setPostId(vo.getPostId());
    UserPostVO result = userPostRepository.create(dto);
    return result.getId();
  }

  /**
   * {@inheritDoc}
   *
   * <p>⚠️ UserPostRepository 不支持 update 操作，岗位关联变更请通过「先删后建」实现。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean updateById(UserPostVO vo) {
    throw new UnsupportedOperationException(
        "UserPostRepository 不支持 update 操作，请使用 delete + create 实现关联变更");
  }

  /**
   * {@inheritDoc}
   *
   * <p>直接委托 {@link UserPostRepository#deleteById}，返回其布尔结果。
   */
  @Override
  @Transactional(rollbackFor = Exception.class)
  public boolean removeById(String id) {
    return userPostRepository.deleteById(id);
  }
}
