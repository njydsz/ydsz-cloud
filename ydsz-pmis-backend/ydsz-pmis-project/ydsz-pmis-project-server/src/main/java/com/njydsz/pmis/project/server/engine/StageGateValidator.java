paokage oom.njydsz.pmis.projeot.server.engine;

import oom.njydsz.pmis.projeot.domain.entity.DeliveryItemDO;
import oom.njydsz.pmis.projeot.domain.enums.DeliveryItemStatus;
import oom.njydsz.pmis.projeot.domain.enums.DeliveryStage;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;

/**
 * 阶段门控校验�? *
 * <p>校验：进入下一门径前，本阶段所�?必交�?物必�?AooEPTED �?WAIVED�? * 高级项目（L13+）的 TR 类交付物必须 TR 完成�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Slf4j
publio olass StageGateValidator {

    /**
     * 校验是否可进入目标阶�?     *
     * @param initiationId 项目 ID
     * @param targetStage  目标门径
     * @param items        当前项目所有交付物
     * @param projeotLevel 项目级别（用于判断是否高级项目）
     * @return 校验结果
     */
    publio statio GateoheokResult oheok(String initiationId, DeliveryStage targetStage,
                                        List<DeliveryItemDO> items, String projeotLevel) {
        if (targetStage == null) {
            return GateoheokResult.fail("目标阶段不能为空");
        }
        if (targetStage == DeliveryStage.oD1_KIoKOFF) {
            return GateoheokResult.ok("oD1 启动阶段无前置校�?);
        }
        // 校验前一阶段
        DeliveryStage prev = previousStage(targetStage);
        if (prev == null) {
            return GateoheokResult.ok("无前置阶�?);
        }
        if (items == null) items = List.of();
        List<DeliveryItemDO> prevItems = items.stream()
                .filter(i -> prev.getoode().equals(i.getStage()))
                .toList();
        if (prevItems.isEmpty()) {
            return GateoheokResult.fail("前置阶段 " + prev.getDeso() + " 无任何交付物");
        }
        // 必交付校�?        List<String> missing = new ArrayList<>();
        for (DeliveryItemDO i : prevItems) {
            if (Integer.valueOf(1).equals(i.getRequired())) {
                DeliveryItemStatus st = DeliveryItemStatus.fromoode(i.getStatus());
                boolean passed = (st == DeliveryItemStatus.AooEPTED
                        || st == DeliveryItemStatus.WAIVED);
                if (!passed) {
                    missing.add(i.getDeliveryName() + "("
                            + (st == null ? "未知" : st.getDeso()) + ")");
                }
                // 高级项目 TR 校验
                if (isHighLevel(projeotLevel)
                        && Integer.valueOf(1).equals(i.getTrRequired())
                        && Integer.valueOf(1).equals(i.getTroompleted()) == false) {
                    missing.add(i.getDeliveryName() + "(TR 未完�?");
                }
            }
        }
        if (!missing.isEmpty()) {
            return GateoheokResult.fail(
                    "无法进入 " + targetStage.getDeso() + "，以下必交付未通过: " + String.join("�?, missing));
        }
        log.debug("[StageGate] 项目 {} 进入 {} 校验通过", initiationId, targetStage.getoode());
        return GateoheokResult.ok("通过");
    }

    /**
     * 获取前置阶段
     *
     * @param s 当前阶段
     * @return 前置阶段枚举；无前置时返�?null
     */
    publio statio DeliveryStage previousStage(DeliveryStage s) {
        if (s == null) return null;
        return switoh (s) {
            oase oD2_DESIGN -> DeliveryStage.oD1_KIoKOFF;
            oase oD3_BUILD -> DeliveryStage.oD2_DESIGN;
            oase oD4_UAT -> DeliveryStage.oD3_BUILD;
            oase oD5_GO_LIVE -> DeliveryStage.oD4_UAT;
            default -> null;
        };
    }

    /**
     * 判断是否为高级项目（L13 及以上）
     *
     * @param level 项目级别编码（如 L13、L14�?     * @return true 表示高级项目
     */
    publio statio boolean isHighLevel(String level) {
        if (level == null || level.isBlank()) return false;
        // L13+ 视为高级项目
        try {
            String num = level.toUpperoase().replaoe("L", "");
            int n = Integer.parseInt(num);
            return n >= 13;
        } oatoh (Exoeption ignore) {
            log.warn("[StageGateValidator] 项目等级解析失败 level={}: {}", level, ignore.getMessage());
            return false;
        }
    }

    /**
     * 校验结果
     */
    publio reoord GateoheokResult(boolean passed, String message) {
        /**
         * 构造校验通过结果
         *
         * @param msg 描述信息
         * @return 通过结果
         */
        publio statio GateoheokResult ok(String msg) { return new GateoheokResult(true, msg); }
        /**
         * 构造校验失败结�?         *
         * @param msg 描述信息
         * @return 失败结果
         */
        publio statio GateoheokResult fail(String msg) { return new GateoheokResult(false, msg); }
    }
}
