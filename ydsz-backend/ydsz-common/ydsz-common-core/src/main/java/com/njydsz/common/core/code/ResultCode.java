package com.njydsz.common.core.code;

import com.njydsz.common.core.response.BaseResponse;

/**
 * 统一结果码接口
 *
 * <p>定义标准化的错误码契约，所有业务模块的错误码应实现此接口。
 * 参考阿里巴巴《Java开发手册》错误码规范设计。
 *
 * <p><b>编码规范：</b>
 * <ul>
 *   <li>A 开头：用户端错误（参数校验、权限等）</li>
 *   <li>B 开头：当前系统业务异常</li>
 *   <li>C 开头：第三方服务异常</li>
 * </ul>
 *
 * <p><b>使用示例：</b>
 * <pre>{@code
 * // 业务模块自定义错误码
 * public enum OrderResultCode implements ResultCode {
 *     ORDER_NOT_FOUND("B02001", "订单不存在"),
 *     ORDER_CANCELLED("B02002", "订单已取消");
 *
 *     private final String code;
 *     private final String msg;
 *
 *     @Override public String getCode() { return code; }
 *     @Override public String getMsg() { return msg; }
 * }
 *
 * // 在 Controller 中使用
 * return BaseResponse.error(OrderResultCode.ORDER_NOT_FOUND);
 * }</pre>
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 * @see BaseResultCode
 * @see BaseResponse#error(ResultCode)
 */
public interface ResultCode {

    /**
     * 获取结果码
     *
     * @return 结果码字符串
     */
    String getCode();

    /**
     * 获取结果消息
     *
     * @return 结果消息描述
     */
    String getMsg();

    /**
     * 获取国际化消息 key
     *
     * <p>默认实现返回 {@code "error." + 枚举名称}。
     * 实现类可覆盖此方法以自定义 key 格式。
     *
     * @return 形如 "error.BAD_REQUEST" 的国际化 key
     */
    default String getMessageKey() {
        return "error." + ((Enum<?>) this).name();
    }

    /**
     * 将结果码映射到合适的 HTTP 状态码
     *
     * <p>默认实现基于 code 前缀进行语义化推断（参照阿里《Java开发手册》段位规划）：
     * <ul>
     *   <li>{@code A2}（认证授权）→ 401</li>
     *   <li>{@code A}（用户端错误）→ 400</li>
     *   <li>{@code B}（系统业务异常）→ 500</li>
     *   <li>{@code C}（第三方服务异常）→ 500</li>
     *   <li>{@code A00000}（成功）→ 200</li>
     *   <li>其他 → 500</li>
     * </ul>
     *
     * <p>业务枚举若需更精确的映射（如 404 / 403 / 429），可覆盖此方法。</p>
     *
     * @return 对应的 HTTP 状态码
     */
    default int getHttpStatusCode() {
        String code = getCode();
        if (code == null) {
            return 500;
        }
        if (BaseResponse.SUCCESS.equals(code)) {
            return 200;
        }
        char prefix = code.charAt(0);
        return switch (prefix) {
            case 'A' -> code.length() >= 2 && code.charAt(1) == '2' ? 401 : 400;
            case 'B' -> 500;
            case 'C' -> 500;
            default -> 500;
        };
    }
}
