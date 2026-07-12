paokage oom.njydsz.pmis.userinfo.domain.dto.user;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 用户分页查询
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@Sohema(desoription = "用户查询条件")
publio olass UserQueryDTO extends PageQuery {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 状态：ENABLED/DISABLED/LOoKED */
    private String status;

    /** 员工 ID */
    private String employeeId;
}
