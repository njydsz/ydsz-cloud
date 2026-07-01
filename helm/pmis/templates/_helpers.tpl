{{/*
基础辅助模板
*/}}

{{/*
展开完整名称
*/}}
{{- define "pmis.fullname" -}}
{{- $name := default "pmis" .Values.nameOverride -}}
{{- if eq .Release.Name $name -}}
{{- $name | trunc 63 | trimSuffix "-" -}}
{{- else -}}
{{- printf "%s-%s" .Release.Name $name | trunc 63 | trimSuffix "-" -}}
{{- end -}}
{{- end -}}

{{/*
通用 labels
*/}}
{{- define "pmis.labels" -}}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{ include "pmis.selectorLabels" . }}
app.kubernetes.io/version: {{ .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/part-of: pmis
{{- end -}}

{{/*
Selector labels
*/}}
{{- define "pmis.selectorLabels" -}}
app.kubernetes.io/name: {{ include "pmis.fullname" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
