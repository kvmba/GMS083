# BeiDou Docker 支持

使用**单容器多阶段构建**：编译前端、编译后端都在 Docker 镜像构建阶段完成，最终只生成一个运行镜像。

- `Dockerfile`：多阶段构建镜像，基于 `container-registry.oracle.com/graalvm/jdk:21-ol9`。
- `entrypoint.sh`：运行容器启动脚本，负责检查 jar、创建日志目录并启动服务。
- `application.docker.yml`：Docker 外部配置模板，复制为镜像内 `/app/application.yml`，关键信息通过环境变量注入。
- 根目录 `docker-compose.yml`：只定义一个 `beidou` 服务。

## 快速开始

可先复制环境变量模板（可选）：

```bash
cp .env.example .env
# 然后按需修改 .env
```

### 构建并启动（自动拉取远端最新代码）

```bash
git pull && GIT_COMMIT_SHA=$(git rev-parse HEAD) docker compose up -d --build
```

一条命令完成。原理：

1. `git pull` 先更新构建机本地 checkout——`COPY . .` 层的输入变了，后续编译层缓存全部失效；
2. `GIT_COMMIT_SHA` 以环境变量传入 compose（优先级高于 `.env`），Dockerfile 里 `git fetch` + `git checkout <该提交>`，保证容器内代码与远端完全一致；
3. 之后 `yarn build` → `mvn package` 全量编译，结果必然是最新分支代码。

> 远端有新提交而构建机本地没更新时，`COPY` 层不会变化、后面会走旧缓存——所以第 1 步的 `git pull` 是必须的，不能省。

`docker compose build` 阶段会自动完成：

1. `git fetch` + `git checkout <远端最新提交>` 同步到最新代码；
2. 进入 `gms-ui` 执行 `yarn install && yarn build`；
3. 把 `gms-ui/dist` 复制到 `gms-server/src/main/resources/static`；
4. 进入 `gms-server` 执行 `mvn clean package -DskipTests`；
5. 把 `target/BeiDou.jar` 复制为镜像内 `/app/BeiDou.jar`；
6. 把 `docker/application.docker.yml` 复制为镜像内 `/app/application.yml`。

> 注意：直接 `docker compose build`（不带 `GIT_COMMIT_SHA`）会构建失败，这是有意为之——防止静默走缓存构建旧代码。

### 只启动已有镜像（不重新编译）

```bash
docker compose up -d
```

查看日志：

```bash
docker compose logs -f beidou
```

停止：

```bash
docker compose down
```

## 挂载路径

运行期**挂载整个仓库根目录**到容器 `/workspace`：

```yaml
- .:/workspace
```

这样 `gms-server/wz`、`gms-server/scripts`、`gms-server/handbook`、`gms-server/logs` 全部来自宿主机，**不复制进镜像**。

编译产物 `BeiDou.jar` 和 `application.yml` 放在镜像内 `/app`，不会被根目录挂载覆盖。

## 数据库配置

可通过环境变量覆盖：

| 环境变量 | 默认值 | 说明 |
| --- | --- | --- |
| `DB_URL` | `jdbc:mysql://127.0.0.1:3306/GMS083?...` | JDBC 连接串 |
| `DB_USERNAME` | `gms` | 数据库用户名 |
| `DB_PASSWORD` | `gms` | 数据库密码 |
| `JWT_SECRET` | `please-change-me-in-production` | JWT 密钥，生产必须修改 |
| `SERVER_PORT` | `8686` | REST 端口 |
| `GMS_LOGIN_PORT` | `8484` | 游戏登录端口 |
| `GMS_LANGUAGE` | `zh-CN` | 语言 |
| `GMS_WAN_HOST` / `GMS_LAN_HOST` / `GMS_LOCALHOST` | `127.0.0.1` | 游戏服 IP |
| `SWAGGER_ENABLED` | `false` | 是否开启 Swagger |
| `APP_VUE_URL` | `http://localhost:8787` | 前端地址（CORS 用） |
| `JWT_DURATION` | `1800000` | JWT 过期毫秒数 |

更多可选变量（上传大小、限流等）见 `.env.example`。

示例：

```bash
DB_PASSWORD='your-db-password' JWT_SECRET='your-jwt-secret' git pull && GIT_COMMIT_SHA=$(git rev-parse HEAD) docker compose up -d --build
```

## 代理配置

代理地址统一在仓库根目录 `.env` 中配置（`HTTP_PROXY` / `HTTPS_PROXY` / `NO_PROXY`），
`docker compose` 构建时自动读取并作为 build args 传入：

- `HTTP_PROXY` / `HTTPS_PROXY`：构建阶段下载 Maven/Node/Git 等资源的代理地址
- `NO_PROXY` / `no_proxy`：默认 `localhost,127.0.0.1`

运行容器不注入代理，服务启动后直连网络/数据库。

> 注意：`docker pull` 拉取基础镜像属于 Docker daemon 的网络，不归容器内环境变量管。如果拉镜像也需要代理，要在 Docker daemon 层配置代理，例如 `~/.docker/config.json` 或 systemd 的 `docker.service.d/http-proxy.conf`。

## 端口

默认映射：

- `8686`：REST / Swagger
- `8484`：游戏登录
- `7575-7600`：频道端口范围

如需修改，通过环境变量传入，例如：

```bash
SERVER_PORT=8888 GMS_LOGIN_PORT=8585 CHANNEL_PORT_RANGE=7575-7580 git pull && GIT_COMMIT_SHA=$(git rev-parse HEAD) docker compose up -d --build
```

## 注意事项

- 首次构建需要联网下载 Maven 依赖、Node 依赖和基础镜像，耗时较长。
- 如果拉取 `container-registry.oracle.com/graalvm/jdk:21-ol9` 提示需要登录，先执行 `docker login container-registry.oracle.com`。
- 使用 `21-ol9` 而不是 `21`，是因为最新 `21` 标签可能基于 Oracle Linux 10，要求 CPU 支持 `x86-64-v3`；`21-ol9` 基于 Oracle Linux 9，兼容旧 CPU。
- 构建阶段会执行 `git pull --rebase --autostash`，如果仓库使用私有凭据，请确保宿主 `.git/config` 中的凭据已配置。
- 镜像内已经包含编译好的 `/app/BeiDou.jar` 和 `/app/application.yml`；根目录挂载不会覆盖 `/app`。
- 如果修改了 `docker/Dockerfile`，需要重新构建镜像：`docker compose build beidou`。
