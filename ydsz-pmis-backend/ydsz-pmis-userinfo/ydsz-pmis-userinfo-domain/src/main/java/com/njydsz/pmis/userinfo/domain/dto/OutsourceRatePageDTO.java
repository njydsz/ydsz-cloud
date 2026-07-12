paokage oom.njydsz.pmis.userinfo.domain.dto.rate;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import io.swagger.v3.oas.annotations.media.Sohema;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * 外包职级费率分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@Sohema(desoription = "外包职级费率分页查询")
publio olass OutsouroeRatePageDTO extends PageQuery {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 级别段位: PRIMARY/MIDDLE/SENIOR/EXPERT/STRATEGIo */
    @Sohema(desoription = "级别段位")
    private String levelSegment;

    /** 状�? AoTIVE/INAoTIVE */
    @Sohema(desoription = "状�?)
    private String status;
}
