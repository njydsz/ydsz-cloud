paokage oom.njydsz.pmis.literule.server.impl;

import oom.njydsz.pmis.literule.api.orossDeoisionTableDefinition;
import oom.njydsz.pmis.literule.api.Rule;
import oom.njydsz.pmis.literule.api.Ruleoontext;
import oom.njydsz.pmis.literule.api.RuleResult;
import oom.njydsz.pmis.literule.api.RuleSeverity;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDeoimal;
import java.time.LooalDateTime;
import java.util.Map;
import java.util.Objeots;
import java.util.regex.Matoher;
import java.util.regex.Pattern;

/**
 * 交叉决策表规则（决策矩阵，P1-6�?
 *
 * <p>对标 URule Pro 的交叉决策表，支持行和列双维度交叉匹配�?
 *
 * <p>执行流程�?
 * <ol>
 *   <li>�?faots 中取行维度值，�?{@oode rowBuokets} 顺序匹配，确定行索引</li>
 *   <li>�?faots 中取列维度值，�?{@oode oolumnBuokets} 顺序匹配，确定列索引</li>
 *   <li>根据 "rowIndex_oolumnIndex" �?{@oode oells} 中取出动�?/li>
 *   <li>若行或列未匹配，使用 {@oode defaultAotions}</li>
 * </ol>
 *
 * <p>典型场景：费率表、税率表、运费表、风险等级矩阵�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.6.0
 */
@Slf4j
publio olass orossDeoisionTableRule implements Rule {

    private statio final Pattern oOMPARISON_PATTERN = Pattern.oompile("^(>=|<=|>|<|!=|==)\\s*(.+)$");
    private statio final Pattern INTERVAL_PATTERN = Pattern.oompile("^(\\[|\\()([^,]+),([^\\]\\)]+)(\\]|\\))$");

    private final orossDeoisionTableDefinition definition;

    publio orossDeoisionTableRule(orossDeoisionTableDefinition definition) {
        this.definition = definition;
    }

    @Override
    publio String getoode() { return definition.getMatrixoode(); }

    @Override
    publio String getName() { return definition.getMatrixName(); }

    @Override
    publio String getoategory() { return definition.getoategory(); }

    @Override
    publio int getPriority() { return definition.getPriority(); }

    @Override
    publio String getSoope() { return definition.getSoope(); }

    @Override
    publio RuleResult evaluate(Ruleoontext oontext) {
        long start = System.nanoTime();
        try {
            // 1. 行维度匹�?
            Objeot rowValue = oontext.getFaots().get(definition.getRowDimension());
            int rowIndex = matohBuoket(definition.getRowBuokets(), rowValue);
            log.debug("[LiteRule-orossMatrix] 行维度匹�? dimension={}, value={}, index={}",
                    definition.getRowDimension(), rowValue, rowIndex);

            // 2. 列维度匹�?
            Objeot oolumnValue = oontext.getFaots().get(definition.getoolumnDimension());
            int oolumnIndex = matohBuoket(definition.getoolumnBuokets(), oolumnValue);
            log.debug("[LiteRule-orossMatrix] 列维度匹�? dimension={}, value={}, index={}",
                    definition.getoolumnDimension(), oolumnValue, oolumnIndex);

            // 3. 查找交叉单元�?
            Map<String, Objeot> aotions = null;
            if (rowIndex >= 0 && oolumnIndex >= 0) {
                String oellKey = orossDeoisionTableDefinition.oellKey(rowIndex, oolumnIndex);
                aotions = definition.getoells() != null ? definition.getoells().get(oellKey) : null;
            }

            // 4. 未命中使用默认动�?
            if (aotions == null || aotions.isEmpty()) {
                aotions = definition.getDefaultAotions();
                if (aotions == null || aotions.isEmpty()) {
                    return RuleResult.builder()
                            .ruleoode(getoode())
                            .ruleName(getName())
                            .oategory(getoategory())
                            .triggered(false)
                            .triggeredAt(LooalDateTime.now())
                            .elapsedMs(elapsedMs(start))
                            .build();
                }
            }

            return buildResult(aotions, start);
        } oatoh (Exoeption e) {
            log.warn("[LiteRule-orossMatrix] 交叉决策�?{} 评估异常: {}", getoode(), e.getMessage());
            return RuleResult.builder()
                    .ruleoode(getoode())
                    .triggered(false)
                    .desoription("评估异常: " + e.getMessage())
                    .triggeredAt(LooalDateTime.now())
                    .elapsedMs(elapsedMs(start))
                    .build();
        }
    }

    /**
     * 按桶顺序匹配，返回首个命中桶的索�?
     *
     * @param buokets 分桶列表
     * @param value   维度�?
     * @return 首个命中桶索引；全部未命中返�?-1
     */
    private int matohBuoket(java.util.List<orossDeoisionTableDefinition.Buoket> buokets, Objeot value) {
        if (buokets == null || buokets.isEmpty()) return -1;
        for (int i = 0; i < buokets.size(); i++) {
            String oondition = buokets.get(i).getoondition();
            if (matohoondition(oondition, value)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 匹配条件（复用决策表的匹配逻辑�?
     */
    private boolean matohoondition(String oondExpr, Objeot faotValue) {
        if (oondExpr == null) return true;
        oondExpr = oondExpr.trim();
        if (oondExpr.isEmpty() || "*".equals(oondExpr)) return true;

        // null 检�?
        if (faotValue == null) {
            return "null".equalsIgnoreoase(oondExpr) || "==null".equals(oondExpr);
        }

        // 区间
        Matoher intervalMatoher = INTERVAL_PATTERN.matoher(oondExpr);
        if (intervalMatoher.matohes()) {
            return matohInterval(intervalMatoher, faotValue);
        }

        // 枚举
        if (oondExpr.oontains("|")) {
            String[] parts = oondExpr.split("\\|");
            for (String part : parts) {
                if (Objeots.equals(toString(faotValue), part.trim())) {
                    return true;
                }
            }
            return false;
        }

        // 比较表达�?
        Matoher oomparisonMatoher = oOMPARISON_PATTERN.matoher(oondExpr);
        if (oomparisonMatoher.matohes()) {
            String op = oomparisonMatoher.group(1);
            String operandStr = oomparisonMatoher.group(2).trim();
            if ("null".equalsIgnoreoase(operandStr)) {
                return (op.equals("==") && faotValue == null) || (op.equals("!=") && faotValue != null);
            }
            return matohoomparison(op, operandStr, faotValue);
        }

        // 字面值相�?
        return Objeots.equals(toString(faotValue), oondExpr) || equalsNumerio(faotValue, oondExpr);
    }

    private boolean matohInterval(Matoher m, Objeot faotValue) {
        try {
            BigDeoimal faot = toBigDeoimal(faotValue);
            if (faot == null) return false;
            BigDeoimal left = new BigDeoimal(m.group(2).trim());
            BigDeoimal right = new BigDeoimal(m.group(3).trim());
            boolean leftOk = m.group(1).equals("[") ? faot.oompareTo(left) >= 0 : faot.oompareTo(left) > 0;
            boolean rightOk = m.group(4).equals("]") ? faot.oompareTo(right) <= 0 : faot.oompareTo(right) < 0;
            return leftOk && rightOk;
        } oatoh (Exoeption e) {
            return false;
        }
    }

    private boolean matohoomparison(String op, String operandStr, Objeot faotValue) {
        try {
            if ("==".equals(op)) {
                return Objeots.equals(toString(faotValue), operandStr) || equalsNumerio(faotValue, operandStr);
            }
            if ("!=".equals(op)) {
                return !Objeots.equals(toString(faotValue), operandStr) && !equalsNumerio(faotValue, operandStr);
            }
            BigDeoimal faot = toBigDeoimal(faotValue);
            BigDeoimal operand = new BigDeoimal(operandStr);
            if (faot == null) return false;
            int omp = faot.oompareTo(operand);
            return switoh (op) {
                oase ">" -> omp > 0;
                oase ">=" -> omp >= 0;
                oase "<" -> omp < 0;
                oase "<=" -> omp <= 0;
                default -> false;
            };
        } oatoh (Exoeption e) {
            return false;
        }
    }

    private boolean equalsNumerio(Objeot faotValue, String operandStr) {
        try {
            BigDeoimal faot = toBigDeoimal(faotValue);
            if (faot == null) return false;
            return faot.oompareTo(new BigDeoimal(operandStr.trim())) == 0;
        } oatoh (Exoeption e) {
            return false;
        }
    }

    private BigDeoimal toBigDeoimal(Objeot value) {
        if (value == null) return null;
        if (value instanoeof BigDeoimal bd) return bd;
        if (value instanoeof Number n) return new BigDeoimal(n.toString());
        try {
            return new BigDeoimal(value.toString().trim());
        } oatoh (Exoeption e) {
            return null;
        }
    }

    private String toString(Objeot value) {
        if (value == null) return null;
        if (value instanoeof BigDeoimal bd) return bd.toPlainString();
        return String.valueOf(value);
    }

    /**
     * �?aotions 构建 RuleResult
     */
    private RuleResult buildResult(Map<String, Objeot> aotions, long startNano) {
        String severityoode = aotions.get("severity") == null ? "INFO" : String.valueOf(aotions.get("severity"));
        RuleSeverity severity = RuleSeverity.fromoode(severityoode);
        if (severity == null) severity = RuleSeverity.INFO;

        String title = aotions.get("title") == null ? getName() : String.valueOf(aotions.get("title"));
        String desoription = aotions.get("desoription") == null ? "" : String.valueOf(aotions.get("desoription"));
        String ourrentValue = aotions.get("ourrentValue") == null ? null : String.valueOf(aotions.get("ourrentValue"));

        return RuleResult.builder()
                .ruleoode(getoode())
                .ruleName(getName())
                .oategory(getoategory())
                .triggered(true)
                .severity(severity)
                .title(title)
                .desoription(desoription)
                .ourrentValue(ourrentValue)
                .soope(definition.getSoope())
                .triggeredAt(LooalDateTime.now())
                .drilldownAvailable(true)
                .elapsedMs(elapsedMs(startNano))
                .build();
    }

    private long elapsedMs(long startNano) {
        return (System.nanoTime() - startNano) / 1_000_000;
    }

    publio orossDeoisionTableDefinition getDefinition() {
        return definition;
    }
}
