package com.njydsz.common.json.internal;

import java.util.List;

/**
 * JSON 双体系冲突异常。
 *
 * <p>当启用严格模式（{@link JsonConfig#isStrictMode()} = true）且检测到项目同时存在
 * YdszJson 与 Jackson 注解/依赖时抛出，阻止应用启动。
 *
 * <p><b>触发条件：</b>
 * <ul>
 *   <li>{@code ydsz.json.strict-mode=true}</li>
 *   <li>扫描发现 @JsonClass 注解的类同时使用 Jackson 注解（{@code com.fasterxml.jackson.*}）</li>
 *   <li>类路径中检测到 Jackson 依赖（{@code ObjectMapper} 类存在）</li>
 * </ul>
 *
 * <p><b>解决方式：</b>
 * <ul>
 *   <li>移除业务代码中的 Jackson 注解，统一使用 {@code @JsonClass} / {@code @JsonField}</li>
 *   <li>排除 Jackson 依赖（在 pom 中 exclude {@code spring-boot-starter-json}）</li>
 *   <li>如确需双体系共存，关闭 {@code ydsz.json.strict-mode}（不推荐）</li>
 * </ul>
 *
 * @author ydsz-team
 * @since 1.2.0
 */
public class DualJsonConflictException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 冲突详情列表 */
    private final List<DualJsonConflict> conflicts;

    /**
     * 构造双体系冲突异常
     *
     * @param message   错误消息摘要
     * @param conflicts 冲突详情列表
     */
    public DualJsonConflictException(String message, List<DualJsonConflict> conflicts) {
        super(message);
        this.conflicts = conflicts;
    }

    /**
     * 获取所有冲突详情
     *
     * @return 冲突详情列表
     */
    public List<DualJsonConflict> getConflicts() {
        return conflicts;
    }

    /**
     * 单条冲突记录
     */
    public static class DualJsonConflict {

        /** 检测到冲突的全限定类名 */
        private final String className;

        /** 冲突类型 */
        private final ConflictType type;

        /** 冲突描述 */
        private final String description;

        public DualJsonConflict(String className, ConflictType type, String description) {
            this.className = className;
            this.type = type;
            this.description = description;
        }

        public String getClassName() {
            return className;
        }

        public ConflictType getType() {
            return type;
        }

        public String getDescription() {
            return description;
        }

        @Override
        public String toString() {
            return "[" + type + "] " + className + ": " + description;
        }
    }

    /**
     * 冲突类型枚举
     */
    public enum ConflictType {
        /** 类同时使用 @JsonClass 和 Jackson 注解 */
        MIXED_ANNOTATIONS,
        /** 类路径中检测到 Jackson 依赖（ObjectMapper） */
        JACKSON_ON_CLASSPATH,
        /** Jackson 注解出现在非 @JsonClass 的类上（可能由 Spring Boot Actuator 等组件引入） */
        EXTERNAL_JACKSON_ANNOTATION
    }
}
