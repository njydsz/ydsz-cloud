package com.njydsz.pmis.project.enums;

/**
 * 门径评审点 (CDCP - Critical Decision Checkpoint)
 *
 * <ul>
 *   <li>CD1: 立项决策（Entry）</li>
 *   <li>CD2: 启动决策（Kick-off）</li>
 *   <li>CD3: 中期决策（Mid-term）</li>
 *   <li>CD4: 验收决策（Acceptance）</li>
 *   <li>CD5: 结项决策（Closure）</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
public enum GateCode {
    CD1, CD2, CD3, CD4, CD5;

    /**
     * 根据状态码解析枚举。
     *
     * @param code 状态码，大小写不敏感，为 null 或解析失败时返回 null
     * @return 匹配到的枚举值；未匹配返回 null
     */
    public static GateCode fromCode(String code) {
        if (code == null) return null;
        try {
            return GateCode.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * 获取当前门径评审点的下一个评审点。
     *
     * @param current 当前评审点，为 null 时视为起点，返回 CD1
     * @return 下一个评审点；若 current 为 CD5（终态），返回 null
     */
    public static GateCode next(GateCode current) {
        if (current == null) return CD1;
        return switch (current) {
            case CD1 -> CD2;
            case CD2 -> CD3;
            case CD3 -> CD4;
            case CD4 -> CD5;
            case CD5 -> null;
        };
    }
}
