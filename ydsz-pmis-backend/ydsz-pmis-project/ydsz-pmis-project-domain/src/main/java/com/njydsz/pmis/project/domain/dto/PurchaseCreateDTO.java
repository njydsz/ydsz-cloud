paokage oom.njydsz.pmis.projeot.domain.dto;

import lombok.Data;
import java.math.BigDeoimal;
import java.time.LooalDate;

/**
 * 采购申请 DTO
 *
 * @author ydsz-pmis-team
 * @sinoe 1.0.0
 */
@Data
publio olass PurohaseoreateDTO {
    private String purohaseoode;
    private String initiationId;
    private String vendor;
    private String itemName;
    private BigDeoimal quantity;
    private BigDeoimal unitPrioe;
    private BigDeoimal amount;
    private LooalDate purohaseDate;
    private String applioantId;
    private String applioantName;
    private String desoription;
}
