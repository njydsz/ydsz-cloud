package com.njydsz.pmis.common.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.util.Currency;
import java.util.Locale;

/**
 * 金额工具类
 *
 * @author ydsz-pmis-team
 * @since 2.0.0
 */
public final class MoneyUtils {

    private MoneyUtils() {
    }

    /** 金额默认精度 */
    private static final int MONEY_SCALE = 2;

    /**
     * 格式化金额（2位小数）
     *
     * @param amount 金额
     * @return 格式化后的金额字符串
     */
    public static String format(BigDecimal amount) {
        return format(amount, MONEY_SCALE);
    }

    /**
     * 格式化金额
     *
     * @param amount 金额
     * @param scale  小数位
     * @return 格式化后的金额字符串
     */
    public static String format(BigDecimal amount, int scale) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        DecimalFormat df = new DecimalFormat();
        df.setMinimumFractionDigits(scale);
        df.setMaximumFractionDigits(scale);
        df.setGroupingUsed(false);
        return df.format(amount);
    }

    /**
     * 转换为分
     *
     * @param yuan 金额（元）
     * @return 金额（分）
     */
    public static long yuanToFen(BigDecimal yuan) {
        if (yuan == null) {
            return 0;
        }
        return yuan.multiply(new BigDecimal("100")).longValue();
    }

    /**
     * 转换为元
     *
     * @param fen 金额（分）
     * @return 金额（元）
     */
    public static BigDecimal fenToYuan(long fen) {
        return new BigDecimal(fen).divide(new BigDecimal("100"), MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 大写金额转换
     *
     * @param amount 金额
     * @return 中文大写金额
     */
    public static String toChinese(BigDecimal amount) {
        if (amount == null) {
            amount = BigDecimal.ZERO;
        }
        amount = amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
        String str = amount.toPlainString();
        String[] parts = str.split("\\.");
        String integerPart = parts[0];
        String decimalPart = parts.length > 1 ? parts[1] : "00";

        String[] digits = {"零", "壹", "贰", "叁", "肆", "伍", "陆", "柒", "捌", "玖"};
        String[] units = {"", "拾", "佰", "仟", "万", "拾", "佰", "仟", "亿", "拾", "佰", "仟"};

        StringBuilder result = new StringBuilder();
        boolean zeroFlag = true;

        for (int i = 0; i < integerPart.length(); i++) {
            char c = integerPart.charAt(i);
            int digit = c - '0';
            int pos = integerPart.length() - i - 1;

            if (digit != 0) {
                result.append(digits[digit]);
                result.append(units[pos]);
                zeroFlag = false;
            } else {
                if (!zeroFlag) {
                    result.append("零");
                    zeroFlag = true;
                }
                if (pos == 4 || pos == 8) {
                    if (result.length() > 0 && !result.toString().endsWith("零")) {
                        result.append(units[pos]);
                    }
                }
            }
        }

        if (result.length() == 0) {
            result.append("零");
        }
        result.append("元");

        int jiao = decimalPart.charAt(0) - '0';
        int fen = decimalPart.charAt(1) - '0';

        if (jiao == 0 && fen == 0) {
            result.append("整");
        } else {
            if (jiao != 0) {
                result.append(digits[jiao]).append("角");
            } else if (fen != 0) {
                result.append("零");
            }
            if (fen != 0) {
                result.append(digits[fen]).append("分");
            }
        }

        return result.toString();
    }

    /**
     * 加法
     */
    public static BigDecimal add(BigDecimal a, BigDecimal b) {
        return BigDecimalUtils.add(a, b);
    }

    /**
     * 减法
     */
    public static BigDecimal subtract(BigDecimal a, BigDecimal b) {
        return BigDecimalUtils.subtract(a, b);
    }

    /**
     * 乘法
     */
    public static BigDecimal multiply(BigDecimal a, BigDecimal b) {
        return BigDecimalUtils.multiply(a, b).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 除法
     */
    public static BigDecimal divide(BigDecimal a, BigDecimal b) {
        return BigDecimalUtils.divide(a, b, MONEY_SCALE, RoundingMode.HALF_UP);
    }

    /**
     * 货币格式化
     */
    public static String currencyFormat(BigDecimal amount, Locale locale) {
        NumberFormat currencyFormat = NumberFormat.getCurrencyInstance(locale);
        return currencyFormat.format(amount != null ? amount : BigDecimal.ZERO);
    }
}
