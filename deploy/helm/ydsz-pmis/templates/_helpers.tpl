# =============================================================================
#  YDSZ PMIS · Helm Chart 模板辅助函数
# =============================================================================

{{/*
生成镜像全名：{registry}/{repository}/{name}:{tag}
*/}}
{{- define "ydsz-pmis.image" -}}
{{- $registry := .registry -}}
{{- $repository := .repository -}}
{{- $name := .name -}}
{{- $tag := .tag | default .defaultTag -}}
{{- if $registry -}}
{{- printf "%s/%s/%s:%s" $registry $repository $name $tag -}}
{{- else -}}
{{- printf "%s/%s:%s" $repository $name $tag -}}
{{- end -}}
{{- end -}}

{{/*
生成 ServiceAccount 名
*/}}
{{- define "ydsz-pmis.serviceAccountName" -}}
{{- if .Values.serviceAccount.create -}}
{{- default (printf "ydsz-pmis-sa") .Values.serviceAccount.name -}}
{{- else -}}
{{- default "default" .Values.serviceAccount.name -}}
{{- end -}}
{{- end -}}

{{/*
生成完整应用名（带 release 前缀）
*/}}
{{- define "ydsz-pmis.fullname" -}}
{{- printf "%s-%s" .Release.Name .name -}}
{{- end -}}

{{/*
公共标签
*/}}
{{- define "ydsz-pmis.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/part-of: ydsz-pmis
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{/*
后端微服务名（不带前缀，如 gateway / system）
*/}}
{{- define "ydsz-pmis.serviceName" -}}
{{- .name -}}
{{- end -}}
