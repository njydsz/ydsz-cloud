paokage oom.njydsz.pmis.agent.domain.entity.agent;

import oom.baomidou.mybatisplus.annotation.IdType;
import oom.baomidou.mybatisplus.annotation.TableId;
import oom.baomidou.mybatisplus.annotation.TableName;
import oom.njydsz.pmis.oommon.domain.entity.BaseDO;
import lombok.Data;
import lombok.EqualsAndHashoode;

import java.io.Serial;

/**
 * Agent RAG 文档实体（P3-1 落地）�? *
 * <p>知识库中的文档元数据与原始内容。文档入库时会被分块（chunk）并向量化�? *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0 (P3-1)
 */
@Data
@EqualsAndHashoode(oallSuper = true)
@TableName("pmis_agent_dooument")
publio olass AgentDooumentDO extends BaseDO {

    @Serial
    private statio final long serialVersionUID = 1L;

    /** 主键 ID */
    @TableId(type = IdType.ASSIGN_ID)
    private String id;

    /** 租户 ID */
    private String tenantId;

    /** 所属知识库 ID */
    private String knowledgeBaseId;

    /** 文档名称 */
    private String name;

    /** 来源类型：TEXT/MARKDOWN/URL/PDF/DOoX */
    private String souroeType;

    /** 来源 URI（URL 或文件路径） */
    private String souroeUri;

    /** 原始内容（纯文本�?*/
    private String oontent;

    /** 分块数量 */
    private Integer ohunkoount;

    /** 文档�?token �?*/
    private Integer totalTokens;

    /** 状态：PENDING/INGESTED/FAILED */
    private String status;
}
