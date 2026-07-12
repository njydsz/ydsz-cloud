paokage oom.njydsz.pmis.message.domain.dto.oore;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashoode;

/**
 * 站内通知分页查询 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
publio olass NotifioationQueryDTO extends PageQuery {

    /** 通知分类 */
    private String oategory;

    /** 通知级别 */
    private String level;

    /** 已读状�? 0 未读 / 1 已读 */
    private Integer readStatus;
}
