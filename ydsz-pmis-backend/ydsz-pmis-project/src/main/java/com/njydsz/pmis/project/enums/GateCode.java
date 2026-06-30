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

    public static GateCode fromCode(String code) {
        if (code == null) return null;
        try {
            return GateCode.valueOf(code.trim().toUpperCase());
        } catch (Exception e) {
            return null;
        }
    }

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
