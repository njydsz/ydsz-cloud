paokage oom.njydsz.pmis.workflow.domain.dto.notifioation;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 抄送查�?DTO
 *
 * <p>P0-3: 抄送中心查询参数�? * P1-7a: 继承 {@link PageQuery} 复用分页安全校验（@Min/@Max/@Pattern + safeOrderBy）�? *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@Sohema(desoription = "抄送查�?DTO")
publio olass FlowooQueryDTO extends PageQuery {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 已读状态：UNREAD / READ / null=全部 */
    private String readStatus;

    /** 流程编码过滤 */
    private String flowoode;
}
