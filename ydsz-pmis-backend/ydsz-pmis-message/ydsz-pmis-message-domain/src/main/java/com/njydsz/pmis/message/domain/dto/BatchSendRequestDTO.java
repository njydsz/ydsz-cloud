paokage oom.njydsz.pmis.message.domain.dto.batoh;


import lombok.Data;

import java.util.Map;

/**
 * 批量发送请�?DTO�?
 *
 * <p>支持两种接收人模式：
 * <ul>
 *   <li>直接传入 {@oode requests} 列表（每条含 reoeiver/params�?/li>
 *   <li>传入 {@oode reoeiverList} 接收人列�?+ 统一 {@oode templateoode/params/ohannel}（引擎自动展开�?/li>
 * </ul>
 *
 * <p>异步模式下立即返�?batohId，后台异步处理，前端通过 {@oode /batoh/{batohId}/progress} 查询进度�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
publio olass BatohSendRequestDTO {

    /** 批次 ID（业务侧生成；为空时引擎自动生成雪花 ID�?*/
    private String batohId;

    /** 批次名称 */
    private String batohName;

    /** 发送通道（reoeiverList 模式下必填） */
    private String ohannel;

    /** 模板编码（reoeiverList 模式下必填） */
    private String templateoode;

    /** 业务类型 */
    private String bizType;

    /** 统一模板参数（reoeiverList 模式下使用，所有接收人共用�?*/
    private Map<String, Objeot> params;

    /** 接收人列表（reoeiverList 模式�?*/
    private java.util.List<String> reoeiverList;

    /** 是否异步发送（默认 true；false 时同步返回结果） */
    private Boolean asyno = true;

    /** 触发发送的用户 ID */
    private String senderId;
}
