package com.remisoft.common.domain.enums;

import java.util.Map;

import com.remisoft.common.core.constant.HeaderConstants;
import com.remisoft.common.core.constant.TokenConstants;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 数据权限范围类型枚举
 *
 * <p>定义系统中数据权限的维度类型，用于行级数据权限控制。
 * 支持租户、集团、公司、部门、用户、项目、区域等多种维度。
 *
 * <p><b>维度优先级（从高到低）：</b>
 * <ul>
 *   <li>GROUP(40) - 集团级别</li>
 *   <li>COMPANY(30) - 公司级别</li>
 *   <li>PROJECT(25) - 项目级别</li>
 *   <li>DEPT(20) - 部门级别</li>
 *   <li>REGION(15) - 区域级别</li>
 *   <li>TENANT(10) - 租户级别</li>
 *   <li>USER(5) - 用户级别</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>配合 HeaderConstants 中的 X-Data-Scope 等请求头使用</li>
 *   <li>SQL拦截器根据此枚举值决定行级数据过滤条件</li>
 *   <li>配合 @RbacDataScope 注解在AOP切面中控制数据访问</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 * @see HeaderConstants
 * @see TokenConstants
 */
@Getter
@AllArgsConstructor
public enum DataScopeType implements TypeEnum<String> {

    /**
     * 租户维度
     * <p>按租户隔离数据，用于多租户系统
     */
    TENANT("tenant", "租户", 10),

    /**
     * 集团维度
     * <p>按集团维度过滤，用户可访问集团下所有公司数据
     */
    GROUP("group", "集团", 40),

    /**
     * 公司维度
     * <p>按公司维度过滤，用户可访问公司及下属部门数据
     */
    COMPANY("company", "公司", 30),

    /**
     * 部门维度
     * <p>按部门维度过滤，用户只能访问本部门及下级部门数据
     */
    DEPT("dept", "部门", 20),

    /**
     * 用户维度
     * <p>按用户维度过滤，用户只能访问自己的数据
     */
    USER("user", "用户", 5),

    /**
     * 项目维度
     * <p>按项目维度过滤，用户可访问有权限的项目数据
     */
    PROJECT("project", "项目", 25),

    /**
     * 区域维度
     * <p>按区域维度过滤，用户可访问有权限的区域数据
     */
    REGION("region", "区域", 15),

    /**
     * 自定义维度
     * <p>使用自定义 SQL 条件进行数据过滤
     */
    CUSTOM("custom", "自定义", 50);

    /**
     * 维度类型编码
     */
    private final String code;

    /**
     * 维度类型描述
     */
    private final String desc;

    /**
     * 维度优先级
     * <p>数值越大优先级越高，用于多维度叠加时确定主导维度
     */
    private final int priority;

    /** 按维度编码索引的不可变映射，用于通过编码快速查找枚举值 */
    private static final Map<String, DataScopeType> CODE_MAP = TypeEnum.buildCodeMap(DataScopeType.class);

    /**
     * 根据编码获取数据权限范围类型
     *
     * @param code 编码值
     * @return 对应的枚举值
     * @throws IllegalArgumentException 当编码不存在或为 null 时抛出
     */
    public static DataScopeType codeOf(String code) {
        return TypeEnum.codeOf(CODE_MAP, code);
    }

    /**
     * 比较两个维度，获取优先级较高的维度
     *
     * @param a 维度A，可为 null
     * @param b 维度B，可为 null
     * @return 优先级较高的维度，若有 null 则返回非 null 的那个，两者都 null 时返回 null
     */
    public static DataScopeType max(DataScopeType a, DataScopeType b) {
        if (a == null) return b;
        if (b == null) return a;
        return a.getPriority() >= b.getPriority() ? a : b;
    }
}