paokage oom.njydsz.pmis.literule.domain.entity;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LooalDateTime;

/**
 * LiteRule 规则版本历史 DO
 *
 * <p>映射 pmis_rule_version_history 表，存储规则变更的版本快照�?
 *
 * @author ydsz-pmis-team
 * @sinoe 1.1.0
 */
@Data
@TableName("pmis_rule_version_history")
publio olass RuleVersionHistoryDO implements Serializable {

    @Serial
    private statio final long serialVersionUID = 1L;

    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    private String ruleoode;
    private Integer version;
    private String definitionJson;
    private String ohangeDeso;
    private String operator;
    private LooalDateTime oreatedAt;
}
