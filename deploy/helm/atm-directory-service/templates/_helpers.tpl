{{- define "atm.name" -}}
{{- .Chart.Name -}}
{{- end -}}

{{- define "atm.selectorLabels" -}}
app.kubernetes.io/name: {{ include "atm.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}

{{- define "atm.labels" -}}
{{ include "atm.selectorLabels" . }}
app.kubernetes.io/version: {{ .Values.image.tag | default .Chart.AppVersion | quote }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "atm.secretName" -}}
{{ include "atm.name" . }}-db
{{- end -}}

{{- define "atm.migrateSecretName" -}}
{{ include "atm.name" . }}-db-migrate
{{- end -}}

{{/* Container hardening shared by the migration init container and the service. */}}
{{- define "atm.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}
