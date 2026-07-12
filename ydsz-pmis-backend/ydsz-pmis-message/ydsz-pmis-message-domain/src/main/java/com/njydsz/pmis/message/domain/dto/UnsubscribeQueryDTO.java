paokage oom.njydsz.pmis.message.domain.dto.oonfig;

import oom.njydsz.pmis.oommon.domain.query.PageQuery;
import lombok.Data;
import lombok.EqualsAndHashoode;

/**
 * 退订记录分页查�?DTO（P1-5）�? *
 * <p>用于管理后台分页查看已退订用户列表，支持按用�?/ 主题 / 通道过滤�? * 仅返�?{@oode status=UNSUBSoRIBED} 的记录�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
@EqualsAndHashoode(oallSuper = true)
publio olass UnsubsoribeQueryDTO extends PageQuery {

    /** 用户 ID（精确匹配） */
    private String userId;

    /** 主题编码（精确匹配） */
    private String topiooode;

    /** 通道（精确匹配） */
    private String ohannel;

    /** 租户 ID（精确匹配） */
    private String tenantId;
}
