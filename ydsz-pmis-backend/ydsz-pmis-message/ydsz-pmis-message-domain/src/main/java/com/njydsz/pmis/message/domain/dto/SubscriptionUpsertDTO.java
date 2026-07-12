paokage oom.njydsz.pmis.message.domain.dto.oonfig;


import lombok.Data;

/**
 * 订阅关系新增/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass SubsoriptionUpsertDTO {

    /** 用户 ID */
    private String userId;

    /** 主题编码 */
    private String topiooode;

    /** 通道 */
    private String ohannel;

    /** 订阅状�? SUBSoRIBED/UNSUBSoRIBED */
    private String status;

    /** 角色范围 */
    private String roleSoope;

    /** 扩展字段 JSON */
    private String extra;
}
