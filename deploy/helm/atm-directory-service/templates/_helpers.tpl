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

{{- define "atm.migrationSecretName" -}}
{{ include "atm.name" . }}-db-migration
{{- end -}}

{{/* Container hardening shared by the migration init container and the service. */}}
{{- define "atm.containerSecurityContext" -}}
allowPrivilegeEscalation: false
readOnlyRootFilesystem: true
capabilities:
  drop: ["ALL"]
{{- end -}}

{{/*
Strict parse of a JDBC URL in config (review #13). Equivalent of the shared chart's
parse (cicd-templates 13ca2c6 charts/fintechbankx-service/templates/_helpers.tpl:76-211),
so the driver can only use the TLS settings checked here:
- jdbc:postgresql:// with exactly one '?' and no fragment; nothing that looks like a
  parameter ('=', '&', ';' or '%') before the '?';
- exactly one sslmode, equal to verify-full, and exactly one sslrootcert, equal to
  <databaseCaBundle.mountPath>/<databaseCaBundle.key>;
- no other ssl* key (sslfactory, sslfactoryarg, sslhostnameverifier,
  sslpasswordcallback, sslcert, ...) and no service: they can switch verification
  off or redirect the connection;
- TLS keys in lower case only, and no percent-encoded parameter name.
Arguments: dict "key" (config key), "url", "root" (expected sslrootcert path).
*/}}
{{- define "atm.strictJdbcUrl" -}}
{{- $url := toString .url -}}
{{- $hint := printf "config.%s must be jdbc:postgresql://<host>:5432/<db>?sslmode=verify-full&sslrootcert=%s (deploy/terraform outputs reader_jdbc_url and jdbc_url)" .key .root -}}
{{- if not (hasPrefix "jdbc:postgresql://" $url) -}}
{{- fail (printf "%s: not a jdbc:postgresql:// URL" $hint) -}}
{{- end -}}
{{- if or (contains "#" $url) (ne 2 (len (splitList "?" $url))) -}}
{{- fail (printf "%s: it needs exactly one '?' and no fragment" $hint) -}}
{{- end -}}
{{- if regexMatch "[=&;%]" (index (splitList "?" $url) 0) -}}
{{- fail (printf "%s: no parameter may come before the '?'" $hint) -}}
{{- end -}}
{{- $modes := 0 -}}
{{- $roots := 0 -}}
{{- range $pair := splitList "&" (index (splitList "?" $url) 1) -}}
{{- if not (contains "=" $pair) -}}
{{- fail (printf "%s: parameter %q has no value" $hint $pair) -}}
{{- end -}}
{{- $name := first (splitList "=" $pair) -}}
{{- $value := trimPrefix (printf "%s=" $name) $pair -}}
{{- if contains "%" $name -}}
{{- fail (printf "%s: parameter names must not be percent-encoded (%s)" $hint $name) -}}
{{- end -}}
{{- if or (hasPrefix "ssl" (lower $name)) (eq (lower $name) "service") -}}
{{- if ne $name (lower $name) -}}
{{- fail (printf "%s: TLS keys are lower case only (%s)" $hint $name) -}}
{{- else if eq $name "sslmode" -}}
{{- $modes = add1 $modes -}}
{{- if ne $value "verify-full" -}}
{{- fail (printf "%s: sslmode=%s is not verify-full" $hint $value) -}}
{{- end -}}
{{- else if eq $name "sslrootcert" -}}
{{- $roots = add1 $roots -}}
{{- if ne $value $.root -}}
{{- fail (printf "%s: sslrootcert=%s is not the mounted RDS CA bundle" $hint $value) -}}
{{- end -}}
{{- else -}}
{{- fail (printf "%s: %s is refused (it can switch certificate verification off or redirect the connection)" $hint $name) -}}
{{- end -}}
{{- end -}}
{{- end -}}
{{- if ne $modes 1 -}}
{{- fail (printf "%s: sslmode must appear exactly once (found %d)" $hint $modes) -}}
{{- end -}}
{{- if ne $roots 1 -}}
{{- fail (printf "%s: sslrootcert must appear exactly once (found %d)" $hint $roots) -}}
{{- end -}}
{{- end -}}

{{/*
config.* must not reach the datasource or Flyway around the parsed URL keys (review #13):
- no spring.datasource.* or spring.flyway.* key in any spelling (SPRING_DATASOURCE_URL,
  spring.datasource.url, SPRING_DATASOURCE_HIKARI_DATA_SOURCE_PROPERTIES_*), except
  SPRING_DATASOURCE_USERNAME;
- no jdbc: URL in any value other than the parsed keys;
- no JAVA_TOOL_OPTIONS, JDK_JAVA_OPTIONS or JAVA_OPTS that mention jdbc, ssl or spring
  (system properties outrank the environment).
Arguments: dict "config" (.Values.config), "urlKeys" (keys parsed by atm.strictJdbcUrl).
*/}}
{{- define "atm.refuseDatasourceOverrides" -}}
{{- $urlKeys := .urlKeys -}}
{{- range $key, $value := .config -}}
{{- $norm := regexReplaceAll "[._-]" (lower $key) "" -}}
{{- if and (or (hasPrefix "springdatasource" $norm) (hasPrefix "springflyway" $norm)) (ne $norm "springdatasourceusername") -}}
{{- fail (printf "config.%s is refused: spring.datasource.* and spring.flyway.* come from application.yml and the chart (the URL goes in config.DB_URL, which is parsed; only SPRING_DATASOURCE_USERNAME may be set)" $key) -}}
{{- end -}}
{{- if and (not (has $key $urlKeys)) (regexMatch "(?i)jdbc:" (toString $value)) -}}
{{- fail (printf "config.%s is refused: a jdbc: URL belongs only in %s, which the chart parses" $key (join ", " $urlKeys)) -}}
{{- end -}}
{{- if and (has $norm (list "javatooloptions" "jdkjavaoptions" "javaopts")) (regexMatch "(?i)jdbc|ssl|spring" (toString $value)) -}}
{{- fail (printf "config.%s is refused: JVM options must not set JDBC, TLS or Spring properties" $key) -}}
{{- end -}}
{{- end -}}
{{- end -}}
