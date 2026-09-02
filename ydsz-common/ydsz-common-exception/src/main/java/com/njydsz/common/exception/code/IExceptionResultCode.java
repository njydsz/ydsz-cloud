package com.njydsz.common.exception.code;

import com.njydsz.common.core.code.ResultCode;

/**
 * 异常结果码桥接接口。
 *
 * <p>由异常对象实现，提供从异常实例获取 {@link ResultCode} 的桥接能力， 供响应构建器（如 {@code YdszResponse.error(Throwable)}
 * 适配链路）消费。
 *
 * <p><b>迁移说明：</b>本接口原定义于 {@code ydsz-common-core}（26.09.01 精简核心时移除）， 因属于异常处理能力，迁移至 {@code
 * ydsz-common-exception} 模块维护。
 *
 * <p><b>类型说明：</b>{@link #resultCode()} 返回值的实际类型为 {@code com.njydsz.common.core.code.ResultCode}
 * 的子类型（通常是 {@link ExceptionCode} 的枚举实现）， 调用方如需统一协议可直接使用 {@link ResultCode}（核心模块，统一接口）。
 *
 * @author ydsz-team
 * @since 26.09.01
 * @see ResultCode
 */
public interface IExceptionResultCode {

  /**
   * 获取异常关联的结果码。
   *
   * <p>返回值满足 {@link ResultCode}（核心模块，统一接口）的契约。
   *
   * @return 结果码，不可为 null
   */
  ResultCode resultCode();
}
