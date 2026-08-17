# syntax=docker/dockerfile:1

# ========== 构建阶段 ==========
# 使用 GraalVM JDK 21（Oracle Linux 9，兼容旧 CPU）
FROM container-registry.oracle.com/graalvm/jdk:21-ol9 AS build

# 构建时下载资源使用的 HTTP/HTTPS 代理(按需通过 build args 传入)
ARG HTTP_PROXY=
ARG HTTPS_PROXY=
ARG NO_PROXY=localhost,127.0.0.1
ENV HTTP_PROXY=${HTTP_PROXY} \
    HTTPS_PROXY=${HTTPS_PROXY} \
    http_proxy=${HTTP_PROXY} \
    https_proxy=${HTTPS_PROXY} \
    NO_PROXY=${NO_PROXY} \
    no_proxy=${NO_PROXY}

# 安装构建工具：git / curl / tar / gzip / bash
RUN set -eux; \
    if command -v microdnf >/dev/null 2>&1; then \
        microdnf install -y bash git curl tar gzip ca-certificates; \
        microdnf clean all; \
    elif command -v dnf >/dev/null 2>&1; then \
        dnf install -y bash git curl tar gzip ca-certificates; \
        dnf clean all; \
    elif command -v apt-get >/dev/null 2>&1; then \
        apt-get update && apt-get install -y --no-install-recommends bash git curl ca-certificates tar gzip; \
        rm -rf /var/lib/apt/lists/*; \
    else \
        echo "Unsupported base image package manager" >&2; \
        exit 1; \
    fi

# 安装 Maven
ARG MAVEN_VERSION=3.9.9
RUN set -eux; \
    curl -fsSL "https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz" \
        | tar -xz -C /opt; \
    ln -s "/opt/apache-maven-${MAVEN_VERSION}/bin/mvn" /usr/local/bin/mvn

# 安装 Node.js LTS + Yarn（gms-ui 要求 Node 20.15.0）
ARG NODE_VERSION=20.15.0
ARG TARGETARCH
RUN set -eux; \
    ARCH="${TARGETARCH:-$(uname -m)}"; \
    case "$ARCH" in \
        x86_64|amd64) NODE_ARCH=x64 ;; \
        aarch64|arm64) NODE_ARCH=arm64 ;; \
        *) echo "Unsupported architecture: $ARCH" >&2; exit 1 ;; \
    esac; \
    curl -fsSL "https://nodejs.org/dist/v${NODE_VERSION}/node-v${NODE_VERSION}-linux-${NODE_ARCH}.tar.gz" \
        | tar -xz -C /opt; \
    ln -s "/opt/node-v${NODE_VERSION}-linux-${NODE_ARCH}/bin/node" /usr/local/bin/node; \
    ln -s "/opt/node-v${NODE_VERSION}-linux-${NODE_ARCH}/bin/npm" /usr/local/bin/npm; \
    ln -s "/opt/node-v${NODE_VERSION}-linux-${NODE_ARCH}/bin/npx" /usr/local/bin/npx; \
    npm install -g yarn@1.22.22; \
    ln -sf "$(npm prefix -g)/bin/yarn" /usr/local/bin/yarn; \
    ln -sf "$(npm prefix -g)/bin/yarnpkg" /usr/local/bin/yarnpkg

# Maven 仓库缓存 + Maven/JVM 代理(代理地址通过构建参数注入,未设置时直连)
ENV MAVEN_OPTS="-Dmaven.repo.local=/root/.m2/repository"
ENV YARN_CACHE_FOLDER=/usr/local/share/.cache/yarn

WORKDIR /workspace

# 复制整个仓库（包含 .git，用于构建期同步到远端指定提交）
COPY . .

# 每次构建都同步到远端最新提交：
# GIT_COMMIT_SHA 由 build.sh 通过 ls-remote 获取并传入，SHA 变化会使后续层缓存全部失效，
# 避免 COPY . . 未变时 RUN 层走缓存、跳过拉取导致构建旧代码
ARG GIT_COMMIT_SHA=
RUN test -n "${GIT_COMMIT_SHA}" || { echo "[build] 缺少 GIT_COMMIT_SHA，请使用 docker/build.sh 构建" >&2; exit 1; }; \
    git config --global --add safe.directory /workspace \
    && git fetch --prune origin \
    && git checkout -f "${GIT_COMMIT_SHA}" \
    && git log -1 --oneline

# 编译前端（install 需要 devDependencies，build 时再切 production）
WORKDIR /workspace/gms-ui
RUN yarn install --frozen-lockfile \
    && NODE_ENV=production yarn build

# 把前端产物复制到服务端静态目录
RUN rm -rf /workspace/gms-server/src/main/resources/static \
    && mkdir -p /workspace/gms-server/src/main/resources/static \
    && cp -a /workspace/gms-ui/dist/. /workspace/gms-server/src/main/resources/static/

# 编译后端 jar
WORKDIR /workspace/gms-server
RUN mvn clean package -DskipTests

# 准备运行目录：jar + 外部配置 + 日志目录
RUN cp -f target/BeiDou.jar /workspace/gms-server/BeiDou.jar \
    && cp -f /workspace/docker/application.docker.yml /workspace/gms-server/application.yml \
    && mkdir -p /workspace/gms-server/logs

# ========== 运行阶段 ==========
FROM container-registry.oracle.com/graalvm/jdk:21-ol9

WORKDIR /workspace/gms-server

# 编译产物放在 /app，避免被运行期根目录挂载覆盖
RUN mkdir -p /app
COPY --from=build /workspace/gms-server/BeiDou.jar /app/BeiDou.jar
COPY --from=build /workspace/gms-server/application.yml /app/application.yml
RUN mkdir -p /workspace/gms-server/logs

# 启动脚本
COPY docker/entrypoint.sh /usr/local/bin/beidou-entrypoint.sh
RUN chmod +x /usr/local/bin/beidou-entrypoint.sh

EXPOSE 8686 8484
ENTRYPOINT ["/usr/local/bin/beidou-entrypoint.sh"]
