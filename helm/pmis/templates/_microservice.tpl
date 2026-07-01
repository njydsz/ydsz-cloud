{{/*
通用微服务 Deployment 模板
用法: {{- include "pmis.microservice" (list . "auth") -}}
参数: . = root, name = 服务名 (auth/user/project/...)
*/}}
{{- define "pmis.microservice" -}}
{{- $root := index . 0 -}}
{{- $name := index . 1 -}}
{{- $svc := index $root.Values.microservices $name -}}
{{- if $svc.enabled -}}
{{- $fullName := printf "%s-%s" (default "pmis" $root.Release.Name) $name -}}
apiVersion: apps/v1
kind: Deployment
metadata:
  name: {{ $fullName }}
  labels:
    app.kubernetes.io/name: {{ $fullName }}
    app.kubernetes.io/instance: {{ $root.Release.Name }}
    app.kubernetes.io/component: {{ $name }}
    app.kubernetes.io/part-of: pmis
    app.kubernetes.io/version: {{ $root.Chart.AppVersion | quote }}
  annotations:
    prometheus.io/scrape: "true"
    prometheus.io/port: "{{ $svc.port }}"
    prometheus.io/path: "/actuator/prometheus"
spec:
  replicas: {{ $svc.replicas }}
  selector:
    matchLabels:
      app.kubernetes.io/name: {{ $fullName }}
      app.kubernetes.io/component: {{ $name }}
  strategy:
    type: RollingUpdate
    rollingUpdate:
      maxSurge: 1
      maxUnavailable: 0
  template:
    metadata:
      labels:
        app.kubernetes.io/name: {{ $fullName }}
        app.kubernetes.io/instance: {{ $root.Release.Name }}
        app.kubernetes.io/component: {{ $name }}
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "{{ $svc.port }}"
    spec:
      imagePullSecrets:
        {{- if $root.Values.imageCredentials.enabled }}
        - name: {{ $root.Release.Name }}-registry
        {{- end }}
      containers:
        - name: {{ $name }}
          image: "{{ $root.Values.global.imageRegistry }}/ydsz-pmis-{{ $name }}:{{ $root.Values.global.imageTag }}"
          imagePullPolicy: {{ $root.Values.global.imagePullPolicy }}
          ports:
            - name: http
              containerPort: {{ $svc.port }}
              protocol: TCP
            - name: management
              containerPort: {{ $svc.port }}
              protocol: TCP
          env:
            - name: SPRING_PROFILES_ACTIVE
              value: {{ $root.Values.spring.profiles | quote }}
            - name: NACOS_SERVER_ADDR
              value: {{ $root.Values.spring.nacos.serverAddr | quote }}
            - name: NACOS_NAMESPACE
              value: {{ $root.Values.spring.nacos.namespace | quote }}
            - name: JAVA_OPTS
              value: {{ $svc.jvmOpts | quote }}
            {{- with $svc.env }}
            {{- toYaml . | nindent 12 }}
            {{- end }}
          resources:
            {{- toYaml $root.Values.resources | nindent 12 }}
          livenessProbe:
            httpGet:
              path: /actuator/health/liveness
              port: management
            initialDelaySeconds: {{ $root.Values.healthcheck.livenessProbe.initialDelaySeconds }}
            periodSeconds: {{ $root.Values.healthcheck.livenessProbe.periodSeconds }}
            timeoutSeconds: {{ $root.Values.healthcheck.livenessProbe.timeoutSeconds }}
            failureThreshold: {{ $root.Values.healthcheck.livenessProbe.failureThreshold }}
          readinessProbe:
            httpGet:
              path: /actuator/health/readiness
              port: management
            initialDelaySeconds: {{ $root.Values.healthcheck.readinessProbe.initialDelaySeconds }}
            periodSeconds: {{ $root.Values.healthcheck.readinessProbe.periodSeconds }}
            timeoutSeconds: {{ $root.Values.healthcheck.readinessProbe.timeoutSeconds }}
            failureThreshold: {{ $root.Values.healthcheck.readinessProbe.failureThreshold }}
          lifecycle:
            preStop:
              exec:
                command: ["sh", "-c", "sleep 10"]
      terminationGracePeriodSeconds: 30
---
apiVersion: v1
kind: Service
metadata:
  name: {{ $fullName }}
  labels:
    app.kubernetes.io/name: {{ $fullName }}
    app.kubernetes.io/component: {{ $name }}
spec:
  type: {{ $root.Values.network.serviceType }}
  ports:
    - port: {{ $svc.port }}
      targetPort: http
      protocol: TCP
      name: http
    - port: {{ $svc.port }}
      targetPort: management
      protocol: TCP
      name: management
  selector:
    app.kubernetes.io/name: {{ $fullName }}
    app.kubernetes.io/component: {{ $name }}
{{- end -}}
{{- end -}}
