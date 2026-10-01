package com.njydsz.userinfo.domain.dto;

import java.io.Serial;
import java.io.Serializable;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import com.njydsz.common.safe.annotation.Xss;

/**
 * 岗位请求 DTO。
 *
 * <p>同时用于创建和更新场景：创建时 {@code id} 可不传，更新时 {@code id} 必填。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Data
public class PostDTO implements Serializable {

  @Serial private static final long serialVersionUID = 1L;

  /** 岗位 ID（更新时必填） */
  @Xss(message = "{userinfo.param.xss}")
  private String id;

  /** 岗位名称（前端展示，如「项目经理」「后端开发工程师」） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String postName;

  /** 岗位编码（全局唯一，建议使用英文枚举值如 {@code PM} / {@code DEV}） */
  @NotBlank(message = "{userinfo.param.required}")
  @Size(max = 64, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String postCode;

  /** 岗位描述 */
  @Size(max = 500, message = "{userinfo.error.max_length}")
  @Xss(message = "{userinfo.param.xss}")
  private String description;

  /** 同级排序序号（升序） */
  private Integer sort;

  /** 启用状态（{@code "ENABLED"} / {@code "DISABLED"}） */
  @Xss(message = "{userinfo.param.xss}")
  private String status;
}
