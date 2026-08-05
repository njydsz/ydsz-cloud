package com.remisoft.common.core.code;

/**
 * 异常结果码契约接口。
 *
 * <p>定义异常与 {@link ResultCode} 之间的标准桥接契约。业务异常类实现此接口后，
 * 可被 {@link com.remisoft.common.core.response.BaseResponse} 在 O(1) 时间内识别并提取错误码，
 * 消除反射探测的性能开销与跨层耦合。</p>
 *
 * <p><b>设计动机：</b></p>
 * <ul>
 *   <li>避免 core 层通过反射探测 exception 模块的 {@code resultCode} 字段（隐式耦合 + 性能损耗）</li>
 *   <li>为第三方异常适配提供统一扩展点</li>
 *   <li>Spring Cloud Sleuth 的 {@code BaggageField}、
 *       Spring Boot 的 {@link org.springframework.boot.context.properties.bind.Bindable}
 *       也采用了相似的"接口桥接替代反射"模式</li>
 * </ul>
 *
 * <p><b>典型实现：</b></p>
 * <pre>{@code
 * public abstract class AbstractYdszException extends RuntimeException implements IExceptionResultCode {
 *     private final ResultCode resultCode;
 *
 *     {@literal @}Override
 *     public ResultCode resultCode() {
 *         return resultCode;
 *     }
 * }
 * }</pre>
 *
 * @author remi-team
 * @since 1.7.0
 * @see ResultCode
 * @see com.remisoft.common.core.response.BaseResponse#error(Throwable, java.net.URI)
 */
public interface IExceptionResultCode {

    /**
     * 获取异常绑定的结果码。
     *
     * <p>返回值不允许为 {@code null}；若异常构造时未绑定结果码，
     * 应返回 {@link BaseResultCode#UNKNOWN} 兜底，而非返回 {@code null}。</p>
     *
     * @return 异常对应的结果码（永不为 null）
     */
    ResultCode resultCode();
}
