package com.njydsz.pmis.common.core.response;

/**
 * 标准结果码枚举
 *
 * <p>提供系统级通用结果码，参考阿里巴巴《Java开发手册》错误码规范：
 * <ul>
 *   <li>A 类：用户端错误</li>
 *   <li>B 类：当前系统业务异常</li>
 *   <li>C 类：第三方服务异常</li>
 * </ul>
 *
 * <p>业务模块应自定义实现 {@link ResultCode} 接口的枚举，避免与此通用枚举混淆。
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see ResultCode
 */
public enum StandardResultCode implements ResultCode {

    // ==================== 成功 ====================
    SUCCESS("A00000", "操作成功"),

    // ==================== A类：用户端错误 ====================
    USER_ERROR("A01000", "用户端错误"),
    INVALID_PARAMETER("A01001", "参数校验失败"),
    UNAUTHORIZED("A01002", "未授权，请先登录"),
    FORBIDDEN("A01003", "权限不足，拒绝访问"),
    USER_NOT_FOUND("A01004", "用户不存在"),
    USER_DISABLED("A01005", "用户已被禁用"),
    TOKEN_EXPIRED("A01006", "令牌已过期"),
    TOKEN_INVALID("A01007", "令牌无效"),
    REQUEST_TOO_FREQUENT("A01008", "请求过于频繁，请稍后重试"),
    CAPTCHA_ERROR("A01009", "验证码错误或已过期"),

    // ==================== B类：系统业务异常 ====================
    SYSTEM_ERROR("B00000", "系统异常"),
    SERVICE_UNAVAILABLE("B00001", "系统繁忙，请稍后重试"),
    DATA_NOT_FOUND("B00002", "数据不存在"),
    DATA_DUPLICATE("B00003", "数据已存在"),
    DATA_EXPIRED("B00004", "数据已过期"),
    OPERATION_FAILED("B00005", "操作失败"),
    CONCURRENT_CONFLICT("B00006", "数据已被修改，请刷新后重试"),
    FILE_UPLOAD_FAILED("B00007", "文件上传失败"),
    FILE_TOO_LARGE("B00008", "文件大小超出限制"),
    FILE_TYPE_NOT_ALLOWED("B00009", "文件类型不支持"),

    // ==================== C类：第三方服务异常 ====================
    THIRD_PARTY_ERROR("C00000", "第三方服务异常"),
    REMOTE_SERVICE_UNAVAILABLE("C00001", "远程服务不可用"),
    REMOTE_SERVICE_TIMEOUT("C00002", "远程服务调用超时"),
    DATABASE_ERROR("C00003", "数据库服务异常"),
    CACHE_ERROR("C00004", "缓存服务异常"),
    MQ_ERROR("C00005", "消息队列服务异常");

    private final String code;
    private final String msg;

    StandardResultCode(String code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMsg() {
        return msg;
    }
}
