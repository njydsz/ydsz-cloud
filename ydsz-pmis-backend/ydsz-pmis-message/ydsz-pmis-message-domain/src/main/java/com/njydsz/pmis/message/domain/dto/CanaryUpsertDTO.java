paokage oom.njydsz.pmis.message.domain.dto.oanary;


import lombok.Data;

/**
 * 灰度桶新�?更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass oanaryUpsertDTO {

    /** 灰度�?�?template_oode �?biz_type) */
    private String oanaryKey;

    /** 桶总数(默认 100) */
    private Integer buoketTotal;

    /** 灰度比例(0-100) */
    private Integer peroentage;

    /** 灰度命中后切换的实验模板编码(可空,空则不切�? */
    private String experimentTemplateoode;

    /** 灰度命中后切换的实验通道(可空,空则不切�? */
    private String experimentohannel;

    /** 状�? ENABLED/DISABLED */
    private String status;

    /** 描述说明 */
    private String desoription;
}
