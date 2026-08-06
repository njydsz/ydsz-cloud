package com.remisoft.common.domain.enums;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.remisoft.common.core.constant.HeaderConstants;

import lombok.AllArgsConstructor;
import lombok.Getter;
/**
 * 服务类型枚举定义
 *
 * <p>定义系统服务的类型，用于区分不同来源的请求。
 *
 * <p><b>迁移计划：</b>
 * 此枚举包含网关路由配置（pathPrefix），计划迁移至 {@code remi-gateway} 模块。
 * 现有引用模块：common-util、common-web、common-app。
 * 请这些模块在 1.9.0 之前完成迁移，使用本地定义的枚举替代。
 *
 * @author remi-team
 * @since 1.0.0
 * @deprecated 1.7.0 迁移至 remi-gateway 模块，1.9.0 移除
 * @see HeaderConstants
 * @see TypeEnum 替代实现基类
 */
@Getter
@AllArgsConstructor
@Deprecated(since = "1.7.0", forRemoval = true)
public enum ServiceType implements TypeEnum<String> {

    /**
     * 管理端服务
     * <p>用于Web管理后台的请求，路径前缀为 /web-api/**
     */
    WEB_SERVICE("webService", "/web-api/**", "网页端服务"),

    /**
     * 移动端服务
     * <p>用于移动App的请求，路径前缀为 /app-api/**
     */
    APP_SERVICE("appService", "/app-api/**", "移动端服务");

    /**
     * 服务类型编码
     */
    private final String code;

    /**
     * 路径前缀
     */
    private final String pathPrefix;

    /**
     * 服务类型描述
     */
    private final String desc;

    /** 按服务类型编码索引的不可变映射，用于通过编码快速查找枚举值 */
    private static final Map<String, ServiceType> CODE_MAP = TypeEnum.buildCodeMap(ServiceType.class);
    /** 按路径前缀索引的不可变映射，用于通过路径前缀快速查找枚举值 */
    private static final Map<String, ServiceType> PATH_PREFIX_MAP;

    static {
        PATH_PREFIX_MAP = Collections.unmodifiableMap(
                Arrays.stream(values())
                        .collect(Collectors.toMap(ServiceType::getPathPrefix, Function.identity()))
        );
    }

    /**
     * 根据编码获取服务类型（安全版本）
     *
     * @param code 服务类型编码
     * @return 对应的枚举值，未找到返回 null
     */
    public static ServiceType of(String code) {
        if (code == null) {
            return null;
        }
        return CODE_MAP.get(code);
    }

    /**
     * 根据编码获取服务类型
     *
     * @param code 服务类型编码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 当编码不存在时抛出
     */
    public static ServiceType codeOf(String code) {
        return TypeEnum.codeOf(CODE_MAP, code);
    }

    /**
     * 根据路径前缀获取服务类型
     *
     * @param pathPrefix 路径前缀
     * @return 对应的枚举值，未找到返回 null
     */
    public static ServiceType pathPrefixOf(String pathPrefix) {
        if (pathPrefix == null) {
            return null;
        }
        return PATH_PREFIX_MAP.get(pathPrefix);
    }

    /**
     * 检查编码是否有效
     *
     * @param code 服务类型编码
     * @return 有效返回 true，否则返回 false
     */
    public static boolean isValidCode(String code) {
        return of(code) != null;
    }

    /**
     * 检查路径前缀是否有效
     *
     * @param pathPrefix 路径前缀
     * @return 有效返回 true，否则返回 false
     */
    public static boolean isValidPathPrefix(String pathPrefix) {
        return pathPrefixOf(pathPrefix) != null;
    }
}
