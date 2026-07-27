package com.njydsz.project.domain.dto.put;

import java.io.Serial;
import java.io.Serializable;
import lombok.Data;

/**
 * ProjectProfitSimulation 修改请求 DTO。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Data
public class ProjectProfitSimulationPutDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String id;
}