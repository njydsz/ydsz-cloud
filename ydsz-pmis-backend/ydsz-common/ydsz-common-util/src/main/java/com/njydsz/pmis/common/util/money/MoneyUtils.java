package com.njydsz.common.util.money;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 *
 * @author ydsz-team
 * @since 1.0.0
 * 
 *
 *       参考互联网大厂实现，提供更强大、更精准的金额处理功能
 *       遵循《中华人民共和国国家标准 GB/T 15835-2011》数字用法规定
 */
public class MoneyUtils {

    private MoneyUtils() {
        throw new UnsupportedOperationException("MoneyUtils is a utility class and cannot be instantiated");
    }

    private static final String[] CN_UPPER_NUMBER = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
    private static final String[] CN_UPPER_MONETARY_UNIT = {"分", "角", "元", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟", "兆", "拾", "佰", "仟"};
    private static final String CN_FULL = "整";
    private static final String CN_NEGATIVE = "负";
    private static final String CN_ZERO_FULL = "零元整";
    
    private static final BigDecimal MAX_MONEY = new BigDecimal("999999999999999.99");
    private static final BigDecimal MIN_MONEY = new BigDecimal("-999999999999999.99");
    private static final BigDecimal HUNDRED = new BigDecimal("100");
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    /**
     * 金额转中文大写 (支持完整的零处理逻辑)
     * 遵循《中华人民共和国国家标准 GB/T 15835-2011》数字用法规定
     * 
     * @param money 金额
     * @return 中文大写金额
     */
    public static String numberToCn(BigDecimal money) {
        if (money == null || money.signum() == 0) {
            return CN_ZERO_FULL;
        }
        
        if (money.compareTo(MAX_MONEY) > 0 || money.compareTo(MIN_MONEY) < 0) {
            throw new IllegalArgumentException("金额超出限制范围：" + MIN_MONEY + " 到 " + MAX_MONEY);
        }

        StringBuilder sb = new StringBuilder();
        if (money.signum() == -1) {
            sb.append(CN_NEGATIVE);
            money = money.abs();
        }

        long value = money.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValue();
        if (value == 0) {
            return CN_ZERO_FULL;
        }
        
        String strValue = String.valueOf(value);
        int len = strValue.length();
        boolean zeroFlag = false;
        
        for (int i = 0; i < len; i++) {
            int num = strValue.charAt(i) - '0';
            int unitPos = len - i - 1;
            
            if (num != 0) {
                if (zeroFlag) {
                    sb.append(CN_UPPER_NUMBER[0]);
                    zeroFlag = false;
                }
                sb.append(CN_UPPER_NUMBER[num]).append(CN_UPPER_MONETARY_UNIT[unitPos]);
            } else {
                if (unitPos == 2 || unitPos == 6 || unitPos == 10 || unitPos == 14) {
                    if (zeroFlag) {
                        sb.append(CN_UPPER_MONETARY_UNIT[unitPos]);
                        zeroFlag = false;
                    } else {
                        if (sb.length() > 0 && !sb.toString().endsWith(CN_UPPER_MONETARY_UNIT[unitPos])) {
                            sb.append(CN_UPPER_MONETARY_UNIT[unitPos]);
                        }
                    }
                } else {
                    zeroFlag = true;
                }
            }
        }

        String result = sb.toString();
        if (value % 100 == 0) {
            if (!result.endsWith(CN_FULL)) {
                result = result + CN_FULL;
            }
        }

        result = result.replaceAll("零 [拾佰仟]", "零")
                .replaceAll("零 + 万", "万")
                .replaceAll("零 + 亿", "亿")
                .replaceAll("零 + 元", "元")
                .replaceAll("零+", "零")
                .replaceAll("零元", "元")
                .replaceAll("亿万", "亿");
        
        if (result.startsWith("零")) {
            result = result.substring(1);
        }
        
        return result;
    }

    /**
     * 格式化金额 (会计格式：#,##0.00)
     * 
     * @param money 金额
     * @return 格式化后的字符串
     */
    public static String format(BigDecimal money) {
        if (money == null) {
            return "0.00";
        }
        DecimalFormat df = new DecimalFormat("#,##0.00");
        return df.format(money);
    }
    
    /**
     * 格式化金额 (自定义小数位数)
     * 
     * @param money 金额
     * @param scale 小数位数
     * @return 格式化后的字符串
     */
    public static String format(BigDecimal money, int scale) {
        if (money == null) {
            return buildFormatPattern(0, scale);
        }
        String pattern = buildFormatPattern(scale, scale);
        DecimalFormat df = new DecimalFormat(pattern);
        return df.format(money);
    }
    
    /**
     * 构建格式化模式
     */
    private static String buildFormatPattern(int minFractionDigits, int maxFractionDigits) {
        StringBuilder pattern = new StringBuilder("#,##0");
        if (maxFractionDigits > 0) {
            pattern.append(".");
            for (int i = 0; i < minFractionDigits; i++) {
                pattern.append("0");
            }
            for (int i = minFractionDigits; i < maxFractionDigits; i++) {
                pattern.append("#");
            }
        }
        return pattern.toString();
    }
    
    /**
     * 格式化金额 (不带千分位)
     * 
     * @param money 金额
     * @return 格式化后的字符串
     */
    public static String formatSimple(BigDecimal money) {
        if (money == null) {
            return "0.00";
        }
        return money.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    /**
     * 分转元
     * 
     * @param fen 分
     * @return 元
     */
    public static BigDecimal fenToYuan(Long fen) {
        if (fen == null) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(fen).divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 分转元 (支持 BigDecimal)
     * 
     * @param fen 分
     * @return 元
     */
    public static BigDecimal fenToYuan(BigDecimal fen) {
        if (fen == null) {
            return BigDecimal.ZERO;
        }
        return fen.divide(HUNDRED, 2, RoundingMode.HALF_UP);
    }

    /**
     * 元转分
     * 
     * @param yuan 元
     * @return 分
     */
    public static Long yuanToFen(BigDecimal yuan) {
        if (yuan == null) {
            return 0L;
        }
        return yuan.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP).longValue();
    }
    
    /**
     * 元转分 (返回 BigDecimal，避免精度丢失)
     * 
     * @param yuan 元
     * @return 分
     */
    public static BigDecimal yuanToFenExact(BigDecimal yuan) {
        if (yuan == null) {
            return BigDecimal.ZERO;
        }
        return yuan.multiply(HUNDRED).setScale(0, RoundingMode.HALF_UP);
    }
    
    /**
     * 金额加法 (空值视为 0)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return 和
     */
    public static BigDecimal add(BigDecimal v1, BigDecimal v2) {
        return (v1 == null ? ZERO : v1).add(v2 == null ? ZERO : v2);
    }
    
    /**
     * 金额减法 (空值视为 0)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return 差
     */
    public static BigDecimal subtract(BigDecimal v1, BigDecimal v2) {
        return (v1 == null ? ZERO : v1).subtract(v2 == null ? ZERO : v2);
    }
    
    /**
     * 金额乘法 (空值视为 0)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return 积
     */
    public static BigDecimal multiply(BigDecimal v1, BigDecimal v2) {
        if (v1 == null || v2 == null) {
            return ZERO;
        }
        return v1.multiply(v2).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 金额除法 (空值或除数为 0 返回 0)
     * 
     * @param v1 被除数
     * @param v2 除数
     * @return 商
     */
    public static BigDecimal divide(BigDecimal v1, BigDecimal v2) {
        if (v1 == null || v2 == null || v2.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return v1.divide(v2, 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 金额除法 (自定义精度)
     * 
     * @param v1 被除数
     * @param v2 除数
     * @param scale 精度
     * @return 商
     */
    public static BigDecimal divide(BigDecimal v1, BigDecimal v2, int scale) {
        if (v1 == null || v2 == null || v2.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return v1.divide(v2, scale, RoundingMode.HALF_UP);
    }
    
    /**
     * 计算百分比 (空值或除数为 0 返回 0)
     * 
     * @param part 部分
     * @param total 总体
     * @return 百分比 (0-100 之间)
     */
    public static BigDecimal percentage(BigDecimal part, BigDecimal total) {
        if (part == null || total == null || total.compareTo(ZERO) == 0) {
            return ZERO;
        }
        return part.divide(total, 4, RoundingMode.HALF_UP).multiply(HUNDRED).setScale(2, RoundingMode.HALF_UP);
    }
    
    /**
     * 比较大小 (v1 > v2)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return v1 > v2 返回 true
     */
    public static boolean gt(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) > 0;
    }
    
    /**
     * 比较大小 (v1 >= v2)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return v1 >= v2 返回 true
     */
    public static boolean ge(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) >= 0;
    }
    
    /**
     * 比较大小 (v1 < v2)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return v1 < v2 返回 true
     */
    public static boolean lt(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) < 0;
    }
    
    /**
     * 比较大小 (v1 <= v2)
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return v1 <= v2 返回 true
     */
    public static boolean le(BigDecimal v1, BigDecimal v2) {
        return v1 != null && v2 != null && v1.compareTo(v2) <= 0;
    }
    
    /**
     * 比较相等
     * 
     * @param v1 金额 1
     * @param v2 金额 2
     * @return 相等返回 true
     */
    public static boolean eq(BigDecimal v1, BigDecimal v2) {
        return (v1 == null && v2 == null) || (v1 != null && v2 != null && v1.compareTo(v2) == 0);
    }
    
    /**
     * 判断金额是否为零
     * 
     * @param money 金额
     * @return 为零或 null 返回 true
     */
    public static boolean isZero(BigDecimal money) {
        return money == null || money.compareTo(ZERO) == 0;
    }
    
    /**
     * 判断金额是否为正数
     * 
     * @param money 金额
     * @return 为正数返回 true
     */
    public static boolean isPositive(BigDecimal money) {
        return money != null && money.compareTo(ZERO) > 0;
    }
    
    /**
     * 判断金额是否为负数
     * 
     * @param money 金额
     * @return 为负数返回 true
     */
    public static boolean isNegative(BigDecimal money) {
        return money != null && money.compareTo(ZERO) < 0;
    }
    
    /**
     * 获取最大值
     * 
     * @param values 金额数组
     * @return 最大值
     */
    public static BigDecimal max(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return Arrays.stream(values)
                .filter(v -> v != null)
                .max(BigDecimal::compareTo)
                .orElse(null);
    }
    
    /**
     * 获取最小值
     * 
     * @param values 金额数组
     * @return 最小值
     */
    public static BigDecimal min(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        return Arrays.stream(values)
                .filter(v -> v != null)
                .min(BigDecimal::compareTo)
                .orElse(null);
    }
    
    /**
     * 计算总和
     * 
     * @param values 金额数组
     * @return 总和
     */
    public static BigDecimal sum(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return ZERO;
        }
        return Arrays.stream(values)
                .filter(v -> v != null)
                .reduce(ZERO, BigDecimal::add);
    }
    
    /**
     * 计算平均值
     * 
     * @param values 金额数组
     * @return 平均值
     */
    public static BigDecimal avg(BigDecimal... values) {
        if (values == null || values.length == 0) {
            return null;
        }
        List<BigDecimal> nonNull = Arrays.stream(values)
                .filter(v -> v != null)
                .collect(Collectors.toList());
        if (nonNull.isEmpty()) {
            return null;
        }
        BigDecimal total = nonNull.stream().reduce(ZERO, BigDecimal::add);
        return total.divide(BigDecimal.valueOf(nonNull.size()), 2, RoundingMode.HALF_UP);
    }
    
    /**
     * 金额验证 (是否在有效范围内)
     * 
     * @param money 金额
     * @return 有效返回 true
     */
    public static boolean isValid(BigDecimal money) {
        return money != null && money.compareTo(MIN_MONEY) >= 0 && money.compareTo(MAX_MONEY) <= 0;
    }
    
    /**
     * 金额验证 (是否为正数且在有效范围内)
     * 
     * @param money 金额
     * @return 有效返回 true
     */
    public static boolean isValidPositive(BigDecimal money) {
        return money != null && money.compareTo(ZERO) > 0 && money.compareTo(MAX_MONEY) <= 0;
    }
}
