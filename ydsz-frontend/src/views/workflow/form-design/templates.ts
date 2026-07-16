/**
 * @fileoverview 表单模板库 - 预置常用业务表单 schema
 * @description P2-7: 提供 5 个开箱即用的表单模板，用户可一键导入设计器。
 *   模板基于 form-create/element-ui rule 结构，分类对齐流程模板库（HR/FINANCE/ADMIN/PROJECT/GENERAL）。
 * @module views/workflow/form-design/templates
 * @author ydsz-pmis-team
 * @since 1.0.0
 */

/** 表单模板项 */
export interface FormTemplate {
  /** 模板编码（唯一） */
  code: string
  /** 模板名称 */
  name: string
  /** 分类 */
  category: 'HR' | 'FINANCE' | 'ADMIN' | 'PROJECT' | 'GENERAL'
  /** 描述 */
  description: string
  /** 图标（Element Plus 图标组件名） */
  icon: string
  /** form-create rule 数组 */
  rule: Record<string, unknown>[]
  /** form-create options */
  options: Record<string, unknown>
}

/** 预置表单模板列表 */
export const FORM_TEMPLATES: FormTemplate[] = [
  // ==================== 请假申请 ====================
  {
    code: 'leave_form',
    name: '请假申请',
    category: 'HR',
    description: '员工请假申请表单，包含请假类型、时间、时长、事由、附件',
    icon: 'Calendar',
    options: {
      submitBtn: true,
      resetBtn: true,
      form: { labelWidth: '120px', labelPosition: 'right', size: 'default' },
    },
    rule: [
      { type: 'input', field: 'applicant', title: '申请人', props: { disabled: true }, value: '' },
      { type: 'select', field: 'leaveType', title: '请假类型', props: { placeholder: '请选择' },
        options: [
          { label: '事假', value: 'personal' },
          { label: '病假', value: 'sick' },
          { label: '年假', value: 'annual' },
          { label: '婚假', value: 'marriage' },
          { label: '产假', value: 'maternity' },
          { label: '丧假', value: 'bereavement' },
        ],
        validate: [{ required: true, message: '请选择请假类型', trigger: 'change' }],
      },
      { type: 'group', title: '', children: [
        { type: 'el-row', children: [
          { type: 'el-col', props: { span: 12 }, children: [
            { type: 'datePicker', field: 'startTime', title: '开始时间', props: { type: 'datetime', format: 'YYYY-MM-DD HH:mm:ss', valueFormat: 'YYYY-MM-DD HH:mm:ss' },
              validate: [{ required: true, message: '请选择开始时间', trigger: 'change' }],
              col: { span: 24 },
            },
          ]},
          { type: 'el-col', props: { span: 12 }, children: [
            { type: 'datePicker', field: 'endTime', title: '结束时间', props: { type: 'datetime', format: 'YYYY-MM-DD HH:mm:ss', valueFormat: 'YYYY-MM-DD HH:mm:ss' },
              validate: [{ required: true, message: '请选择结束时间', trigger: 'change' }],
              col: { span: 24 },
            },
          ]},
        ]},
      ]},
      { type: 'inputNumber', field: 'duration', title: '请假时长（小时）', props: { min: 0.5, step: 0.5, precision: 1 }, validate: [{ required: true, message: '请输入请假时长', trigger: 'blur' }] },
      { type: 'input', field: 'reason', title: '请假事由', props: { type: 'textarea', rows: 4, placeholder: '请详细说明请假原因' }, validate: [{ required: true, message: '请填写请假事由', trigger: 'blur' }] },
      { type: 'upload', field: 'attachment', title: '附件', props: { action: '/api/file/upload', multiple: true, limit: 5 } },
    ],
  },

  // ==================== 报销申请 ====================
  {
    code: 'expense_form',
    name: '报销申请',
    category: 'FINANCE',
    description: '费用报销申请表单，包含报销类型、金额、费用明细、票据张数、事由',
    icon: 'Money',
    options: {
      submitBtn: true,
      resetBtn: true,
      form: { labelWidth: '120px', labelPosition: 'right', size: 'default' },
    },
    rule: [
      { type: 'input', field: 'reimburser', title: '报销人', props: { disabled: true }, value: '' },
      { type: 'select', field: 'expenseType', title: '报销类型', props: { placeholder: '请选择' },
        options: [
          { label: '差旅费', value: 'travel' },
          { label: '交通费', value: 'transport' },
          { label: '餐饮费', value: 'meal' },
          { label: '办公用品', value: 'office' },
          { label: '通讯费', value: 'communication' },
          { label: '会议费', value: 'meeting' },
          { label: '其他', value: 'other' },
        ],
        validate: [{ required: true, message: '请选择报销类型', trigger: 'change' }],
      },
      { type: 'inputNumber', field: 'amount', title: '报销金额（元）', props: { min: 0, precision: 2, step: 100 }, validate: [{ required: true, message: '请输入报销金额', trigger: 'blur' }] },
      { type: 'input', field: 'detail', title: '费用明细', props: { type: 'textarea', rows: 4, placeholder: '请列明费用明细（时间、地点、金额、用途）' }, validate: [{ required: true, message: '请填写费用明细', trigger: 'blur' }] },
      { type: 'inputNumber', field: 'receiptCount', title: '票据张数', props: { min: 1, step: 1 }, validate: [{ required: true, message: '请输入票据张数', trigger: 'blur' }] },
      { type: 'input', field: 'reason', title: '报销事由', props: { type: 'textarea', rows: 3 } },
      { type: 'upload', field: 'receipts', title: '票据附件', props: { action: '/api/file/upload', multiple: true, accept: '.jpg,.jpeg,.png,.pdf', limit: 10 } },
    ],
  },

  // ==================== 合同审批 ====================
  {
    code: 'contract_form',
    name: '合同审批',
    category: 'PROJECT',
    description: '合同审批表单，包含合同编号、名称、对方单位、金额、签订日期、生效日期',
    icon: 'Document',
    options: {
      submitBtn: true,
      resetBtn: true,
      form: { labelWidth: '120px', labelPosition: 'right', size: 'default' },
    },
    rule: [
      { type: 'input', field: 'contractNo', title: '合同编号', props: { placeholder: '自动生成或手工录入' } },
      { type: 'input', field: 'contractName', title: '合同名称', validate: [{ required: true, message: '请输入合同名称', trigger: 'blur' }] },
      { type: 'input', field: 'counterparty', title: '对方单位', validate: [{ required: true, message: '请输入对方单位', trigger: 'blur' }] },
      { type: 'select', field: 'contractType', title: '合同类型', props: { placeholder: '请选择' },
        options: [
          { label: '采购合同', value: 'purchase' },
          { label: '销售合同', value: 'sales' },
          { label: '服务合同', value: 'service' },
          { label: '租赁合同', value: 'lease' },
          { label: '承包合同', value: 'contract' },
          { label: '其他', value: 'other' },
        ],
      },
      { type: 'inputNumber', field: 'amount', title: '合同金额（元）', props: { min: 0, precision: 2 }, validate: [{ required: true, message: '请输入合同金额', trigger: 'blur' }] },
      { type: 'group', title: '', children: [
        { type: 'el-row', children: [
          { type: 'el-col', props: { span: 12 }, children: [
            { type: 'datePicker', field: 'signDate', title: '签订日期', props: { type: 'date', format: 'YYYY-MM-DD', valueFormat: 'YYYY-MM-DD' }, col: { span: 24 } },
          ]},
          { type: 'el-col', props: { span: 12 }, children: [
            { type: 'datePicker', field: 'effectiveDate', title: '生效日期', props: { type: 'date', format: 'YYYY-MM-DD', valueFormat: 'YYYY-MM-DD' }, col: { span: 24 } },
          ]},
        ]},
      ]},
      { type: 'datePicker', field: 'expiryDate', title: '到期日期', props: { type: 'date', format: 'YYYY-MM-DD', valueFormat: 'YYYY-MM-DD' } },
      { type: 'input', field: 'remark', title: '合同备注', props: { type: 'textarea', rows: 3 } },
      { type: 'upload', field: 'attachment', title: '合同附件', props: { action: '/api/file/upload', accept: '.pdf,.doc,.docx', limit: 5 } },
    ],
  },

  // ==================== 采购申请 ====================
  {
    code: 'purchase_form',
    name: '采购申请',
    category: 'ADMIN',
    description: '采购申请表单，包含采购物品、规格、数量、单价、总价、供应商、用途',
    icon: 'ShoppingCart',
    options: {
      submitBtn: true,
      resetBtn: true,
      form: { labelWidth: '120px', labelPosition: 'right', size: 'default' },
    },
    rule: [
      { type: 'input', field: 'applicant', title: '申请人', props: { disabled: true }, value: '' },
      { type: 'input', field: 'itemName', title: '采购物品', validate: [{ required: true, message: '请输入采购物品', trigger: 'blur' }] },
      { type: 'input', field: 'specification', title: '规格型号' },
      { type: 'group', title: '', children: [
        { type: 'el-row', children: [
          { type: 'el-col', props: { span: 8 }, children: [
            { type: 'inputNumber', field: 'quantity', title: '数量', props: { min: 1, step: 1 }, col: { span: 24 }, validate: [{ required: true, message: '请输入数量', trigger: 'blur' }] },
          ]},
          { type: 'el-col', props: { span: 8 }, children: [
            { type: 'inputNumber', field: 'unitPrice', title: '单价（元）', props: { min: 0, precision: 2 }, col: { span: 24 }, validate: [{ required: true, message: '请输入单价', trigger: 'blur' }] },
          ]},
          { type: 'el-col', props: { span: 8 }, children: [
            { type: 'inputNumber', field: 'totalPrice', title: '总价（元）', props: { min: 0, precision: 2, disabled: true }, col: { span: 24 } },
          ]},
        ]},
      ]},
      { type: 'input', field: 'supplier', title: '供应商' },
      { type: 'input', field: 'purpose', title: '采购用途', props: { type: 'textarea', rows: 3 }, validate: [{ required: true, message: '请填写采购用途', trigger: 'blur' }] },
      { type: 'upload', field: 'attachment', title: '附件', props: { action: '/api/file/upload', multiple: true } },
    ],
  },

  // ==================== 用印申请 ====================
  {
    code: 'seal_form',
    name: '用印申请',
    category: 'ADMIN',
    description: '用印申请表单，包含用印事由、文件名称、印章类型、用印次数、申请人',
    icon: 'Stamp',
    options: {
      submitBtn: true,
      resetBtn: true,
      form: { labelWidth: '120px', labelPosition: 'right', size: 'default' },
    },
    rule: [
      { type: 'input', field: 'applicant', title: '申请人', props: { disabled: true }, value: '' },
      { type: 'input', field: 'fileName', title: '文件名称', validate: [{ required: true, message: '请输入文件名称', trigger: 'blur' }] },
      { type: 'select', field: 'sealType', title: '印章类型', props: { placeholder: '请选择' },
        options: [
          { label: '公章', value: 'official' },
          { label: '合同章', value: 'contract' },
          { label: '财务章', value: 'finance' },
          { label: '法人章', value: 'legal' },
          { label: '其他', value: 'other' },
        ],
        validate: [{ required: true, message: '请选择印章类型', trigger: 'change' }],
      },
      { type: 'inputNumber', field: 'sealCount', title: '用印次数', props: { min: 1, step: 1 }, validate: [{ required: true, message: '请输入用印次数', trigger: 'blur' }] },
      { type: 'input', field: 'reason', title: '用印事由', props: { type: 'textarea', rows: 4 }, validate: [{ required: true, message: '请填写用印事由', trigger: 'blur' }] },
      { type: 'upload', field: 'attachment', title: '附件', props: { action: '/api/file/upload', multiple: true } },
    ],
  },
]

/** 按分类筛选模板 */
export function filterTemplatesByCategory(category: string | 'ALL'): FormTemplate[] {
  if (category === 'ALL') return FORM_TEMPLATES
  return FORM_TEMPLATES.filter((t) => t.category === category)
}

/** 模板分类选项 */
export const TEMPLATE_CATEGORIES = [
  { label: '全部', value: 'ALL' },
  { label: '人事', value: 'HR' },
  { label: '财务', value: 'FINANCE' },
  { label: '行政', value: 'ADMIN' },
  { label: '项目', value: 'PROJECT' },
  { label: '通用', value: 'GENERAL' },
] as const
