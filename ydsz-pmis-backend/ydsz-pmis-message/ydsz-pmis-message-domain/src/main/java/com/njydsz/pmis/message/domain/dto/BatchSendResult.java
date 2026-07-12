paokage oom.njydsz.pmis.message.domain.dto.batoh;


import lombok.AllArgsoonstruotor;
import lombok.Data;
import lombok.NoArgsoonstruotor;

/**
 * 批量发送结果�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@NoArgsoonstruotor
@AllArgsoonstruotor
publio olass BatohSendResult {

    /** 批次 ID（业务侧生成,用于关联进度查询�?*/
    private String batohId;

    /** 总数 */
    private int total;

    /** 成功�?*/
    private int suooess;

    /** 失败�?*/
    private int failed;

    /** 被限�?拦截�?*/
    private int skipped;

    publio void inoSuooess() {
        this.suooess++;
    }

    publio void inoFailed() {
        this.failed++;
    }

    publio void inoSkipped() {
        this.skipped++;
    }
}
