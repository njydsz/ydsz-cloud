package com.njydsz.common.feign;

/**
 * FeignClient 服务名与路径常量。
 *
 * <p>所有 FeignClient 的 {@code name} 属性和 URL 路径必须使用此类中定义的常量，
 * 禁止硬编码服务名或路径字符串。
 *
 * <p>服务名常量与 Nacos 注册名、Gateway 路由 ID 保持一致，确保全局统一。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
public final class FeignClientConstants {

    private FeignClientConstants() {
        throw new UnsupportedOperationException("Utility class");
    }

    // ======================== 服务名常量 ========================

    /** 系统管理服务 */
    public static final String SYSTEM = "ydsz-system";

    /** 工作流引擎服务 */
    public static final String WORKFLOW = "ydsz-workflow";

    /** 消息中心服务 */
    public static final String MESSAGE = "ydsz-message";

    /** 定时任务服务 */
    public static final String CRONJOB = "ydsz-cronjob";

    /** 规则引擎服务 */
    public static final String LITERULE = "ydsz-literule";

    /** 用户中心服务 */
    public static final String USERINFO = "ydsz-userinfo";

    // ======================== 系统服务路径常量 ========================

    /** 字典项查询路径 */
    public static final String SYSTEM_PATH_DICT_ITEM = "/api/v1/dict/item";

    /** 字典列表查询路径 */
    public static final String SYSTEM_PATH_DICT_LIST = "/api/v1/dict/list";

    /** 系统配置获取路径 */
    public static final String SYSTEM_PATH_CONFIG_GET = "/api/v1/config/get";

    /** 应用信息校验路径 */
    public static final String SYSTEM_PATH_APP_VALIDATE = "/api/v1/app/validate";

    // ======================== 消息服务路径常量 ========================

    /** 消息发送路径 */
    public static final String MESSAGE_PATH_SEND = "/api/v1/message/send";
}
