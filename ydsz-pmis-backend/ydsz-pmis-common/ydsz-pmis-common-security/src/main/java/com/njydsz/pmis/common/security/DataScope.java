package com.njydsz.pmis.common.security;

import lombok.Getter;

/**
 * 数据权限范围
 *
 * <p>控制业务查询/写入能访问的数据边界：
 * <ul>
 *   <li>{@link #ALL} - 全部数据（超管/特殊角色）</li>
 *   <li>{@link #DEPT} - 本部门数据（dept_id = 当前用户部门）</li>
 *   <li>{@link #DEPT_AND_CHILD} - 本部门及下级部门</li>
 *   <li>{@link #SELF} - 仅本人数据（creator_id = 当前用户）</li>
 *   <li>{@link #CUSTOM} - 自定义（通过 role.dept_ids 配置）</li>
 *   <li>{@link #PROJECT} - 项目级（project_id in (…)，来自项目成员）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Getter
public enum DataScope {

    /** 全部 */
    ALL(0, "全部"),
    /** 本部门 */
    DEPT(1, "本部门"),
    /** 本部门及下级 */
    DEPT_AND_CHILD(2, "本部门及下级"),
    /** 仅本人 */
    SELF(3, "仅本人"),
    /** 自定义部门集 */
    CUSTOM(4, "自定义"),
    /** 项目成员 */
    PROJECT(5, "项目成员");

    /** 数据权限范围编码 */
    private final int code;

    /** 数据权限范围描述 */
    private final String desc;

    DataScope(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 从字符串解析，未知值默认为 SELF（最严格）
     *
     * @param s 字符串值
     * @return 解析得到的 DataScope
     */
    public static DataScope parse(String s) {
        if (s == null || s.isEmpty()) {
            return SELF;
        }
        try {
            return DataScope.valueOf(s.trim().toUpperCase());
        } catch (Exception e) {
            return SELF;
        }
    }

    /**
     * 是否能访问跨部门数据
     *
     * @return true 表示可访问全部数据
     */
    public boolean isCrossDept() {
        return this == ALL;
    }
}
