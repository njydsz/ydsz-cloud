package com.njydsz.system.web.controller;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.njydsz.common.base.api.ApiVersion;
import com.njydsz.common.core.response.YdszResponse;
import com.njydsz.common.exception.code.ErrorCodeTable;
import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.system.domain.vo.ErrorCodeVO;

/**
 * 业务异常错误码查询 Controller。
 *
 * <p>暴露全局错误码注册表（{@link ErrorCodeTable}）中的错误码元信息，供前端/第三方系统自动生成错误码 → i18n 文案映射。
 *
 * <p><b>使用场景：</b>
 *
 * <ul>
 *   <li>前端构建时调用：自动生成 {@code errors.ts} 静态映射文件，消除手工同步成本
 *   <li>接口契约文档生成：配合 OpenAPI {@code x-error-codes} 扩展属性，补全错误码契约
 *   <li>运维排障：通过错误码反查模块归属、HTTP 状态码、i18n key
 * </ul>
 *
 * <p><b>接口路径：</b>{@code /error-codes}
 *
 * <p><b>鉴权：</b>无需鉴权（GET），错误码元信息属于平台公共数据。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@ApiVersion("26.10.01")
@Tag(name = "错误码管理", description = "业务异常错误码查询、元信息导出")
@RestController
@RequestMapping("/error-codes")
@RequiredArgsConstructor
public class ErrorCodeController {

  private final ErrorCodeTable errorCodeTable;

  /**
   * 查询全部业务异常错误码。
   *
   * <p>返回全平台所有模块的错误码列表，包含错误码、i18n 消息键、HTTP 状态码、所属模块信息。
   *
   * <p>数据来源于 {@link ErrorCodeTable}（启动期通过 {@code ExceptionCodeScanner} 扫描注册）。
   *
   * @return 错误码列表（按模块分组、按 code 排序）
   */
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "成功，返回全平台错误码列表"),
      @ApiResponse(responseCode = "500", description = "服务端内部错误")})
  @GetMapping
  @Operation(summary = "查询全部业务异常错误码")
  public YdszResponse<List<ErrorCodeVO>> listAll() {
    log.debug("查询全部业务异常错误码");
    Map<String, Map<String, ErrorCodeTable.CodeEntry>> grouped = errorCodeTable.groupByModule();
    List<ErrorCodeVO> result = new ArrayList<>(errorCodeTable.size());

    for (Map.Entry<String, Map<String, ErrorCodeTable.CodeEntry>> moduleEntry : grouped.entrySet()) {
      String moduleName = moduleEntry.getKey();
      ErrorCodeTable.ModuleEntry moduleMeta = errorCodeTable.getModules().get(moduleName);
      String moduleDesc = moduleMeta != null ? moduleMeta.description() : moduleName;

      for (Map.Entry<String, ErrorCodeTable.CodeEntry> codeEntry : moduleEntry.getValue().entrySet()) {
        ErrorCodeTable.CodeEntry ce = codeEntry.getValue();
        ExceptionCode exceptionCode = errorCodeTable.lookup(ce.code());
        int httpStatus = exceptionCode != null ? exceptionCode.getHttpStatus() : 400;

        result.add(new ErrorCodeVO(ce.code(), ce.key(), httpStatus, moduleName, moduleDesc));
      }
    }

    log.debug("查询完成，返回 {} 条错误码", result.size());
    return YdszResponse.success(result);
  }
}
