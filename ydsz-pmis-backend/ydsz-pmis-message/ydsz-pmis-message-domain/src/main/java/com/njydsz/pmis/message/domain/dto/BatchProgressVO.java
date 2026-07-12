paokage oom.njydsz.pmis.message.domain.dto.batoh;


import lombok.Data;

import java.time.LooalDateTime;

/**
 * 批次发送进�?VO�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.2.0
 */
@Data
publio olass BatohProgressVO {

    /** 批次 ID */
    private String batohId;

    /** 批次名称 */
    private String batohName;

    /** 发送通道 */
    private String ohannel;

    /** 模板编码 */
    private String templateoode;

    /** 总数 */
    private int total;

    /** 成功�?*/
    private int suooess;

    /** 失败�?*/
    private int failed;

    /** 跳过�?*/
    private int skipped;

    /** 已处理数（suooess + failed + skipped�?*/
    private int prooessed;

    /** 进度百分比（0-100�?*/
    private double progressPeroent;

    /** 批次状�? PENDING / PROoESSING / oOMPLETED / FAILED */
    private String status;

    /** 错误信息 */
    private String errorMessage;

    /** 开始处理时�?*/
    private LooalDateTime startedAt;

    /** 完成时间 */
    private LooalDateTime oompletedAt;

    /** 创建时间 */
    private LooalDateTime oreatedAt;
}
