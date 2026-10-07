package com.njydsz.message.web.controller;

import java.util.List;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.message.domain.vo.ErrorCodeVO;
import com.njydsz.message.server.service.error.MessageExceptionCodeRegistry;

/**
 * 错误码元信息 Controller。
 *
 * <p>暴露消息模块全部业务异常错误码（{@link ErrorCodeVO}）的注册表查询接口，
 * 供前端 TypeScript 代码生成器消费，自动建立错误码 → i18n 文案的映射关系。
 *
 * <p><b>接口路径：</b>{@code GET /message/error-codes}
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>前端构建时拉取错误码列表，生成 {@code errors.ts}
 *   <li>前端运行时懒加载错误码映射表
 *   <li>Swagger/OpenAPI 文档中展示错误码枚举信息（通过 {@code x-error-codes} 扩展字段）
 * </ul>
 *
 * <p><b>鉴权要求：</b>无。本端点配置在 {@code ydsz.auth.filter.common-ignore-url} 白名单中，
 * 不校验 Token 也不校验细粒度权限，便于 CI 流水线/前端构建期直接拉取。
 *
 * @author ydsz-team
 * @since 26.10.01
 * @see MessageExceptionCodeRegistry 错误码注册表服务
 */
@Tag(name = "错误码元信息", description = "消息模块业务异常错误码注册表查询")
@Slf4j
@ApiVersion("26.10.01")
@RestController
@RequestMapping("/message/error-codes")
@RequiredArgsConstructor
public class MessageErrorCodesController {

  /** 消息模块错误码注册表 */
  private final MessageExceptionCodeRegistry errorCodeRegistry;

  /**
   * 获取消息模块全部业务异常错误码列表（按错误码升序）。
   *
   * <p>返回的 {@link ErrorCodeVO} 列表包含每个错误码的 code / messageKey / httpStatus / description 四要素，
   * 前端代码生成器可据此自动生成 {@code errors.ts} 错误码映射文件。
   *
   * @return 统一响应结果，包含完整的消息模块错误码列表
   */
  @Operation(
      summary = "查询消息模块全部错误码",
      description = "返回消息模块注册的全部业务异常错误码列表（按错误码升序），包含 code / messageKey / httpStatus / description")
  @GetMapping
  public YdszResponse<List<ErrorCodeVO>> listAllErrorCodes() {
    log.debug("[MessageErrorCodesController] listing all error codes, total={}", errorCodeRegistry.listAll().size());
    return YdszResponse.success(errorCodeRegistry.listAll());
  }
}
