# CI / CD 全流水线部署（Git + Docker + GitHub Actions / GitLab CI / Jenkins）

> 目标：从 Git 推送到生产发布全自动：
> `push → lint → test → build JAR → build image → push registry → deploy k8s/compose → health check → notify`

---

## 一、整体拓扑

```
开发者
  │  git push
  ▼
┌─────────┐   webhook    ┌──────────────┐
│ GitLab/ │ ───────────▶ │ CI Runner    │  (GitHub Actions / GitLab Runner / Jenkins)
│ GitHub  │              │  - checkout  │
└─────────┘              │  - maven test│
                         │  - docker bui│
                         │  - push hub  │
                         └──────┬───────┘
                                │ image tag
                                ▼
                          ┌──────────────┐
                          │ ArgoCD /     │
                          │ K8s / Compose│
                          └──────┬───────┘
                                 │ rollout
                                 ▼
                         Production Cluster
                         (Gateway / Service / Auth)
```

---

## 二、Git 分支与版本策略

### 2.1 Git Flow（推荐企业级）

| 分支 | 用途 | 触发行为 |
|------|------|---------|
| `main` | 当前生产版本 | tag 后触发生产部署 |
| `develop` | 日常集成 | 部署到 dev 环境 |
| `feature/*` | 功能开发 | PR 触发 lint + test |
| `release/*` | 发布分支 | 部署到 uat 环境 |
| `hotfix/*` | 紧急修复 | 合并 main + develop |

### 2.2 版本号（SemVer）

- `MAJOR.MINOR.PATCH` — `1.0.0` / `1.1.0` / `1.1.1`
- 在 CI 中通过 git tag + maven 写入 `pom.xml.version`
- Docker 镜像打两个 tag：`latest` + `1.0.0-20260417-a1b2c3d`

---

## 三、Dockerfile（多阶段 + 瘦身）

每个服务（pandora-auth / pandora-start / pandora-gateway / pandora-service-user）共用一个模板：

`deploy/docker/Dockerfile`（项目根目录）：

```dockerfile
# ============ Stage 1: Build ============
FROM maven:3.9-eclipse-temurin-25 AS builder
WORKDIR /workspace
COPY . .
ARG MODULE=pandora-start
RUN mvn -B -pl ${MODULE} -am clean package -DskipTests \
    && cp ${MODULE}/target/*.jar app.jar

# ============ Stage 2: Runtime ============
FROM eclipse-temurin:25-jre-alpine
LABEL maintainer="pandora@example.com"
LABEL org.opencontainers.image.source="https://github.com/example/pandora-arch"

# 时区 + 中文字体（可选）
RUN apk add --no-cache tzdata curl && \
    cp /usr/share/zoneinfo/Asia/Shanghai /etc/localtime && \
    echo "Asia/Shanghai" > /etc/timezone

# 非 root 运行
RUN addgroup -S app && adduser -S -G app app
USER app

WORKDIR /app
COPY --from=builder /workspace/app.jar /app/app.jar

EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fs http://localhost:${SERVER_PORT:-8080}/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseZGC -XX:+ZGenerational \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+HeapDumpOnOutOfMemoryError \
               -XX:HeapDumpPath=/app/logs/ \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT exec java $JAVA_OPTS -jar /app/app.jar
```

构建命令：

```bash
docker build -t registry.example.com/pandora/pandora-start:1.0.0 \
  --build-arg MODULE=pandora-start \
  -f deploy/docker/Dockerfile .
```

---

## 四、Docker Compose（生产单机 / 预发布）

`deploy/compose/docker-compose.yml`：

```yaml
version: "3.9"
name: pandora-arch

x-env: &common-env
  TZ: Asia/Shanghai
  SPRING_PROFILES_ACTIVE: prod

services:
  mysql:
    image: mysql:9.6.0
    restart: always
    environment:
      MYSQL_ROOT_PASSWORD: Root@2026
      MYSQL_DATABASE: pandora
    command: --default-authentication-plugin=caching_sha2_password
    volumes:
      - mysql_data:/var/lib/mysql
    networks: [pandora-net]

  redis:
    image: redis:7.4.2
    restart: always
    command: redis-server --requirepass RedisPwd@2026 --appendonly yes
    volumes:
      - redis_data:/data
    networks: [pandora-net]

  kafka:
    image: bitnami/kafka:3.8.1
    restart: always
    environment:
      KAFKA_CFG_NODE_ID: 1
      KAFKA_CFG_PROCESS_ROLES: broker,controller
      KAFKA_CFG_LISTENERS: PLAINTEXT://:9092,CONTROLLER://:9093
      KAFKA_CFG_ADVERTISED_LISTENERS: PLAINTEXT://kafka:9092
      KAFKA_CFG_CONTROLLER_QUORUM_VOTERS: 1@kafka:9093
      KAFKA_CFG_CONTROLLER_LISTENER_NAMES: CONTROLLER
    volumes:
      - kafka_data:/bitnami/kafka
    networks: [pandora-net]

  mongo:
    image: mongo:5.0
    restart: always
    volumes: [mongo_data:/data/db]
    networks: [pandora-net]

  minio:
    image: minio/minio:latest
    restart: always
    command: server /data --console-address ":9001"
    environment:
      MINIO_ROOT_USER: minioadmin
      MINIO_ROOT_PASSWORD: minioadmin
    ports: ["9000:9000", "9001:9001"]
    volumes: [minio_data:/data]
    networks: [pandora-net]

  pandora-auth:
    image: registry.example.com/pandora/pandora-auth:${TAG:-1.0.0}
    restart: always
    environment:
      <<: *common-env
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/pandora
      SPRING_DATA_REDIS_HOST: redis
      SPRING_DATA_REDIS_PASSWORD: RedisPwd@2026
    ports: ["9100:9100"]
    depends_on: [mysql, redis]
    networks: [pandora-net]

  pandora-start:
    image: registry.example.com/pandora/pandora-start:${TAG:-1.0.0}
    restart: always
    environment:
      <<: *common-env
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/pandora
      SPRING_DATA_REDIS_HOST: redis
      SPRING_KAFKA_BOOTSTRAP_SERVERS: kafka:9092
      SPRING_DATA_MONGODB_URI: mongodb://mongo:27017/pandora
      AUTH_SERVER_URL: http://pandora-auth:9100
    ports: ["8080:8080"]
    depends_on: [mysql, redis, kafka, mongo, pandora-auth]
    networks: [pandora-net]
    deploy:
      replicas: 2
      resources:
        limits: { cpus: "2", memory: 2G }

networks:
  pandora-net: { driver: bridge }

volumes:
  mysql_data: {}
  redis_data: {}
  kafka_data: {}
  mongo_data: {}
  minio_data: {}
```

一键启动：

```bash
TAG=1.0.0 docker compose -f deploy/compose/docker-compose.yml up -d
```

---

## 五、GitHub Actions 流水线

`.github/workflows/ci.yml`：

```yaml
name: CI

on:
  push:
    branches: [main, develop]
  pull_request:
    branches: [main, develop]

env:
  REGISTRY: registry.example.com
  IMAGE_PREFIX: pandora

jobs:
  # ================= 静态检查 + 单测 =================
  test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
        with: { fetch-depth: 0 }

      - name: Setup JDK 25
        uses: actions/setup-java@v4
        with:
          distribution: temurin
          java-version: "25"
          cache: maven

      - name: Lint (Checkstyle + Spotbugs)
        run: mvn -B checkstyle:check spotbugs:check

      - name: Run tests
        run: mvn -B test

      - name: Upload coverage
        uses: actions/upload-artifact@v4
        with:
          name: jacoco
          path: '**/target/site/jacoco/*'

  # ================= 构建 + 推镜像 =================
  build-image:
    needs: test
    if: github.event_name == 'push'
    runs-on: ubuntu-latest
    strategy:
      matrix:
        module: [pandora-auth, pandora-start, pandora-gateway, pandora-service-user]
    steps:
      - uses: actions/checkout@v4

      - name: Setup JDK 25
        uses: actions/setup-java@v4
        with: { distribution: temurin, java-version: "25", cache: maven }

      - name: Login to registry
        uses: docker/login-action@v3
        with:
          registry: ${{ env.REGISTRY }}
          username: ${{ secrets.REGISTRY_USER }}
          password: ${{ secrets.REGISTRY_TOKEN }}

      - name: Compute tag
        id: meta
        run: |
          SHA=$(git rev-parse --short HEAD)
          DATE=$(date +%Y%m%d)
          VERSION=$(mvn -q help:evaluate -Dexpression=project.version -DforceStdout)
          echo "tag=${VERSION}-${DATE}-${SHA}" >> $GITHUB_OUTPUT

      - name: Build & push
        uses: docker/build-push-action@v5
        with:
          context: .
          file: deploy/docker/Dockerfile
          build-args: MODULE=${{ matrix.module }}
          push: true
          tags: |
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.module }}:${{ steps.meta.outputs.tag }}
            ${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.module }}:latest
          cache-from: type=registry,ref=${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.module }}:buildcache
          cache-to: type=registry,ref=${{ env.REGISTRY }}/${{ env.IMAGE_PREFIX }}/${{ matrix.module }}:buildcache,mode=max

  # ================= 部署到生产 =================
  deploy:
    needs: build-image
    if: github.ref == 'refs/heads/main'
    runs-on: ubuntu-latest
    environment: production
    steps:
      - uses: actions/checkout@v4

      - name: SSH 部署到服务器
        uses: appleboy/ssh-action@v1.0.3
        with:
          host: ${{ secrets.PROD_HOST }}
          username: ${{ secrets.PROD_USER }}
          key: ${{ secrets.PROD_SSH_KEY }}
          script: |
            cd /opt/pandora-arch
            export TAG=latest
            docker compose pull
            docker compose up -d --no-deps --force-recreate \
              pandora-auth pandora-start pandora-gateway pandora-service-user
            sleep 20
            curl -fs http://localhost:8080/actuator/health || exit 1

      - name: 通知钉钉/飞书
        if: always()
        run: |
          curl -X POST ${{ secrets.DINGTALK_WEBHOOK }} \
            -H "Content-Type: application/json" \
            -d "{\"msgtype\":\"markdown\",\"markdown\":{\"title\":\"部署结果\",\"text\":\"pandora-arch ${{ github.ref_name }} 部署: ${{ job.status }}\"}}"
```

---

## 六、GitLab CI（等价流水线）

`.gitlab-ci.yml`：

```yaml
stages: [test, build, deploy]

variables:
  MAVEN_CLI_OPTS: "-B -ntp -s ci_settings.xml"
  DOCKER_DRIVER: overlay2
  REGISTRY: registry.example.com

.test-template: &test
  stage: test
  image: maven:3.9-eclipse-temurin-25
  cache:
    key: $CI_COMMIT_REF_SLUG
    paths: [.m2/repository]
  script: mvn $MAVEN_CLI_OPTS test

unit-test:
  <<: *test
  coverage: '/Total.*?([0-9]{1,3})%/'

build-image:
  stage: build
  image: docker:24
  services: [docker:24-dind]
  parallel:
    matrix:
      - MODULE: [pandora-auth, pandora-start, pandora-gateway, pandora-service-user]
  before_script:
    - docker login -u $REGISTRY_USER -p $REGISTRY_PASS $REGISTRY
  script:
    - TAG=$(cat pom.xml | grep -m1 '<version>' | sed 's/.*>\(.*\)<.*/\1/')-$CI_COMMIT_SHORT_SHA
    - docker build --build-arg MODULE=$MODULE -t $REGISTRY/pandora/$MODULE:$TAG -f deploy/docker/Dockerfile .
    - docker push $REGISTRY/pandora/$MODULE:$TAG
  only: [main, develop]

deploy-prod:
  stage: deploy
  image: alpine:3.20
  before_script:
    - apk add --no-cache openssh-client
    - eval $(ssh-agent -s) && echo "$PROD_SSH_KEY" | ssh-add -
  script:
    - ssh -o StrictHostKeyChecking=no $PROD_USER@$PROD_HOST "cd /opt/pandora-arch && docker compose pull && docker compose up -d"
  environment: { name: production, url: https://api.example.com }
  only: [main]
  when: manual
```

---

## 七、Jenkinsfile（声明式）

`Jenkinsfile`：

```groovy
pipeline {
  agent any
  tools { jdk 'JDK25'; maven 'Maven3.9' }

  environment {
    REGISTRY     = 'registry.example.com'
    IMAGE_PREFIX = 'pandora'
    TAG          = "${env.BUILD_NUMBER}-${env.GIT_COMMIT.take(7)}"
  }

  options {
    timeout(time: 40, unit: 'MINUTES')
    buildDiscarder(logRotator(numToKeepStr: '20'))
  }

  stages {
    stage('Checkout') { steps { checkout scm } }

    stage('Test & SonarQube') {
      steps {
        sh 'mvn -B clean verify sonar:sonar -Dsonar.projectKey=pandora-arch'
      }
    }

    stage('Build Images (matrix)') {
      matrix {
        axes { axis { name 'MODULE'; values 'pandora-auth','pandora-start','pandora-gateway','pandora-service-user' } }
        stages {
          stage('Image') {
            steps {
              sh """
                docker build --build-arg MODULE=$MODULE \\
                  -t $REGISTRY/$IMAGE_PREFIX/$MODULE:$TAG \\
                  -f deploy/docker/Dockerfile .
                docker push $REGISTRY/$IMAGE_PREFIX/$MODULE:$TAG
              """
            }
          }
        }
      }
    }

    stage('Deploy') {
      when { branch 'main' }
      steps {
        input message: '确认发布到生产？', ok: '发'
        sshagent(credentials: ['prod-ssh']) {
          sh """
            ssh $PROD_USER@$PROD_HOST '
              cd /opt/pandora-arch &&
              TAG=$TAG docker compose pull &&
              TAG=$TAG docker compose up -d &&
              sleep 30 && curl -fs http://localhost:8080/actuator/health'
          """
        }
      }
    }
  }

  post {
    success { dingTalk robot: 'devops', text: "✅ ${env.JOB_NAME} #${env.BUILD_NUMBER} 成功" }
    failure { dingTalk robot: 'devops', text: "❌ ${env.JOB_NAME} #${env.BUILD_NUMBER} 失败" }
  }
}
```

---

## 八、Kubernetes 部署（ArgoCD + Helm）

### 8.1 Helm Chart 结构

```
deploy/helm/pandora-arch/
├── Chart.yaml
├── values.yaml
├── values-prod.yaml
└── templates/
    ├── deployment.yaml
    ├── service.yaml
    ├── ingress.yaml
    ├── configmap.yaml
    └── hpa.yaml
```

### 8.2 关键片段

`templates/deployment.yaml`：

```yaml
apiVersion: apps/v1
kind: Deployment
metadata: { name: {{ .Values.name }} }
spec:
  replicas: {{ .Values.replicaCount }}
  strategy:
    type: RollingUpdate
    rollingUpdate: { maxSurge: 1, maxUnavailable: 0 }
  selector: { matchLabels: { app: {{ .Values.name }} } }
  template:
    metadata: { labels: { app: {{ .Values.name }} } }
    spec:
      containers:
        - name: app
          image: "{{ .Values.image.registry }}/{{ .Values.image.repo }}:{{ .Values.image.tag }}"
          ports: [{ containerPort: 8080 }]
          envFrom:
            - configMapRef: { name: {{ .Values.name }}-config }
            - secretRef:    { name: {{ .Values.name }}-secret }
          livenessProbe:
            httpGet: { path: /actuator/health/liveness, port: 8080 }
            initialDelaySeconds: 60
          readinessProbe:
            httpGet: { path: /actuator/health/readiness, port: 8080 }
            initialDelaySeconds: 30
          resources:
            limits:   { cpu: "2", memory: "2Gi" }
            requests: { cpu: "500m", memory: "1Gi" }
```

### 8.3 ArgoCD Application（GitOps）

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Application
metadata: { name: pandora-arch, namespace: argocd }
spec:
  project: default
  source:
    repoURL: git@github.com:example/pandora-arch.git
    path: deploy/helm/pandora-arch
    targetRevision: main
    helm: { valueFiles: [values-prod.yaml] }
  destination:
    server: https://kubernetes.default.svc
    namespace: pandora
  syncPolicy:
    automated: { prune: true, selfHeal: true }
    syncOptions: [CreateNamespace=true]
```

流程：`git push → GitHub Actions build image & 更新 values.yaml tag → ArgoCD 检测变更 → 自动同步到集群`。

---

## 九、回滚与灰度

| 场景 | 操作 |
|------|------|
| Docker Compose 回滚 | `TAG=1.0.0-20260416-xxx docker compose up -d` |
| K8s Deployment 回滚 | `kubectl rollout undo deployment/pandora-start` |
| Helm 回滚 | `helm rollback pandora-arch <revision>` |
| Argo 回滚 | ArgoCD UI → History → Rollback |
| 灰度发布 | Istio VirtualService + 按 Header 路由；或 Kubernetes Deployment maxSurge + 金丝雀 |

---

## 十、生产环境 Checklist

- [ ] Git：受保护分支、强制 PR + 审批、禁 force push
- [ ] Secrets：用 Vault / AWS SM / GitHub Secrets，严禁硬编码
- [ ] 镜像：SBOM（syft）+ 漏扫（trivy）+ 签名（cosign）
- [ ] 日志：集中到 ELK / Loki，保留 ≥ 30 天
- [ ] 监控：Prometheus + Grafana + Alertmanager，核心接口 RT/错误率告警
- [ ] 追踪：OpenTelemetry + Jaeger/Tempo
- [ ] 备份：MySQL 每日物理备份 + binlog 增量；Redis RDB + AOF
- [ ] 安全：HTTPS、WAF、防重放、权限最小化
- [ ] 容量：HPA（CPU 70%）+ 压测报告
- [ ] DR：每季度做一次机房故障演练

---

> **作者**：vahoa  
> **日期**：2026 年  
> **作品**：pandora-arch · DDD 架构底座  
> **版权**：© 2026 vahoa. All rights reserved.
