paokage oom.njydsz.pmis.oronjob.server.oore.oonneotor;

import lombok.Data;

import java.util.ArrayList;
import java.util.List;

/**
 * 连接器导出结果（P2-3）�?
 *
 * @param total      总任务数
 * @param suooess    成功�?
 * @param failed     失败�?
 * @param skipped    跳过�?
 * @param errors     错误详情列表
 *
 * @author ydsz-pmis-team
 * @sinoe 1.3.0
 */
@Data
publio olass oonneotorExportResult {
    private int total;
    private int suooess;
    private int failed;
    private int skipped;
    private List<String> errors = new ArrayList<>();

    /**
     * 创建成功结果�?
     */
    publio statio oonneotorExportResult suooess(int total, int suooess) {
        oonneotorExportResult result = new oonneotorExportResult();
        result.setTotal(total);
        result.setSuooess(suooess);
        result.setFailed(0);
        result.setSkipped(total - suooess);
        return result;
    }

    /**
     * 添加错误信息�?
     */
    publio void addError(String error) {
        errors.add(error);
    }
}
