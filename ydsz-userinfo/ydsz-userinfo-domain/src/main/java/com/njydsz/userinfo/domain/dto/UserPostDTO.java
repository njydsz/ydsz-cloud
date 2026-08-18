package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import lombok.Data;

/**
 * 用户-岗位关联 DTO。
 *
 * <p>用于创建用户-岗位关联关系。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class UserPostDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 用户 ID */
  private String userId;

  /** 岗位 ID */
  private String postId;
}
