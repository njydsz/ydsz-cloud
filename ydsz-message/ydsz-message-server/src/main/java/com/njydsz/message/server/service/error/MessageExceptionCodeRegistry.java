package com.njydsz.message.server.service.error;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import com.njydsz.message.domain.enums.MessageExceptionCode;
import com.njydsz.message.domain.vo.ErrorCodeVO;

/**
 * 消息模块错误码注册表。
 *
 * <p>枚举 {@link MessageExceptionCode} 中的全部错误码，转换为 {@link ErrorCodeVO} 列表，供 Controller
 * 层（{@code MessageErrorCodesController}）和 OpenAPI 自定义器消费。
 *
 * <p><b>设计说明：</b>当前采用显式引用枚举类的方式，后续可通过 SPI 机制让各业务模块自动注册其 ExceptionCodeEnum，
 * 实现全项目错误码统一发现。
 *
 * @author ydsz-team
 * @since 26.10.01
 */
@Slf4j
@Component
public class MessageExceptionCodeRegistry {

  /** 缓存的错误码列表（不可变） */
  private final List<ErrorCodeVO> cachedCodes;

  /**
   * 构造注册表 — 加载 {@link MessageExceptionCode} 中的所有枚举常量。
   */
  public MessageExceptionCodeRegistry() {
    this.cachedCodes = Arrays.stream(MessageExceptionCode.values())
        .map(this::toVO)
        .sorted(Comparator.comparing(ErrorCodeVO::getCode))
        .toList();
    log.info("[MessageExceptionCodeRegistry] loaded {} message module error codes", cachedCodes.size());
  }

  /**
   * 返回全部已注册错误码（不可变列表）。
   *
   * <p>按错误码字符串升序排列，便于前端展示和二分查找。
   *
   * @return {@link ErrorCodeVO} 列表
   */
  public List<ErrorCodeVO> listAll() {
    return cachedCodes;
  }

  /**
   * 将枚举常量转换为 {@link ErrorCodeVO}。
   *
   * @param code 消息模块异常码枚举
   * @return 视图对象
   */
  private ErrorCodeVO toVO(MessageExceptionCode code) {
    return ErrorCodeVO.builder()
        .code(code.getCode())
        .messageKey(code.getKey())
        .httpStatus(code.getHttpStatus())
        .description(buildDescription(code))
        .build();
  }

  /**
   * 构造错误码英文描述（基于枚举常量名衍生）。
   *
   * <p>当前采用枚举常量名转换为可读格式，后续可引入 i18n 化的英文描述资源文件。
   *
   * @param code 异常码枚举
   * @return 英文描述文本
   */
  private String buildDescription(MessageExceptionCode code) {
    // 枚举名格式：TEMPLATE_NOT_FOUND -> "Template not found"
    String name = code.name().replace('_', ' ').toLowerCase();
    return Character.toUpperCase(name.charAt(0)) + name.substring(1);
  }
}
