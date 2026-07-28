package com.njydsz.system.domain.enums;

import java.util.HashMap;
import java.util.Map;

import com.njydsz.common.exception.enums.ExceptionCode;
import com.njydsz.common.exception.enums.ExceptionCodeRegistry;

import lombok.Getter;

/**
 * 系统管理模块异常码枚举。
 *
 * <p>实现 {@link ExceptionCode} 接口，通过 {@link ExceptionCodeRegistry} 全局注册，
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
public enum SystemResultCode implements ExceptionCode {

    // ==================== B90001-B90099 系统配置 ====================
    CONFIG_NOT_FOUND("B90001", "system.config.not.found", 404),
    CONFIG_KEY_DUPLICATE("B90002", "system.config.key.duplicate"),
    CONFIG_GROUP_INVALID("B90003", "system.config.group.invalid"),

    // ==================== B91001-B91099 字典 ====================
    DICT_TYPE_NOT_FOUND("B91001", "system.dict.type.not.found", 404),
    DICT_TYPE_CODE_DUPLICATE("B91002", "system.dict.type.code.duplicate"),
    DICT_ITEM_NOT_FOUND("B91003", "system.dict.item.not.found", 404),
    DICT_ITEM_CODE_DUPLICATE("B91004", "system.dict.item.code.duplicate"),
    DICT_VERSION_NOT_FOUND("B91005", "system.dict.version.not.found", 404),

    // ==================== B92001-B92099 系统变量 ====================
    VARIABLE_NOT_FOUND("B92001", "system.variable.not.found", 404),
    VARIABLE_KEY_DUPLICATE("B92002", "system.variable.key.duplicate"),

    // ==================== B93001-B93099 应用信息 ====================
    APP_INFO_NOT_FOUND("B93001", "system.app.info.not.found", 404),
    APP_KEY_DUPLICATE("B93002", "system.app.key.duplicate")

    /** 错误码 */
    private final String code;
    /** 国际化消息键 */
    private final String key;
    /** HTTP 状态码 */
    private final int httpStatus;

    SystemResultCode(String code, String key) {
        this(code, key, 400);
    }

    SystemResultCode(String code, String key, int httpStatus) {
        this.code = code;
        this.key = key;
        this.httpStatus = httpStatus;
    }

    static {
        Map<String, ExceptionCode> registryMap = new HashMap<>();
        for (SystemResultCode c : values()) {
            registryMap.put(c.getCode(), c);
        }
        ExceptionCodeRegistry.register(registryMap);
    }
}
