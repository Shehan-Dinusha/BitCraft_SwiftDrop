{{/* ─────────────────────────────────────────────────────────────────────────
     SwiftDrop chart helpers
     ───────────────────────────────────────────────────────────────────────── */}}

{{/* Labels shared by every object the chart renders. Takes the root context. */}}
{{- define "swiftdrop.commonLabels" -}}
app.kubernetes.io/part-of: swiftdrop
app.kubernetes.io/managed-by: {{ .Release.Service }}
app.kubernetes.io/instance: {{ .Release.Name }}
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version | replace "+" "_" | trunc 63 | trimSuffix "-" }}
{{- end }}

{{/* Full image reference.  dict "root" $ "image" <map with repository/tag> */}}
{{- define "swiftdrop.image" -}}
{{- $root := .root -}}
{{- $repo := required "image.repository is required" .image.repository -}}
{{- $tag := .image.tag | default $root.Values.global.imageTag | toString -}}
{{- if $root.Values.global.imageRegistry -}}
{{- printf "%s/%s:%s" $root.Values.global.imageRegistry $repo $tag -}}
{{- else -}}
{{- printf "%s:%s" $repo $tag -}}
{{- end -}}
{{- end }}

{{/* Effective backend service settings as YAML (parse with fromYaml).
     Merge order: serviceDefaults < services.<name> < serviceOverrides.
     dict "root" $ "svc" <raw service values> */}}
{{- define "swiftdrop.serviceConfig" -}}
{{- $root := .root -}}
{{- $merged := mergeOverwrite (deepCopy $root.Values.serviceDefaults) (deepCopy .svc) (deepCopy ($root.Values.serviceOverrides | default dict)) -}}
{{- toYaml $merged -}}
{{- end }}

{{/* hpa.enabled resolved explicitly, because mergeOverwrite cannot set `false`.
     Returns the string "true" or "false".  dict "root" $ "svc" <raw service values> */}}
{{- define "swiftdrop.hpaEnabled" -}}
{{- $enabled := true -}}
{{- $d := get (.root.Values.serviceDefaults | default dict) "hpa" | default dict -}}
{{- if hasKey $d "enabled" }}{{ $enabled = get $d "enabled" }}{{ end -}}
{{- $s := get (.svc | default dict) "hpa" | default dict -}}
{{- if hasKey $s "enabled" }}{{ $enabled = get $s "enabled" }}{{ end -}}
{{- $o := get (.root.Values.serviceOverrides | default dict) "hpa" | default dict -}}
{{- if hasKey $o "enabled" }}{{ $enabled = get $o "enabled" }}{{ end -}}
{{- ternary "true" "false" (eq (toString $enabled) "true") -}}
{{- end }}

{{/* Effective PostgreSQL settings as YAML (parse with fromYaml).
     Merge order: postgres.defaults < postgres.databases.<key> < postgres.overrides.
     dict "root" $ "db" <raw database values> */}}
{{- define "swiftdrop.postgresConfig" -}}
{{- $root := .root -}}
{{- $merged := mergeOverwrite (deepCopy $root.Values.postgres.defaults) (deepCopy .db) (deepCopy ($root.Values.postgres.overrides | default dict)) -}}
{{- toYaml $merged -}}
{{- end }}

{{/* Secret names (kept identical to the pre-Helm manifests). */}}
{{- define "swiftdrop.postgresSecretName" -}}
{{- printf "%s-postgres-credentials" . -}}
{{- end }}

{{/* Headless DNS name of a database.  dict "root" $ "key" <db key> */}}
{{- define "swiftdrop.postgresHost" -}}
{{- printf "%s-postgres-headless.%s.svc.cluster.local" .key .root.Values.namespaces.data -}}
{{- end }}

{{/* Rendered data of the shared backend ConfigMap (used for the checksum too). */}}
{{- define "swiftdrop.commonConfigData" -}}
{{- range $k, $v := .Values.commonConfig }}
{{ $k }}: {{ tpl (toString $v) $ | quote }}
{{- end }}
JAVA_TOOL_OPTIONS: {{ .Values.global.javaToolOptions | quote }}
{{- end }}

{{/* Rendered data of one service's ConfigMap.  dict "root" $ "name" <svc> "s" <merged svc> */}}
{{- define "swiftdrop.serviceConfigData" -}}
{{- $root := .root -}}
{{- $s := .s -}}
{{- range $k, $v := ($s.config | default dict) }}
{{ $k }}: {{ tpl (toString $v) $root | quote }}
{{- end }}
{{- if $s.database }}
{{- $db := index $root.Values.postgres.databases $s.database | default dict }}
{{- $dbName := required (printf "services.%s.database=%s has no matching postgres.databases entry" .name $s.database) $db.dbName }}
DB_URL: {{ printf "jdbc:postgresql://%s:5432/%s" (include "swiftdrop.postgresHost" (dict "root" $root "key" $s.database)) $dbName | quote }}
{{- end }}
{{- end }}
