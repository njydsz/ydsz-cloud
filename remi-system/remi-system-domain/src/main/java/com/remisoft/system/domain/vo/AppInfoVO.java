package com.remisoft.system.domain.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * 应用注册 VO
 *
 * <p>对应 {@code remi_app_info} 表的展示视图，是「应用注册中心」列表 / 详情接口的返回值类型。
 * 由 {@link com.remisoft.system.domain.converter.SystemConverter} 从 {@link com.remisoft.system.domain.entity.AppInfo} 实体转换而来。
 *
 * <p><b>安全约束（关键）：</b>
 * <ul>
 *   <li><b>不暴露</b> {@code appSecret} 字段，BCrypt 哈希<b>永远</b>不出现在 VO 中，
 *       即便数据库被拖库也不可逆</li>
 *   <li>管理后台「查看密钥」入口走单独的 {@code /app/{id}/secret} 接口，
 *       该接口需 {@code remi:app:secret:view} 权限码 + 二次密码确认 + 操作审计</li>
 *   <li>列表接口在 {@code RemiJson} 序列化层强制忽略 {@code appSecret} 字段
 *       （如未来误加 {@code @com.remisoft.common.json.annotation.JsonProperty}，需通过单元测试拦截）</li>
 * </ul>
 *
 * <p><b>使用场景：</b>
 * <ul>
 *   <li>应用管理列表 / 详情 / 编辑回显</li>
 *   <li>OAuth 2.0 客户端注册信息展示</li>
 *   <li>第三方应用对接时的「应用信息」展示</li>
 * </ul>
 *
 * @author remi-team
 * @since 1.0.0
 *
 * @see com.remisoft.system.domain.entity.AppInfo 应用注册实体
 * @see com.remisoft.system.domain.dto.AppInfoDTO 应用注册 DTO
 * @see com.remisoft.system.domain.converter.SystemConverter 实体 / DTO / VO 转换器
 */
@Data
@Schema(description = "应用注册视图对象")
public class AppInfoVO {

    @Schema(description = "主键 ID")
    private String id;

    @Schema(description = "应用编码")
    private String appCode;

    @Schema(description = "应用名称")
    private String appName;

    @Schema(description = "应用 Key（client_id）")
    private String appKey;

    @Schema(description = "授权回调地址")
    private String redirectUrl;

    @Schema(description = "应用描述")
    private String description;

    @Schema(description = "启用状态: ENABLED/DISABLED")
    private String status;
}
