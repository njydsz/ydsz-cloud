package com.njydsz.pmis.project.server.engine;

import com.njydsz.pmis.project.domain.entity.DeliveryItemDO;
import com.njydsz.pmis.project.domain.enums.DeliveryItemStatus;
import com.njydsz.pmis.project.domain.enums.DeliveryStage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段门控校验器
 *
 * <p>校验：进入下一门径前，本阶段所有"必交付"物必须 ACCEPTED 或 WAIVED；
 * 高级项目（L13+）的 TR 类交付物必须 TR 完成。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Slf4j
public class StageGateValidator {

    /**
     * 校验是否可进入目标阶段
     *
     * @param initiationId 项目 ID
     * @param targetStage  目标门径
     * @param items        当前项目所有交付物
     * @param projectLevel 项目级别（用于判断是否高级项目）
     * @return 校验结果
     */
    public static GateCheckResult check(String initiationId, DeliveryStage targetStage,
                                        List<DeliveryItemDO> items, String projectLevel) {
        if (targetStage == null) {
            return GateCheckResult.fail("目标阶段不能为空");
        }
        if (targetStage == DeliveryStage.CD1_KICKOFF) {
            return GateCheckResult.ok("CD1 启动阶段无前置校验");
        }
        // 校验前一阶段
        DeliveryStage prev = previousStage(targetStage);
        if (prev == null) {
            return GateCheckResult.ok("无前置阶段");
        }
        if (items == null) items = List.of();
        List<DeliveryItemDO> prevItems = items.stream()
                .filter(i -> prev.getCode().equals(i.getStage()))
                .toList();
        if (prevItems.isEmpty()) {
            return GateCheckResult.fail("前置阶段 " + prev.getDesc() + " 无任何交付物");
        }
        // 必交付校验
        List<String> missing = new ArrayList<>();
        for (DeliveryItemDO i : prevItems) {
            if (Integer.valueOf(1).equals(i.getRequired())) {
                DeliveryItemStatus st = DeliveryItemStatus.fromCode(i.getStatus());
                boolean passed = (st == DeliveryItemStatus.ACCEPTED
                        || st == DeliveryItemStatus.WAIVED);
                if (!passed) {
                    missing.add(i.getDeliveryName() + "("
                            + (st == null ? "未知" : st.getDesc()) + ")");
                }
                // 高级项目 TR 校验
                if (isHighLevel(projectLevel)
                        && Integer.valueOf(1).equals(i.getTrRequired())
                        && Integer.valueOf(1).equals(i.getTrCompleted()) == false) {
                    missing.add(i.getDeliveryName() + "(TR 未完成)");
                }
            }
        }
        if (!missing.isEmpty()) {
            return GateCheckResult.fail(
                    "无法进入 " + targetStage.getDesc() + "，以下必交付未通过: " + String.join("、", missing));
        }
        log.debug("[StageGate] 项目 {} 进入 {} 校验通过", initiationId, targetStage.getCode());
        return GateCheckResult.ok("通过");
    }

    /**
     * 获取前置阶段
     *
     * @param s 当前阶段
     * @return 前置阶段枚举；无前置时返回 null
     */
    public static DeliveryStage previousStage(DeliveryStage s) {
        if (s == null) return null;
        return switch (s) {
            case CD2_DESIGN -> DeliveryStage.CD1_KICKOFF;
            case CD3_BUILD -> DeliveryStage.CD2_DESIGN;
            case CD4_UAT -> DeliveryStage.CD3_BUILD;
            case CD5_GO_LIVE -> DeliveryStage.CD4_UAT;
            default -> null;
        };
    }

    /**
     * 判断是否为高级项目（L13 及以上）
     *
     * @param level 项目级别编码（如 L13、L14）
     * @return true 表示高级项目
     */
    public static boolean isHighLevel(String level) {
        if (level == null || level.isBlank()) return false;
        // L13+ 视为高级项目
        try {
            String num = level.toUpperCase().replace("L", "");
            int n = Integer.parseInt(num);
            return n >= 13;
        } catch (Exception ignore) {
            log.warn("[StageGateValidator] 项目等级解析失败 level={}: {}", level, ignore.getMessage());
            return false;
        }
    }

    /**
     * 校验结果
     */
    public record GateCheckResult(boolean passed, String message) {
        /**
         * 构造校验通过结果
         *
         * @param msg 描述信息
         * @return 通过结果
         */
        public static GateCheckResult ok(String msg) { return new GateCheckResult(true, msg); }
        /**
         * 构造校验失败结果
         *
         * @param msg 描述信息
         * @return 失败结果
         */
        public static GateCheckResult fail(String msg) { return new GateCheckResult(false, msg); }
    }
}
