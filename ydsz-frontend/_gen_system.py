#!/usr/bin/env python3
"""Generate frontend pages for all sub-apps based on backend controllers."""
import os
import json

BASE = r"d:\Code\ydsz\ydsz-pmis\ydsz-frontend\apps"

def write_file(path, content):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, 'w', encoding='utf-8') as f:
        f.write(content)
    print(f"  Created: {os.path.relpath(path, BASE)}")

def gen_api_module(app_dir, module_name, api_base, vo_fields, create_fields, page_query_fields=None, extra_methods=None):
    """Generate a standard API module TypeScript file."""
    vo_type = f"{module_name.capitalize()}VO"
    dto_type = f"{module_name.capitalize()}DTO"
    query_type = f"{module_name.capitalize()}PageQuery"

    # Build namespace
    lines = []
    lines.append("import { requestClient } from '#/api/request';")
    lines.append("")
    lines.append(f"export namespace {module_name.capitalize()}Api {{")
    lines.append(f"  export interface {vo_type} {{")

    for field in vo_fields:
        if isinstance(field, tuple):
            lines.append(f"    {field[0]}: {field[1]};")
        else:
            lines.append(f"    {field};")

    lines.append("  }")
    lines.append("")

    if page_query_fields:
        lines.append(f"  export interface {query_type} {{")
        for field in page_query_fields:
            if isinstance(field, tuple):
                lines.append(f"    {field[0]}?: {field[1]};")
            else:
                lines.append(f"    {field};")
        lines.append("  }")
        lines.append("")

    lines.append(f"  export interface {dto_type} {{")
    for field in create_fields:
        if isinstance(field, tuple):
            lines.append(f"    {field[0]}?: {field[1]};")
        else:
            lines.append(f"    {field};")
    lines.append("  }")
    lines.append("}")

    lines.append("")
    lines.append(f"/** 分页查询{module_name}列表 */")
    lines.append(f"export function get{module_name.capitalize()}PageApi(params: {module_name.capitalize()}Api.{query_type}) {{")
    lines.append("  return requestClient.get<{")
    lines.append("    total: number;")
    lines.append("    current: number;")
    lines.append("    size: number;")
    lines.append(f"    items: {module_name.capitalize()}Api.{vo_type}[];")
    lines.append(f"  }}>(`{api_base}/page`, {{ params }});")
    lines.append("}")
    lines.append("")
    lines.append(f"/** 查询全部{module_name}列表 */")
    lines.append(f"export function get{module_name.capitalize()}ListApi() {{")
    lines.append(f"  return requestClient.get<{module_name.capitalize()}Api.{vo_type}[]>(`{api_base}/list`);")
    lines.append("}")
    lines.append("")
    lines.append(f"/** 根据 ID 查询{module_name} */")
    lines.append(f"export function get{module_name.capitalize()}ByIdApi(id: string) {{")
    lines.append(f"  return requestClient.get<{module_name.capitalize()}Api.{vo_type}>(`{api_base}/${{id}}`);")
    lines.append("}")
    lines.append("")
    lines.append(f"/** 创建{module_name} */")
    lines.append(f"export function create{module_name.capitalize()}Api(data: {module_name.capitalize()}Api.{dto_type}) {{")
    lines.append(f"  return requestClient.post<string>(`{api_base}`, data);")
    lines.append("}")
    lines.append("")
    lines.append(f"/** 更新{module_name} */")
    lines.append(f"export function update{module_name.capitalize()}Api(data: {module_name.capitalize()}Api.{dto_type}) {{")
    lines.append(f"  return requestClient.put<boolean>(`{api_base}`, data);")
    lines.append("}")
    lines.append("")
    lines.append(f"/** 删除{module_name} */")
    lines.append(f"export function delete{module_name.capitalize()}Api(id: string) {{")
    lines.append(f"  return requestClient.delete<boolean>(`{api_base}/${{id}}`);")
    lines.append("}")

    if extra_methods:
        lines.append("")
        lines.append(extra_methods)

    write_file(os.path.join(app_dir, 'src', 'api', f'{module_name}.ts'), '\n'.join(lines) + '\n')


def gen_crud_page(app_dir, module_name, title, fields_config):
    """Generate a standard CRUD page with VXE grid and form modal."""
    view_dir = os.path.join(app_dir, 'src', 'views', module_name)

    # Capitalize for component names
    Cap = module_name.capitalize()
    cap = module_name

    # Generate index.vue
    index_vue = f"""<script lang="ts" setup>
import type {{ VxeGridProps }} from '@ydsz/plugins/vxe-table';

import {{ Page, useVbenModal }} from '@ydsz/common-ui';

import {{ ElButton, ElMessage, ElMessageBox, ElTag, h }} from 'element-plus';

import {{ useYDSZVxeGrid }} from '#/adapter/vxe-table';
import {{
  delete{Cap}Api,
  get{Cap}PageApi,
  type {Cap}Api,
}} from '#/api/{cap}';

import {Cap}Form from './{cap}-form.vue';

defineOptions({{ name: '{Cap}Management' }});

const gridOptions: VxeGridProps<{Cap}Api.{Cap}VO> = {{
  columns: [
    {{ type: 'seq', width: 50, title: '序号' }},
{chr(10).join([f"    {{ field: '{f['field']}', title: '{f['title']}', width: {f.get('width', 150)} }}," for f in fields_config['columns']])}
    {{
      field: 'action',
      title: '操作',
      width: 160,
      fixed: 'right',
      slots: {{
        default: ({{ row }}) => {{
          return h('div', {{ class: 'flex gap-1' }}, [
            h(ElButton, {{ size: 'small', link: true, type: 'primary', onClick: () => handleEdit(row) }}, () => '编辑'),
            h(ElButton, {{ size: 'small', link: true, type: 'danger', onClick: () => handleDelete(row) }}, () => '删除'),
          ]);
        }},
      }},
    }},
  ],
  height: 'auto',
  pagerConfig: {{ pageSize: 20, pageSizes: [10, 20, 50, 100] }},
  proxyConfig: {{
    ajax: {{
      query: async ({{ page }}, formValues) => {{
        return await get{Cap}PageApi({{
          pageNum: page.currentPage,
          pageSize: page.pageSize,
          ...formValues,
        }});
      }},
    }},
  }},
  toolbarConfig: {{ custom: true, refresh: {{ code: 'query' }}, search: true, zoom: true }},
  formConfig: {{
    enabled: true,
    items: [
{chr(10).join([f"      {{ field: '{f['field']}', title: '{f['title']}', itemRender: {{ name: 'Input', props: {{ placeholder: '{f['title']}' }} }} }}," for f in fields_config['search'] if f.get('search', True)])}
    ],
  }},
}};

const [Grid, gridApi] = useYDSZVxeGrid({{ gridOptions }});

const [{Cap}FormModal, {cap}FormApi] = useVbenModal({{ connectedComponent: {Cap}Form }});

function handleAdd() {{
  {cap}FormApi.open();
}}

function handleEdit(row: {Cap}Api.{Cap}VO) {{
  {Cap}FormApi.setData({{ record: row }});
  {Cap}FormApi.open();
}}

async function handleDelete(row: {Cap}Api.{Cap}VO) {{
  try {{
    await ElMessageBox.confirm(`确定删除「${{row.{fields_config['nameField']}}}」吗？`, '删除确认', {{ type: 'warning' }});
    await delete{Cap}Api(row.id);
    ElMessage.success('删除成功');
    gridApi.query();
  }} catch {{
    // cancelled
  }}
}}
</script>

<template>
  <Page auto-content-height>
    <Grid table-title="{title}">
      <template #toolbar-tools>
        <ElButton type="primary" @click="handleAdd">新增</ElButton>
      </template>
    </Grid>
    <{Cap}FormModal @success="gridApi.query()" />
  </Page>
</template>
"""
    write_file(os.path.join(view_dir, 'index.vue'), index_vue)

    # Generate form.vue
    form_fields = fields_config['formFields']
    form_vue = f"""<script lang="ts" setup>
import type {{ {Cap}Api }} from '#/api/{cap}';

import {{ useVbenModal }} from '@ydsz/common-ui';
import {{ ElForm, ElFormItem, ElInput, ElInputNumber, ElMessage, ElRadioGroup, ElRadio }} from 'element-plus';
import {{ computed, reactive, ref }} from 'vue';

import {{ create{Cap}Api, update{Cap}Api }} from '#/api/{cap}';

const emit = defineEmits<{{ success: [] }}>();

const formRef = ref();
const isEdit = ref(false);

const formData = reactive({{
  id: '',
{chr(10).join([f"  {f['field']}: {f.get('default', "''")}," for f in form_fields])}
}});

const rules = {{
{chr(10).join([f"  {f['field']}: [{{ required: true, message: '请输入{f['title']}', trigger: 'blur' }}]," for f in form_fields if f.get('required', False)])}
}};

const [Modal, modalApi] = useVbenModal({{
  onOpenChange: (isOpen) => {{
    if (!isOpen) return;
    const data = modalApi.getData<{{ record?: {Cap}Api.{Cap}VO }}>();
    if (data?.record) {{
      isEdit.value = true;
      Object.assign(formData, {{
        id: data.record.id,
{chr(10).join([f"        {f['field']}: data.record.{f['field']} {f.get('default', "|| ''") if f.get('optional') else f'|| {f.get("default", chr(39) + chr(39))}'}," for f in form_fields])}
      }});
    }} else {{
      isEdit.value = false;
      Object.assign(formData, {{
        id: '',
{chr(10).join([f"        {f['field']}: {f.get('default', "''")}," for f in form_fields])}
      }});
    }}
  }},
  onConfirm: async () => {{
    try {{ await formRef.value?.validate(); }} catch {{ return; }}
    modalApi.lock();
    try {{
      if (isEdit.value) {{
        await update{Cap}Api(formData as any);
        ElMessage.success('更新成功');
      }} else {{
        await create{Cap}Api(formData as any);
        ElMessage.success('创建成功');
      }}
      emit('success');
      modalApi.close();
    }} finally {{
      modalApi.unlock();
    }}
  }},
}});

const title = computed(() => (isEdit.value ? '编辑{title[:-2]}' : '新增{title[:-2]}'));
</script>

<template>
  <Modal :title="title">
    <ElForm ref="formRef" :model="formData" :rules="rules" label-width="100px" label-position="right">
{chr(10).join([f"""      <ElFormItem label="{f['title']}" prop="{f['field']}">
        <ElInput v-model="formData.{f['field']}" placeholder="请输入{f['title']}"{' :disabled="isEdit"' if f.get('disabledOnEdit') else ''} />
      </ElFormItem>""" for f in form_fields if f.get('type', 'input') == 'input'])}
{chr(10).join([f"""      <ElFormItem label="{f['title']}">
        <ElInputNumber v-model="formData.{f['field']}" :min="0" :max="999" />
      </ElFormItem>""" for f in form_fields if f.get('type') == 'number'])}
{chr(10).join([f"""      <ElFormItem label="{f['title']}">
        <ElInput v-model="formData.{f['field']}" type="textarea" :rows="2" placeholder="请输入{f['title']}" />
      </ElFormItem>""" for f in form_fields if f.get('type') == 'textarea'])}
{chr(10).join([f"""      <ElFormItem label="{f['title']}">
        <ElRadioGroup v-model="formData.{f['field']}">
          <ElRadio :value="1">启用</ElRadio>
          <ElRadio :value="0">禁用</ElRadio>
        </ElRadioGroup>
      </ElFormItem>""" for f in form_fields if f.get('type') == 'status'])}
    </ElForm>
  </Modal>
</template>
"""
    write_file(os.path.join(view_dir, f'{cap}-form.vue'), form_vue)


# ==================== SYSTEM-WEB ====================
print("Generating system-web...")
sys_dir = os.path.join(BASE, "system-web")

# Config API
gen_api_module(sys_dir, "config", "/api/v1/config",
    vo_fields=[
        ("id", "string"),
        ("configKey", "string"),
        ("configValue", "string"),
        ("configGroup", "string"),
        ("configName", "string"),
        ("valueType", "string"),
        ("isPublic", "number"),
        ("remark", "string"),
        ("createTime", "string"),
    ],
    create_fields=[
        ("configKey", "string"),
        ("configValue", "string"),
        ("configGroup", "string"),
        ("configName", "string"),
        ("valueType", "string"),
        ("isPublic", "number"),
        ("remark", "string"),
    ],
    page_query_fields=[
        ("pageNum", "number"),
        ("pageSize", "number"),
        ("configKey", "string"),
        ("configGroup", "string"),
    ],
)

# Dict Type API
gen_api_module(sys_dir, "dictType", "/api/v1/dict/type",
    vo_fields=[
        ("id", "string"),
        ("typeCode", "string"),
        ("typeName", "string"),
        ("remark", "string"),
        ("status", "number"),
        ("createTime", "string"),
    ],
    create_fields=[
        ("typeCode", "string"),
        ("typeName", "string"),
        ("remark", "string"),
        ("status", "number"),
    ],
    page_query_fields=[
        ("pageNum", "number"),
        ("pageSize", "number"),
        ("typeName", "string"),
        ("typeCode", "string"),
    ],
)

# Dict Item API
gen_api_module(sys_dir, "dictItem", "/api/v1/dict/item",
    vo_fields=[
        ("id", "string"),
        ("typeCode", "string"),
        ("itemCode", "string"),
        ("itemText", "string"),
        ("itemValue", "string"),
        ("sort", "number"),
        ("status", "number"),
        ("parentId", "string"),
        ("remark", "string"),
        ("createTime", "string"),
    ],
    create_fields=[
        ("typeCode", "string"),
        ("itemCode", "string"),
        ("itemText", "string"),
        ("itemValue", "string"),
        ("sort", "number"),
        ("status", "number"),
        ("parentId", "string"),
        ("remark", "string"),
    ],
    page_query_fields=[
        ("pageNum", "number"),
        ("pageSize", "number"),
        ("typeCode", "string"),
        ("itemCode", "string"),
        ("status", "string"),
    ],
    extra_methods="""/** 按类型编码查询启用的字典项列表 */
export function getDictItemListByTypeApi(typeCode: string) {
  return requestClient.get<DictItemApi.DictItemVO[]>(`/api/v1/dict/item/type/${typeCode}`);
}""",
)

# Variable API
gen_api_module(sys_dir, "variable", "/api/v1/variable",
    vo_fields=[
        ("id", "string"),
        ("variableKey", "string"),
        ("variableValue", "string"),
        ("variableType", "string"),
        ("remark", "string"),
        ("status", "number"),
        ("createTime", "string"),
    ],
    create_fields=[
        ("variableKey", "string"),
        ("variableValue", "string"),
        ("variableType", "string"),
        ("remark", "string"),
        ("status", "number"),
    ],
    page_query_fields=[
        ("pageNum", "number"),
        ("pageSize", "number"),
        ("variableKey", "string"),
        ("status", "string"),
    ],
)

# App Info API
gen_api_module(sys_dir, "app", "/api/v1/app",
    vo_fields=[
        ("id", "string"),
        ("appCode", "string"),
        ("appName", "string"),
        ("appSecret", "string"),
        ("appType", "string"),
        ("redirectUri", "string"),
        ("status", "number"),
        ("remark", "string"),
        ("createTime", "string"),
    ],
    create_fields=[
        ("appCode", "string"),
        ("appName", "string"),
        ("appSecret", "string"),
        ("appType", "string"),
        ("redirectUri", "string"),
        ("status", "number"),
        ("remark", "string"),
    ],
    page_query_fields=[
        ("pageNum", "number"),
        ("pageSize", "number"),
        ("appName", "string"),
        ("status", "string"),
    ],
)

# Generate system-web API index
write_file(os.path.join(sys_dir, 'src', 'api', 'index.ts'),
    "export * from './core';\nexport * from './config';\nexport * from './dictType';\nexport * from './dictItem';\nexport * from './variable';\nexport * from './app';\n")

# Generate CRUD pages for system-web
gen_crud_page(sys_dir, "config", "系统配置", {
    'columns': [
        {"field": "configKey", "title": "配置键", "width": 160},
        {"field": "configName", "title": "配置名称", "width": 150},
        {"field": "configGroup", "title": "配置分组", "width": 120},
        {"field": "valueType", "title": "值类型", "width": 100},
        {"field": "configValue", "title": "配置值", "width": 200},
        {"field": "remark", "title": "备注", "width": 150},
    ],
    'search': [
        {"field": "configKey", "title": "配置键"},
        {"field": "configGroup", "title": "配置分组"},
    ],
    'nameField': "configKey",
    'formFields': [
        {"field": "configKey", "title": "配置键", "required": True, "disabledOnEdit": True},
        {"field": "configName", "title": "配置名称", "required": True},
        {"field": "configGroup", "title": "配置分组"},
        {"field": "valueType", "title": "值类型"},
        {"field": "configValue", "title": "配置值", "type": "textarea"},
        {"field": "remark", "title": "备注", "type": "textarea"},
        {"field": "isPublic", "title": "是否公开", "type": "status", "default": "0"},
        {"field": "status", "title": "状态", "type": "status", "default": "1"},
    ],
})

gen_crud_page(sys_dir, "dictType", "字典类型", {
    'columns': [
        {"field": "typeCode", "title": "类型编码", "width": 150},
        {"field": "typeName", "title": "类型名称", "width": 150},
        {"field": "remark", "title": "备注", "width": 200},
        {"field": "status", "title": "状态", "width": 80},
        {"field": "createTime", "title": "创建时间", "width": 160},
    ],
    'search': [
        {"field": "typeName", "title": "类型名称"},
        {"field": "typeCode", "title": "类型编码"},
    ],
    'nameField': "typeName",
    'formFields': [
        {"field": "typeCode", "title": "类型编码", "required": True, "disabledOnEdit": True},
        {"field": "typeName", "title": "类型名称", "required": True},
        {"field": "remark", "title": "备注", "type": "textarea"},
        {"field": "status", "title": "状态", "type": "status", "default": "1"},
    ],
})

gen_crud_page(sys_dir, "dictItem", "字典项", {
    'columns': [
        {"field": "typeCode", "title": "字典类型", "width": 120},
        {"field": "itemCode", "title": "字典项编码", "width": 120},
        {"field": "itemText", "title": "显示文本", "width": 150},
        {"field": "itemValue", "title": "字典值", "width": 120},
        {"field": "sort", "title": "排序", "width": 80},
        {"field": "status", "title": "状态", "width": 80},
        {"field": "createTime", "title": "创建时间", "width": 160},
    ],
    'search': [
        {"field": "typeCode", "title": "字典类型"},
        {"field": "itemCode", "title": "字典项编码"},
    ],
    'nameField': "itemCode",
    'formFields': [
        {"field": "typeCode", "title": "字典类型", "required": True},
        {"field": "itemCode", "title": "字典项编码", "required": True, "disabledOnEdit": True},
        {"field": "itemText", "title": "显示文本", "required": True},
        {"field": "itemValue", "title": "字典值"},
        {"field": "sort", "title": "排序", "type": "number", "default": "0"},
        {"field": "remark", "title": "备注", "type": "textarea"},
        {"field": "status", "title": "状态", "type": "status", "default": "1"},
    ],
})

gen_crud_page(sys_dir, "variable", "系统变量", {
    'columns': [
        {"field": "variableKey", "title": "变量键", "width": 160},
        {"field": "variableValue", "title": "变量值", "width": 200},
        {"field": "variableType", "title": "变量类型", "width": 100},
        {"field": "remark", "title": "备注", "width": 200},
        {"field": "status", "title": "状态", "width": 80},
        {"field": "createTime", "title": "创建时间", "width": 160},
    ],
    'search': [
        {"field": "variableKey", "title": "变量键"},
    ],
    'nameField': "variableKey",
    'formFields': [
        {"field": "variableKey", "title": "变量键", "required": True, "disabledOnEdit": True},
        {"field": "variableValue", "title": "变量值", "type": "textarea"},
        {"field": "variableType", "title": "变量类型"},
        {"field": "remark", "title": "备注", "type": "textarea"},
        {"field": "status", "title": "状态", "type": "status", "default": "1"},
    ],
})

gen_crud_page(sys_dir, "app", "应用注册", {
    'columns': [
        {"field": "appCode", "title": "应用编码", "width": 120},
        {"field": "appName", "title": "应用名称", "width": 150},
        {"field": "appType", "title": "应用类型", "width": 100},
        {"field": "redirectUri", "title": "回调地址", "width": 250},
        {"field": "status", "title": "状态", "width": 80},
        {"field": "createTime", "title": "创建时间", "width": 160},
    ],
    'search': [
        {"field": "appName", "title": "应用名称"},
    ],
    'nameField': "appName",
    'formFields': [
        {"field": "appCode", "title": "应用编码", "required": True, "disabledOnEdit": True},
        {"field": "appName", "title": "应用名称", "required": True},
        {"field": "appSecret", "title": "应用密钥"},
        {"field": "appType", "title": "应用类型"},
        {"field": "redirectUri", "title": "回调地址"},
        {"field": "remark", "title": "备注", "type": "textarea"},
        {"field": "status", "title": "状态", "type": "status", "default": "1"},
    ],
})

# Generate system-web routes
write_file(os.path.join(sys_dir, 'src', 'router', 'routes', 'modules', 'system.ts'), """import type { RouteRecordRaw } from 'vue-router';

const routes: RouteRecordRaw[] = [
  {
    meta: {
      icon: 'lucide:settings',
      order: 1,
      title: '系统管理',
    },
    name: 'System',
    path: '/system',
    children: [
      {
        name: 'ConfigManagement',
        path: 'config',
        component: () => import('#/views/config/index.vue'),
        meta: { icon: 'lucide:sliders-horizontal', title: '系统配置' },
      },
      {
        name: 'DictTypeManagement',
        path: 'dict-type',
        component: () => import('#/views/dictType/index.vue'),
        meta: { icon: 'lucide:book-open', title: '字典类型' },
      },
      {
        name: 'DictItemManagement',
        path: 'dict-item',
        component: () => import('#/views/dictItem/index.vue'),
        meta: { icon: 'lucide:list', title: '字典项' },
      },
      {
        name: 'VariableManagement',
        path: 'variable',
        component: () => import('#/views/variable/index.vue'),
        meta: { icon: 'lucide:variable', title: '系统变量' },
      },
      {
        name: 'AppManagement',
        path: 'app',
        component: () => import('#/views/app/index.vue'),
        meta: { icon: 'lucide:app-window', title: '应用注册' },
      },
    ],
  },
];

export default routes;
""")

print("system-web done!")
