package com.njydsz.pmis.common.docs.enums;

/**
 * PII（个人身份信息）类型枚举
 * <p>
 * 定义系统中可识别的敏感信息类型。
 *
 * @author ydsz-pmis-team
 * @since 1.0.0
 * 
 * @since 1.3.0
 */
public enum PiiType {

    /** 身份证号 */
    ID_CARD("身份证号"),
    /** 手机号 */
    PHONE("手机号"),
    /** 银行卡号 */
    BANK_CARD("银行卡号"),
    /** 邮箱地址 */
    EMAIL("邮箱地址"),
    /** API 密钥/Token */
    API_KEY("API密钥"),
    /** IP 地址 */
    IP_ADDRESS("IP地址"),
    /** 护照号码 */
    PASSPORT("护照号码");

    /** 类型描述 */
    private final String description;

    PiiType(String description) {
        this.description = description;
    }

    public String getDescription() {
        return description;
    }
}
