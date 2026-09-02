package com.njydsz.system.domain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * 字典项列表查询请求 DTO。
 *
 * <p>用于 InternalApiController 内部 API 的字典项列表查询，通过 POST body 传输参数，
 * 与同模块其他请求 DTO 保持风格一致（{@link DictItemGetRequest}、{@link ConfigGetRequest}、
 * {@link AppValidateRequest}）。
 *
 * @author ydsz-team
 * @since 26.09.01
 */
@Data
@Schema(description = "字典项列表查询请求")
public class DictListRequest {

  /** 字典类型编码 */
  @NotBlank(message = "字典类型编码不能为空")
  @Schema(description = "字典类型编码", required = true)
  private String typeCode;
}
