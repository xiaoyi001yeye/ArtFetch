# ArtFetch GitHub Actions 离线制品发布与服务器安装升级设计

状态：发布制品与服务器安装升级的唯一事实来源

更新日期：2026-06-01

本文档合并并取代 ArtFetch 旧的发布制品、服务器构建、registry 部署、GitHub Actions SSH 部署等设计口径。其它文档如与本文冲突，以本文为准，并应立即调整。

## 1. 核心决策

ArtFetch 使用 GitHub Actions 构建发布制品，目标服务器只下载 GitHub Release 附件并在本机 `docker load` 后启动服务。

目标服务器禁止执行：

- `npm run build`
- `mvn package`
- `docker build`
- `docker pull`
- 从 Docker Hub、GHCR 或其它 registry 拉取运行镜像
- 从源码仓库拉取代码后现场构建

目标服务器只允许消费：

- GitHub 最新正式 Release 的附件
- `release-manifest.json`
- `artfetch-deploy-<version>.tgz`
- `artfetch-deploy-<version>.tgz.sha256`
- `install-or-upgrade-latest.sh`

GitHub Actions 可以访问 Docker Hub、Maven、npm 等构建依赖源；目标服务器不能依赖这些源。

## 2. 版本规则

版本号统一使用大写 `V` 开头的语义化版本：

```text
V1.0.0
V1.0.1
V1.1.0
V2.0.0
```

当前版本从 `V1.0.0` 开始。

版本事实来源是 Flyway 最新迁移文件。发布版本必须等于仓库中最新 Flyway 迁移版本：

```text
backend/src/main/resources/db/migration/V1.0.0__baseline_artfetch_schema.sql
```

同一版本必须贯穿：

- Flyway 迁移版本：`V1.0.0__baseline_artfetch_schema.sql`
- Git tag：`release/V1.0.0`
- GitHub Release：`V1.0.0`
- 部署包：`artfetch-deploy-V1.0.0.tgz`
- 自有服务镜像版本 tag：`artfetch-backend:V1.0.0`
- Manifest：`"version": "V1.0.0"`

任何 `YYYY.MM.DD.N` 日期版本号不再用于新发布链路。

## 3. 数据库迁移策略

ArtFetch 引入 Flyway 管理数据库结构。

新库：

- Flyway 执行 `V1.0.0__baseline_artfetch_schema.sql` 创建完整 schema。

旧生产库：

- 首次引入 Flyway 时启用 baseline。
- `spring.flyway.baseline-on-migrate=true`
- `spring.flyway.baseline-version=1.0.0`
- Flyway 记录旧库已经处于 `V1.0.0`。

生产环境 Hibernate 不再负责自动改表：

- 生产默认 `spring.jpa.hibernate.ddl-auto=validate`
- 本地开发是否允许 `update` 由环境配置另行决定

后续任何 schema 变更都必须新增 Flyway 迁移文件，例如：

```text
V1.0.1__add_xxx.sql
V1.1.0__change_yyy.sql
```

发布 Actions 必须校验输入版本等于最新 Flyway 版本，否则失败。

## 4. GitHub Actions 流程

发布链路只保留两个 workflow：

- `CI`：用于 PR 和普通 push 校验。
- `Release`：手动触发，构建离线制品并创建 GitHub Release。

不再保留：

- `Package` 候选 artifact 晋升流程
- `Deploy Production` SSH 主动部署流程
- GitHub Actions 连接生产服务器的生产部署路径

### 4.1 CI Workflow

触发：

- `pull_request`
- `push`

职责：

1. 后端测试。
2. 前端构建。
3. Docker build 验证 backend、frontend、jupyter 镜像可构建。
4. 不上传 Release 附件。
5. 不部署服务器。

### 4.2 Release Workflow

触发：

- `workflow_dispatch`

输入：

- `version`，例如 `V1.0.0`
- `releaseNotes`

职责：

1. 校验版本格式为 `^V[0-9]+\.[0-9]+\.[0-9]+$`。
2. 找到最新 Flyway 迁移版本，确认等于输入版本。
3. 后端测试。
4. 前端构建。
5. 构建自有服务镜像。
6. 拉取运行所需外部镜像。
7. 为每个自有服务镜像同时打版本 tag 和 Git SHA tag。
8. `docker save | gzip` 导出所有运行镜像 tar。
9. 生成 `release-manifest.json`。
10. 生成 `artfetch-deploy-<version>.tgz` 和 SHA256。
11. 创建或校验 Git tag `release/<version>`。
12. 创建 GitHub 正式 Release 并上传附件。

Release workflow 不连接生产服务器。

## 5. 镜像制品范围

Release 包必须包含目标服务器运行所需的全部 Docker 镜像 tar。目标服务器不能 `docker pull` 补齐缺失镜像。

当前运行镜像包括：

- `artfetch-backend`
- `artfetch-frontend`
- `artfetch-jupyter`
- `postgres:16-alpine`

自有服务镜像同时保留两个 tag：

```text
artfetch-backend:V1.0.0
artfetch-backend:sha-<full-git-sha>
artfetch-frontend:V1.0.0
artfetch-frontend:sha-<full-git-sha>
artfetch-jupyter:V1.0.0
artfetch-jupyter:sha-<full-git-sha>
```

部署时 `.env.release` 使用版本 tag，manifest 同时记录版本 tag、SHA tag、image id 和 tar SHA256。

外部镜像不重新命名为 ArtFetch 自有镜像；保留原始 ref，例如：

```text
postgres:16-alpine
```

但该镜像 tar 必须包含在部署包中，并在 manifest 中记录。

## 6. Release 附件

每个正式 GitHub Release 上传这些顶层附件：

```text
release-manifest.json
artfetch-deploy-V1.0.0.tgz
artfetch-deploy-V1.0.0.tgz.sha256
install-or-upgrade-latest.sh
```

`install-or-upgrade-latest.sh` 是用户在目标服务器直接运行的入口脚本，必须作为 Release 顶层附件暴露，方便复制、下载和审计。

部署包内部结构：

```text
artfetch-deploy-V1.0.0/
├── docker-compose.prod.yml
├── .env.example
├── release-manifest.json
├── images/
│   ├── artfetch-backend-V1.0.0.tar.gz
│   ├── artfetch-backend-V1.0.0.tar.gz.sha256
│   ├── artfetch-frontend-V1.0.0.tar.gz
│   ├── artfetch-frontend-V1.0.0.tar.gz.sha256
│   ├── artfetch-jupyter-V1.0.0.tar.gz
│   ├── artfetch-jupyter-V1.0.0.tar.gz.sha256
│   ├── postgres-16-alpine.tar.gz
│   └── postgres-16-alpine.tar.gz.sha256
└── scripts/
    ├── artfetch-install-or-upgrade.sh
    └── artfetch-clean-failed-install.sh
```

部署包和附件禁止包含：

- `.env`
- 数据库 dump
- 生产密码
- 雅昌 Cookie、账号、密码
- 对象存储 Access Key、Secret Key
- SSH 私钥
- GitHub token

## 7. Manifest Schema

`release-manifest.json` 是发布、安装、升级、排障和审计的事实来源。

示例：

```json
{
  "app": "artfetch",
  "version": "V1.0.0",
  "gitSha": "abcdef1234567890abcdef1234567890abcdef12",
  "gitShortSha": "abcdef1",
  "builtAt": "2026-06-01T08:00:00Z",
  "releasedAt": "2026-06-01T09:00:00Z",
  "flywayVersion": "V1.0.0",
  "images": {
    "backend": {
      "service": "backend",
      "versionTag": "artfetch-backend:V1.0.0",
      "shaTag": "artfetch-backend:sha-abcdef1234567890abcdef1234567890abcdef12",
      "imageId": "sha256:<docker-image-id>",
      "tar": "images/artfetch-backend-V1.0.0.tar.gz",
      "tarSha256": "<tar-file-sha256>"
    },
    "frontend": {
      "service": "frontend",
      "versionTag": "artfetch-frontend:V1.0.0",
      "shaTag": "artfetch-frontend:sha-abcdef1234567890abcdef1234567890abcdef12",
      "imageId": "sha256:<docker-image-id>",
      "tar": "images/artfetch-frontend-V1.0.0.tar.gz",
      "tarSha256": "<tar-file-sha256>"
    },
    "jupyter": {
      "service": "jupyter",
      "versionTag": "artfetch-jupyter:V1.0.0",
      "shaTag": "artfetch-jupyter:sha-abcdef1234567890abcdef1234567890abcdef12",
      "imageId": "sha256:<docker-image-id>",
      "tar": "images/artfetch-jupyter-V1.0.0.tar.gz",
      "tarSha256": "<tar-file-sha256>"
    },
    "postgres": {
      "service": "postgres",
      "ref": "postgres:16-alpine",
      "imageId": "sha256:<docker-image-id>",
      "tar": "images/postgres-16-alpine.tar.gz",
      "tarSha256": "<tar-file-sha256>"
    }
  },
  "compose": {
    "file": "docker-compose.prod.yml",
    "sha256": "<compose-file-sha256>"
  },
  "build": {
    "backendTest": "passed",
    "frontendBuild": "passed",
    "imageBuild": "passed",
    "imageExport": "passed"
  }
}
```

校验规则：

- `app` 必须等于 `artfetch`。
- `version` 必须匹配 `^V[0-9]+\.[0-9]+\.[0-9]+$`。
- `version` 必须等于 `flywayVersion`。
- Git tag `release/<version>` 必须指向 `gitSha`。
- 每个 `images.*.tar` 必须存在于部署包内。
- 每个镜像 tar 的实际 SHA256 必须等于 `tarSha256`。
- `compose.sha256` 必须等于部署包内 `docker-compose.prod.yml` 的实际 SHA256。

## 8. Compose 生产形态

`docker-compose.prod.yml` 必须通过 `.env.release` 指定所有运行镜像，包括外部镜像。

示例：

```yaml
services:
  postgres:
    image: ${POSTGRES_IMAGE:?POSTGRES_IMAGE is required}

  backend:
    image: ${ARTFETCH_BACKEND_IMAGE:?ARTFETCH_BACKEND_IMAGE is required}

  frontend:
    image: ${ARTFETCH_FRONTEND_IMAGE:?ARTFETCH_FRONTEND_IMAGE is required}

  jupyter:
    image: ${ARTFETCH_JUPYTER_IMAGE:?ARTFETCH_JUPYTER_IMAGE is required}
```

部署脚本从 manifest 生成 `.env.release`：

```env
POSTGRES_IMAGE=postgres:16-alpine
ARTFETCH_BACKEND_IMAGE=artfetch-backend:V1.0.0
ARTFETCH_FRONTEND_IMAGE=artfetch-frontend:V1.0.0
ARTFETCH_JUPYTER_IMAGE=artfetch-jupyter:V1.0.0
```

脚本必须先 `docker load` 所有镜像，再执行 `docker image inspect`，确认目标服务器本地已经具备全部镜像。

## 9. 服务器入口脚本

`install-or-upgrade-latest.sh` 是唯一推荐入口。它同时支持首次安装和后续升级。

默认项目目录：

```text
/opt/artfetch
```

可通过环境变量覆盖：

```bash
ARTFETCH_PROJECT_DIR=/data/artfetch bash install-or-upgrade-latest.sh
```

脚本依赖：

- `curl`
- `tar`
- `sha256sum`
- `docker`
- `docker compose`
- `python3`
- `awk`
- `sed`
- `grep`

脚本不依赖 GitHub CLI `gh`。

### 9.1 下载最新 Release

默认只取 GitHub 最新正式 Release：

```text
https://api.github.com/repos/<owner>/<repo>/releases/latest
```

Draft 和 prerelease 不作为默认部署来源。

公开仓库无需 token。私有仓库可使用：

```bash
GITHUB_TOKEN=<token> bash install-or-upgrade-latest.sh
```

脚本下载：

- `release-manifest.json`
- `artfetch-deploy-<version>.tgz`
- `artfetch-deploy-<version>.tgz.sha256`

### 9.2 首次安装

如果目标目录不存在，脚本创建：

```text
/opt/artfetch
├── .env
├── .env.release
├── docker-compose.prod.yml
├── release-manifest.json
├── backend/logs/
├── storage/original-images/
├── releases/
└── backups/
```

如果 `.env` 不存在，脚本从 `.env.example` 创建并自动生成强随机值：

- `POSTGRES_PASSWORD`
- `ARTFETCH_ADMIN_PASSWORD`
- `ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY`

脚本不得在终端、日志或 GitHub 输出中打印这些真实值。

雅昌 Cookie、雅昌账号密码、对象存储业务配置不自动猜测。用户可后续编辑 `.env` 后重启服务。

### 9.3 升级

升级步骤：

1. 下载最新正式 Release 附件。
2. 校验部署包 SHA256。
3. 解包并校验 manifest。
4. 校验每个镜像 tar SHA256。
5. `docker load` 所有镜像。
6. `docker image inspect` 所有 manifest 中声明的镜像。
7. 检查运行中任务，发现采集或上传任务正在运行则停止升级。
8. 备份数据库。
9. 替换 `docker-compose.prod.yml`、`.env.release`、`release-manifest.json`。
10. `docker compose --env-file .env --env-file .env.release -f docker-compose.prod.yml up -d`。
11. 验证容器状态、后端健康检查、前端入口、鉴权 API。

升级过程不修改用户已有 `.env` 的密钥值。

### 9.4 失败处理

安装或升级失败时不做自动回滚。

失败处理策略：

- 清理本次失败解包目录。
- 清理候选 `.env.release` 和候选 Compose 文件。
- 停止并移除本次未成功启用的 ArtFetch 容器。
- 保留 `.env`、数据库备份、本地图片和日志。
- 用户再次运行 `install-or-upgrade-latest.sh` 重新安装或升级。

如用户明确需要完全重装并删除数据，必须显式设置：

```bash
ARTFETCH_WIPE_DATA=1 bash install-or-upgrade-latest.sh
```

只有在该变量为 `1` 时，脚本才允许删除 PostgreSQL volume、本地图片和日志等持久化数据。

## 10. 安全要求

- GitHub Release 附件不得包含密钥。
- `.env` 只存在于目标服务器。
- `.env` 和 `.env.release` 权限必须为 `600`。
- 脚本日志只输出环境变量是否存在，不输出真实值。
- 服务器默认只暴露前端入口。
- PostgreSQL、后端、Jupyter 默认绑定 `127.0.0.1` 或仅在 Docker 网络内部访问。
- 本设计不新增 ArtFetch 应用内 API、页面或按钮，因此不需要新增 Sa-Token 权限码。

如果后续新增发布管理 API、内部运维 API 或页面，必须按 `AGENTS.md` 的授权要求补齐权限码、后端 `@SaCheckPermission`、前端权限控制、审计日志和设计文档更新。

## 11. 成功标准

Release 成功标准：

- 输入版本为 `Vx.y.z`。
- 输入版本等于最新 Flyway 迁移版本。
- 后端测试通过。
- 前端构建通过。
- 所有自有服务镜像构建成功。
- 所有运行镜像成功导出 tar。
- manifest 中每个镜像 tar SHA256 可校验。
- Git tag `release/<version>` 创建成功。
- GitHub Release 附件完整。

服务器安装/升级成功标准：

- 从最新正式 Release 下载附件成功。
- 部署包 SHA256 校验成功。
- 所有镜像 tar SHA256 校验成功。
- 所有镜像 `docker load` 成功。
- `.env.release` 包含所有运行镜像变量。
- `docker compose config` 通过。
- `postgres`、`backend`、`frontend`、`jupyter` 容器状态稳定。
- 后端 `/actuator/health` 返回 UP。
- 前端入口返回 2xx/3xx。
- 未登录访问 `/api/auth/me` 返回 401。

## 12. 迁移旧文档

以下旧口径不再作为实现依据：

- 服务器拉代码后本地构建。
- `docker compose up --build` 作为生产升级方式。
- GHCR 或其它 registry 作为服务器运行镜像来源。
- GitHub Actions 通过 SSH 主动部署生产服务器。
- `YYYY.MM.DD.N` 日期版本号。
- 手工 SQL 作为长期数据库迁移机制。

相关文档如果需要保留历史背景，必须在顶部明确声明：发布制品与服务器安装升级逻辑以本文为准。
