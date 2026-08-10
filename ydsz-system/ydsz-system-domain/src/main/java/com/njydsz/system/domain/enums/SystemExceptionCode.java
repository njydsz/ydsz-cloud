package com.njydsz.system.domain.enums;

import com.njydsz.common.exception.enums.ExceptionCode;
import lombok.Getter;

import com.njydsz.common.exception.registry.YdszExceptionCode;

/**
 * 系统管理模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，自动注册到 {@link com.njydsz.common.exception.code.ErrorCodeTable}，
 * 支持 i18n 消息键、HTTP 状态码、异常分类。
 *
 * <p><b>编码区间</b>：
 * <ul>
 *   <li>B90001-B90099 系统配置</li>
 *   <li>B91001-B91099 字典类型/字典项</li>
 *   <li>B92001-B92099 系统变量</li>
 *   <li>B93001-B93099 应用信息
 * </ul>
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Getter
@YdszExceptionCode(module = "system", description = "系统管理")
public enum SystemExceptionCode implements ExceptionCode {

    // ==================== B90001-B90099 系统配置 ====================
    CONFIG_NOT_FOUND("B90001", "system.config.not.found", 404), // 系统配置不存在（资源未找到，HTTP 404）
    CONFIG_KEY_DUPLICATE("B90002", "system.config.key.duplicate"), // 配置键在分组内重复，违反唯一约束
    CONFIG_GROUP_INVALID("B90003", "system.config.group.invalid"), // 配置分组非法（如为空或不在允许白名单内）
    PARAM_ERROR("B90004", "system.param.error"), // 参数错误（通用）
    CONFIG_VALUE_VALIDATION_WARNING("B90005", "system.config.value.validation.warning"), // 配置值未通过 JsonSchema 校验（告警，不阻止保存）

    // ==================== B91001-B91099 字典 ====================
    DICT_TYPE_NOT_FOUND("B91001", "system.dict.type.not.found", 404), // 字典类型不存在（资源未找到，HTTP 404）
    DICT_TYPE_CODE_DUPLICATE("B91002", "system.dict.type.code.duplicate"), // 字典类型编码在租户内重复，违反唯一约束
    DICT_ITEM_NOT_FOUND("B91003", "system.dict.item.not.found", 404), // 字典项不存在（资源未找到，HTTP 404）
    DICT_ITEM_CODE_DUPLICATE("B91004", "system.dict.item.code.duplicate"), // 字典项编码在同类型内重复，违反唯一约束
    DICT_VERSION_NOT_FOUND("B91005", "system.dict.version.not.found", 404), // 字典版本不存在（资源未找到，HTTP 404）
    SNAPSHOT_PARSE_ERROR("B91006", "system.dict.snapshot.parse.error", 500), // 字典版本快照解析失败

    // ==================== B92001-B92099 系统变量 ====================
    VARIABLE_NOT_FOUND("B92001", "system.variable.not.found", 404), // 系统变量不存在（资源未找到，HTTP 404）
    VARIABLE_KEY_DUPLICATE("B92002", "system.variable.key.duplicate"), // 系统变量键在租户内重复，违反唯一约束

    // ==================== B93001-B93099 应用信息 ====================
    APP_INFO_NOT_FOUND("B93001", "system.app.info.not.found", 404), // 应用信息不存在（资源未找到，HTTP 404）
    APP_KEY_DUPLICATE("B93002", "system.app.key.duplicate"); // 应用 Key（client_id）在租户内重复，违反唯一约束

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    SystemExceptionCode(String code, String key) {
        this(code, key, 400);
    }

    SystemExceptionCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }
}
