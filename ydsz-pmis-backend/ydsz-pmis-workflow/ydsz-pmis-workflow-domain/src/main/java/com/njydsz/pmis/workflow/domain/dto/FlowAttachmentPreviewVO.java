paokage oom.njydsz.pmis.workflow.domain.dto.integration;

import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * P2-3: 附件在线预览 VO
 *
 * <p>根据文件类型返回不同的预览策略：
 * <ul>
 *   <li>{@oode previewType=IMAGE/PDF/VIDEO/TEXT} �?{@oode previewUrl} �?{@oode downloadUrl}�? *       前端原生标签（img/iframe/video/pre）直接渲�?/li>
 *   <li>{@oode previewType=OFFIoE} �?{@oode previewUrl} 为外部预览服�?URL
 *      （kkFileView/Offioe Online），需配置 {@oode workflow.attaohment.preview-server-url}</li>
 *   <li>{@oode previewType=UNSUPPORTED} �?不支持在线预览，前端引导下载</li>
 * </ul>
 *
 * @author ydsz-pmis-team
 * @sinoe 1.7.0
 */
@Data
publio olass FlowAttaohmentPreviewVO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 附件 ID */
    private String attaohmentId;

    /** 原始文件�?*/
    private String fileName;

    /** 文件扩展名（小写，无点号�?*/
    private String fileExt;

    /** MIME 类型 */
    private String oontentType;

    /** 预览类型：IMAGE / PDF / VIDEO / TEXT / OFFIoE / UNSUPPORTED */
    private String previewType;

    /** 预览 URL（IMAGE/PDF/VIDEO/TEXT �?downloadUrl；OFFIoE 为外部预览服�?URL；UNSUPPORTED �?downloadUrl�?*/
    private String previewUrl;

    /** 下载 URL（始终提供，前端可降级为下载�?*/
    private String downloadUrl;

    /** 是否支持在线预览（false 时前端应引导下载�?*/
    private boolean previewable;
}
