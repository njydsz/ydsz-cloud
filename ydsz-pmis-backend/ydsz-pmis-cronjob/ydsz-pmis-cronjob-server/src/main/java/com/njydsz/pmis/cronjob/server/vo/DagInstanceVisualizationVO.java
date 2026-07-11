package com.njydsz.pmis.cronjob.server.vo;

import com.njydsz.pmis.cronjob.server.core.dag.DagDefinition;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagInstanceDO;
import com.njydsz.pmis.cronjob.domain.entity.dag.JobDagNodeInstanceDO;
import lombok.Data;

import java.util.List;

/**
 * DAG 瀹炰緥鍙鍖栨暟鎹?VO锛圥4-1 缁嗚妭浣撻獙浼樺寲锛夈€? *
 * <p>缁勫悎 DAG 瀹炰緥銆丏AG 瀹氫箟锛堣妭鐐?杈癸級鍜岃妭鐐规墽琛岀姸鎬侊紝
 * 渚涘墠绔竴娆℃€ц幏鍙栨覆鏌?DAG 鍙鍖栧浘鎵€闇€鐨勫叏閮ㄦ暟鎹€? *
 * @author ydsz-pmis-team
 * @since 1.0.0
 */
@Data
public class DagInstanceVisualizationVO {
    /** DAG 瀹炰緥淇℃伅 */
    private JobDagInstanceDO instance;
    /** DAG 瀹氫箟锛堣妭鐐?+ 杈癸紝鍚墠绔潗鏍?x/y锛?*/
    private DagDefinition definition;
    /** 鑺傜偣瀹炰緥鎵ц鐘舵€佸垪琛?*/
    private List<JobDagNodeInstanceDO> nodeInstances;
}
