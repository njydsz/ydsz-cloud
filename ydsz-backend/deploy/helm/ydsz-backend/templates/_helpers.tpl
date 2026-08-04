{{/*
  YDSZ · 后端微服务通用 Helm Chart 辅助函数

  扩展 chart 时调用：
    {{ include "ydsz-backend.fullname" . }}
    {{ include "ydsz-backend.labels" . }}
    {{ include "ydsz-backend.selectorLabels" . }}
*/}}

{{/*
  fullname: 资源名称。
  优先取 values.serviceName；若为空则回退到 .Chart.Name，保证 chart 通用性。
  被 Deployment / Service / HPA 共用，确保 selector 与 metadata.name 一致。
*/}}
{{- define "ydsz-backend.fullname" -}}
{{- if .Values.serviceName -}}
{{- .Values.serviceName -}}
{{- else -}}
{{- .Chart.Name -}}
{{- end -}}
{{- end -}}

{{/*
  通用标签：chart 版本 / app 版本 / 模块 / 组件 / 维护者。
*/}}
{{- define "ydsz-backend.labels" -}}
app.kubernetes.io/name: {{ include "ydsz-backend.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: ydsz-pmis
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
module: {{ .Values.moduleName | default .Chart.Name }}
component: backend
{{- end -}}

{{/*
  selectorLabels：仅保留 Pod 选择所需的稳定标签。
  与 labels 分离，避免 helm.sh/chart 等易变标签进入 selector。
*/}}
{{- define "ydsz-backend.selectorLabels" -}}
app.kubernetes.io/name: {{ include "ydsz-backend.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
