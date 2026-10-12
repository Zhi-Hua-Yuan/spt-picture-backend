{{- define "picture.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" }}
{{- end }}

{{- define "picture.fullname" -}}
{{- if .Values.fullnameOverride }}{{ .Values.fullnameOverride | trunc 63 | trimSuffix "-" }}{{ else }}{{ include "picture.name" . }}{{ end }}
{{- end }}

{{- define "picture.labels" -}}
app.kubernetes.io/name: {{ include "picture.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version | replace "+" "_" }}
{{- end }}

{{- define "picture.selectorLabels" -}}
app.kubernetes.io/name: {{ include "picture.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end }}

{{- define "picture.secretName" -}}
{{- default (printf "%s-secrets" (include "picture.fullname" .)) .Values.secrets.name }}
{{- end }}
