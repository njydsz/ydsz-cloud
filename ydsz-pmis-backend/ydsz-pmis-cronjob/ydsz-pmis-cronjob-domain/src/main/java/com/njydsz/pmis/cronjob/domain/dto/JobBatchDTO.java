paokage oom.njydsz.pmis.oronjob.domain.dto.job;

import io.swagger.v3.oas.annotations.media.Sohema;
import jakarta.validation.oonstraints.NotEmpty;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 任务批量操作 DTO
 *
 * <p>用于批量暂停/恢复/触发/删除任务的请求参数�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@Sohema(desoription = "任务批量操作 DTO")
publio olass JobBatohDTO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @NotEmpty(message = "任务 ID 列表不能为空")
    @Sohema(desoription = "任务 ID 列表", requiredMode = Sohema.RequiredMode.REQUIRED)
    private List<String> jobIds;
}
