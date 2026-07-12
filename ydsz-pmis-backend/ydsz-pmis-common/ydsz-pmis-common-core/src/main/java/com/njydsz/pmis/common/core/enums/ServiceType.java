package com.njydsz.pmis.common.core.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.Collections;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 服务类型枚举定义
 *
 * <p>定义系统服务的类型，用于区分不同来源的请求。
 * 支持管理端（WEB）和移动端（APP）两种服务类型。
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>配合 HeaderConstants.X_SERVICE_TYPE 请求头使用</li>
 *   <li>区分不同服务的访问控制和路由</li>
 *   <li>服务间调用的权限验证</li>
 * </ul>
 *
 * @author Marvin Lee
 * @email limw1888@126.com
 * @version 3.5.0
 * @see com.njydsz.pmis.common.core.constant.HeaderConstants
 */
@Getter
@AllArgsConstructor
public enum ServiceType implements TypeEnum<String> {

    /**
     * 管理端服务
     * <p>用于Web管理后台的请求，路径前缀为 /web-api/**
     */
    WEB_SERVICE("webService", "/web-api/**", "管理端服务"),

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
    private static final Map<String, ServiceType> CODE_MAP;
    /** 按路径前缀索引的不可变映射，用于通过路径前缀快速查找枚举值 */
    private static final Map<String, ServiceType> PATH_PREFIX_MAP;

    static {
        CODE_MAP = Collections.unmodifiableMap(
                Arrays.stream(values())
                        .collect(Collectors.toMap(ServiceType::getCode, Function.identity()))
        );
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
        ServiceType value = of(code);
        if (value == null) {
            throw new IllegalArgumentException("Unknown ServiceType code: " + code);
        }
        return value;
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
     * @return 有效返回true，否则返回false
     */
    public static boolean isValidCode(String code) {
        return of(code) != null;
    }

    /**
     * 检查路径前缀是否有效
     *
     * @param pathPrefix 路径前缀
     * @return 有效返回true，否则返回false
     */
    public static boolean isValidPathPrefix(String pathPrefix) {
        return pathPrefixOf(pathPrefix) != null;
    }
}