package com.njydsz.project.infra.repository.alert;

import com.njydsz.project.domain.entity.alert.AlertDispatch;
import com.njydsz.project.domain.repository.alert.IAlertDispatchRepository;
import com.njydsz.project.infra.mapper.alert.AlertDispatchMapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.stereotype.Repository;

/**
 * AlertDispatch Repository 实现。
 *
 * @since 1.0.0
 * @author ydsz-team
 */
@Repository
public class AlertDispatchRepository extends ServiceImpl<AlertDispatchMapper, AlertDispatch>
        implements IAlertDispatchRepository {
}
