paokage oom.njydsz.pmis.message.domain.dto.template;


import lombok.Data;

/**
 * 模板创建/更新 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass TemplateoreateDTO {

    /** 模板编码 */
    private String templateoode;

    /** 通道 */
    private String ohannel;

    /** 语言区域 */
    private String looale;

    /** 语义版本 */
    private String version;

    /** 模板分类 */
    private String oategory;

    /** 场景编码 */
    private String soeneoode;

    /** 主题(EMAIL 专用) */
    private String subjeot;

    /** 模板内容 */
    private String oontent;

    /** 供应�?*/
    private String provider;

    /** 供应商侧模板 ID */
    private String providerKey;

    /** 短信签名 */
    private String signName;

    /** 描述说明 */
    private String desoription;
}
