package com.njydsz.project.domain.converter;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.factory.Mappers;

import com.njydsz.project.domain.entity.alert.AlertDispatch;
import com.njydsz.project.domain.entity.billable.BillableUtilizationSnapshot;
import com.njydsz.project.domain.entity.cost.CostAllocation;
import com.njydsz.project.domain.entity.cost.CostPurchase;
import com.njydsz.project.domain.entity.evm.EvmMeasure;
import com.njydsz.project.domain.entity.execution.ExecutionClosure;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryItem;
import com.njydsz.project.domain.entity.execution.ExecutionDeliveryStandard;
import com.njydsz.project.domain.entity.execution.ExecutionRisk;
import com.njydsz.project.domain.entity.execution.ExecutionTimeEntry;
import com.njydsz.project.domain.entity.execution.ExecutionWbsTask;
import com.njydsz.project.domain.entity.ops.OpsTicket;
import com.njydsz.project.domain.entity.project.ProjectBudgetItem;
import com.njydsz.project.domain.entity.project.ProjectChange;
import com.njydsz.project.domain.entity.project.ProjectContract;
import com.njydsz.project.domain.entity.project.ProjectContractChange;
import com.njydsz.project.domain.entity.project.ProjectContractSupplement;
import com.njydsz.project.domain.entity.project.ProjectContractTemplate;
import com.njydsz.project.domain.entity.project.ProjectCustomerCredit;
import com.njydsz.project.domain.entity.project.ProjectExpense;
import com.njydsz.project.domain.entity.project.ProjectGateReview;
import com.njydsz.project.domain.entity.project.ProjectInitiation;
import com.njydsz.project.domain.entity.project.ProjectInvoice;
import com.njydsz.project.domain.entity.project.ProjectOpportunity;
import com.njydsz.project.domain.entity.project.ProjectOpportunityFollow;
import com.njydsz.project.domain.entity.project.ProjectPayment;
import com.njydsz.project.domain.entity.project.ProjectProfitSimulation;
import com.njydsz.project.domain.entity.project.ProjectProfitSnapshot;
import com.njydsz.project.domain.entity.project.ProjectReconcileDaily;
import com.njydsz.project.domain.entity.project.ProjectRevenue;
import com.njydsz.project.domain.entity.rate.RateCard;
import com.njydsz.project.domain.entity.rate.RateInternal;
import com.njydsz.project.domain.entity.satisfaction.Satisfaction;
import com.njydsz.project.domain.entity.warranty.Warranty;
import com.njydsz.project.domain.vo.AlertDispatchVO;
import com.njydsz.project.domain.vo.BillableUtilizationSnapshotVO;
import com.njydsz.project.domain.vo.CostAllocationVO;
import com.njydsz.project.domain.vo.CostPurchaseVO;
import com.njydsz.project.domain.vo.EvmMeasureVO;
import com.njydsz.project.domain.vo.ExecutionClosureVO;
import com.njydsz.project.domain.vo.ExecutionDeliveryItemVO;
import com.njydsz.project.domain.vo.ExecutionDeliveryStandardVO;
import com.njydsz.project.domain.vo.ExecutionRiskVO;
import com.njydsz.project.domain.vo.ExecutionTimeEntryVO;
import com.njydsz.project.domain.vo.ExecutionWbsTaskVO;
import com.njydsz.project.domain.vo.OpsTicketVO;
import com.njydsz.project.domain.vo.ProjectBudgetItemVO;
import com.njydsz.project.domain.vo.ProjectChangeVO;
import com.njydsz.project.domain.vo.ProjectContractVO;
import com.njydsz.project.domain.vo.ProjectContractChangeVO;
import com.njydsz.project.domain.vo.ProjectContractSupplementVO;
import com.njydsz.project.domain.vo.ProjectContractTemplateVO;
import com.njydsz.project.domain.vo.ProjectCustomerCreditVO;
import com.njydsz.project.domain.vo.ProjectExpenseVO;
import com.njydsz.project.domain.vo.ProjectGateReviewVO;
import com.njydsz.project.domain.vo.ProjectInitiationVO;
import com.njydsz.project.domain.vo.ProjectInvoiceVO;
import com.njydsz.project.domain.vo.ProjectOpportunityVO;
import com.njydsz.project.domain.vo.ProjectOpportunityFollowVO;
import com.njydsz.project.domain.vo.ProjectPaymentVO;
import com.njydsz.project.domain.vo.ProjectProfitSimulationVO;
import com.njydsz.project.domain.vo.ProjectProfitSnapshotVO;
import com.njydsz.project.domain.vo.ProjectReconcileDailyVO;
import com.njydsz.project.domain.vo.ProjectRevenueVO;
import com.njydsz.project.domain.vo.RateCardVO;
import com.njydsz.project.domain.vo.RateInternalVO;
import com.njydsz.project.domain.vo.SatisfactionVO;
import com.njydsz.project.domain.vo.WarrantyVO;

/**
 * project 模块统一 MapStruct 转换器。
 *
 * @author ydsz-team
 * @since 1.0.0
 */
@Mapper
public interface ProjectConverter {

    ProjectConverter INSTANT = Mappers.getMapper(ProjectConverter.class);

    // ===== AlertDispatch =====
    AlertDispatchVO entityToVO(AlertDispatch entity);
    List<AlertDispatchVO> alertDispatchListToVO(List<AlertDispatch> entities);

    // ===== BillableUtilizationSnapshot =====
    BillableUtilizationSnapshotVO entityToVO(BillableUtilizationSnapshot entity);
    List<BillableUtilizationSnapshotVO> billableUtilizationSnapshotListToVO(List<BillableUtilizationSnapshot> entities);

    // ===== CostAllocation =====
    CostAllocationVO entityToVO(CostAllocation entity);
    List<CostAllocationVO> costAllocationListToVO(List<CostAllocation> entities);

    // ===== CostPurchase =====
    CostPurchaseVO entityToVO(CostPurchase entity);
    List<CostPurchaseVO> costPurchaseListToVO(List<CostPurchase> entities);

    // ===== EvmMeasure =====
    EvmMeasureVO entityToVO(EvmMeasure entity);
    List<EvmMeasureVO> evmMeasureListToVO(List<EvmMeasure> entities);

    // ===== ExecutionClosure =====
    ExecutionClosureVO entityToVO(ExecutionClosure entity);
    List<ExecutionClosureVO> executionClosureListToVO(List<ExecutionClosure> entities);

    // ===== ExecutionDeliveryItem =====
    ExecutionDeliveryItemVO entityToVO(ExecutionDeliveryItem entity);
    List<ExecutionDeliveryItemVO> executionDeliveryItemListToVO(List<ExecutionDeliveryItem> entities);

    // ===== ExecutionDeliveryStandard =====
    ExecutionDeliveryStandardVO entityToVO(ExecutionDeliveryStandard entity);
    List<ExecutionDeliveryStandardVO> executionDeliveryStandardListToVO(List<ExecutionDeliveryStandard> entities);

    // ===== ExecutionRisk =====
    ExecutionRiskVO entityToVO(ExecutionRisk entity);
    List<ExecutionRiskVO> executionRiskListToVO(List<ExecutionRisk> entities);

    // ===== ExecutionTimeEntry =====
    ExecutionTimeEntryVO entityToVO(ExecutionTimeEntry entity);
    List<ExecutionTimeEntryVO> executionTimeEntryListToVO(List<ExecutionTimeEntry> entities);

    // ===== ExecutionWbsTask =====
    ExecutionWbsTaskVO entityToVO(ExecutionWbsTask entity);
    List<ExecutionWbsTaskVO> executionWbsTaskListToVO(List<ExecutionWbsTask> entities);

    // ===== OpsTicket =====
    OpsTicketVO entityToVO(OpsTicket entity);
    List<OpsTicketVO> opsTicketListToVO(List<OpsTicket> entities);

    // ===== ProjectBudgetItem =====
    ProjectBudgetItemVO entityToVO(ProjectBudgetItem entity);
    List<ProjectBudgetItemVO> projectBudgetItemListToVO(List<ProjectBudgetItem> entities);

    // ===== ProjectChange =====
    ProjectChangeVO entityToVO(ProjectChange entity);
    List<ProjectChangeVO> projectChangeListToVO(List<ProjectChange> entities);

    // ===== ProjectContract =====
    ProjectContractVO entityToVO(ProjectContract entity);
    List<ProjectContractVO> projectContractListToVO(List<ProjectContract> entities);

    // ===== ProjectContractChange =====
    ProjectContractChangeVO entityToVO(ProjectContractChange entity);
    List<ProjectContractChangeVO> projectContractChangeListToVO(List<ProjectContractChange> entities);

    // ===== ProjectContractSupplement =====
    ProjectContractSupplementVO entityToVO(ProjectContractSupplement entity);
    List<ProjectContractSupplementVO> projectContractSupplementListToVO(List<ProjectContractSupplement> entities);

    // ===== ProjectContractTemplate =====
    ProjectContractTemplateVO entityToVO(ProjectContractTemplate entity);
    List<ProjectContractTemplateVO> projectContractTemplateListToVO(List<ProjectContractTemplate> entities);

    // ===== ProjectCustomerCredit =====
    ProjectCustomerCreditVO entityToVO(ProjectCustomerCredit entity);
    List<ProjectCustomerCreditVO> projectCustomerCreditListToVO(List<ProjectCustomerCredit> entities);

    // ===== ProjectExpense =====
    ProjectExpenseVO entityToVO(ProjectExpense entity);
    List<ProjectExpenseVO> projectExpenseListToVO(List<ProjectExpense> entities);

    // ===== ProjectGateReview =====
    ProjectGateReviewVO entityToVO(ProjectGateReview entity);
    List<ProjectGateReviewVO> projectGateReviewListToVO(List<ProjectGateReview> entities);

    // ===== ProjectInitiation =====
    ProjectInitiationVO entityToVO(ProjectInitiation entity);
    List<ProjectInitiationVO> projectInitiationListToVO(List<ProjectInitiation> entities);

    // ===== ProjectInvoice =====
    ProjectInvoiceVO entityToVO(ProjectInvoice entity);
    List<ProjectInvoiceVO> projectInvoiceListToVO(List<ProjectInvoice> entities);

    // ===== ProjectOpportunity =====
    ProjectOpportunityVO entityToVO(ProjectOpportunity entity);
    List<ProjectOpportunityVO> projectOpportunityListToVO(List<ProjectOpportunity> entities);

    // ===== ProjectOpportunityFollow =====
    ProjectOpportunityFollowVO entityToVO(ProjectOpportunityFollow entity);
    List<ProjectOpportunityFollowVO> projectOpportunityFollowListToVO(List<ProjectOpportunityFollow> entities);

    // ===== ProjectPayment =====
    ProjectPaymentVO entityToVO(ProjectPayment entity);
    List<ProjectPaymentVO> projectPaymentListToVO(List<ProjectPayment> entities);

    // ===== ProjectProfitSimulation =====
    ProjectProfitSimulationVO entityToVO(ProjectProfitSimulation entity);
    List<ProjectProfitSimulationVO> projectProfitSimulationListToVO(List<ProjectProfitSimulation> entities);

    // ===== ProjectProfitSnapshot =====
    ProjectProfitSnapshotVO entityToVO(ProjectProfitSnapshot entity);
    List<ProjectProfitSnapshotVO> projectProfitSnapshotListToVO(List<ProjectProfitSnapshot> entities);

    // ===== ProjectReconcileDaily =====
    ProjectReconcileDailyVO entityToVO(ProjectReconcileDaily entity);
    List<ProjectReconcileDailyVO> projectReconcileDailyListToVO(List<ProjectReconcileDaily> entities);

    // ===== ProjectRevenue =====
    ProjectRevenueVO entityToVO(ProjectRevenue entity);
    List<ProjectRevenueVO> projectRevenueListToVO(List<ProjectRevenue> entities);

    // ===== RateCard =====
    RateCardVO entityToVO(RateCard entity);
    List<RateCardVO> rateCardListToVO(List<RateCard> entities);

    // ===== RateInternal =====
    RateInternalVO entityToVO(RateInternal entity);
    List<RateInternalVO> rateInternalListToVO(List<RateInternal> entities);

    // ===== Satisfaction =====
    SatisfactionVO entityToVO(Satisfaction entity);
    List<SatisfactionVO> satisfactionListToVO(List<Satisfaction> entities);

    // ===== Warranty =====
    WarrantyVO entityToVO(Warranty entity);
    List<WarrantyVO> warrantyListToVO(List<Warranty> entities);

}