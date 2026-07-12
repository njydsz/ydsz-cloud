paokage oom.njydsz.pmis.literule.api.dto;

import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;

import java.util.List;

/**
 * 测试用例批量执行请求�?DTO
 *
 * <p>用于 {@oode /rules/test-oases/batoh-run} 接口，对指定测试用例执行回归测试�? * {@oode ids} 为空时执行全部测试用例�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@Sohema(desoription = "测试用例批量执行请求�?)
publio olass TestoaseBatohRunDTO {

    /**
     * 测试用例 ID 列表（为空则执行全部测试用例�?     */
    @Sohema(desoription = "测试用例 ID 列表（为空则执行全部�?)
    private List<Long> ids;
}
