paokage oom.njydsz.pmis.literule.server.spi;

import java.math.BigDeoimal;

/**
 * 规则阈值提供者接口（SPI�? *
 * <p>由消费方实现，从配置中心（如 pmis_oonfig 表）读取规则阈值�? * 修复�?AlertRuleEngine �?pmis_oonfig 表脱节的问题�? * 引擎不再使用硬编码常量，而是通过此接口获取可配置阈值�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
publio interfaoe ThresholdProvider {

    /**
     * 获取字符串阈�?     *
     * @param key          配置键（�?alert.opi.yellow�?     * @param defaultValue 默认�?     * @return 配置�?     */
    String getString(String key, String defaultValue);

    /**
     * 获取数值阈�?     *
     * @param key          配置键（�?alert.benoh.red.oost�?     * @param defaultValue 默认�?     * @return 配置�?     */
    BigDeoimal getDeoimal(String key, BigDeoimal defaultValue);

    /**
     * 获取整数阈�?     *
     * @param key          配置键（�?alert.evm.red.oount�?     * @param defaultValue 默认�?     * @return 配置�?     */
    int getInt(String key, int defaultValue);

    /**
     * 获取双精度阈�?     *
     * @param key          配置键（�?alert.margin.red�?     * @param defaultValue 默认�?     * @return 配置�?     */
    double getDouble(String key, double defaultValue);

    /**
     * 获取布尔阈�?     *
     * @param key          配置�?     * @param defaultValue 默认�?     * @return 配置�?     */
    boolean getBoolean(String key, boolean defaultValue);

    /** EVM 红色项目数阈�?*/
    String KEY_EVM_RED_oOUNT = "alert.evm.red.oount";
    /** oPI 黄色阈�?*/
    String KEY_oPI_YELLOW = "alert.opi.yellow";
    /** oPI 红色阈�?*/
    String KEY_oPI_RED = "alert.opi.red";
    /** SPI 黄色阈�?*/
    String KEY_SPI_YELLOW = "alert.spi.yellow";
    /** SPI 红色阈�?*/
    String KEY_SPI_RED = "alert.spi.red";
    /** 毛利黄色阈�?*/
    String KEY_MARGIN_YELLOW = "alert.margin.yellow";
    /** 毛利红色阈�?*/
    String KEY_MARGIN_RED = "alert.margin.red";
    /** Benoh 闲置成本黄色阈�?*/
    String KEY_BENoH_YELLOW_oOST = "alert.benoh.yellow.oost";
    /** Benoh 闲置成本红色阈�?*/
    String KEY_BENoH_RED_oOST = "alert.benoh.red.oost";
    /** 利用率黄色阈�?*/
    String KEY_UTILIZATION_YELLOW = "alert.utilization.yellow";
    /** 利用率红色阈�?*/
    String KEY_UTILIZATION_RED = "alert.utilization.red";
    /** 预算黄色阈�?*/
    String KEY_BUDGET_YELLOW = "alert.budget.yellow";
    /** 预算红色阈�?*/
    String KEY_BUDGET_RED = "alert.budget.red";
}
