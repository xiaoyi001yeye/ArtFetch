# ArtFetch 服务器安装与升级手册

目标服务器：`124.174.79.81`

状态：服务器操作手册

更新日期：2026-06-01

发布制品与服务器安装升级逻辑以 [GitHub Actions 离线制品发布与服务器安装升级设计](design-github-actions-release-deployment.md) 为准。本文只保留服务器侧操作步骤，不再描述服务器拉代码、本地构建、registry 拉镜像或 GitHub Actions SSH 部署。

## 1. 部署目标

ArtFetch 部署到单台 Linux 服务器，通过 Docker Compose 运行：

- `postgres`
- `backend`
- `frontend`
- `jupyter`

服务器不从 Docker Hub、GHCR 或其它 registry 拉取运行镜像。所有运行镜像都来自 GitHub Release 附件中的离线 tar 包，并由部署脚本执行 `docker load`。

## 2. 服务器前置条件

服务器需要安装：

- `curl`
- `tar`
- `sha256sum`
- `python3`
- `docker`
- Docker Compose plugin，即 `docker compose`

服务器不需要安装：

- Node.js
- npm
- Java
- Maven
- Git
- GitHub CLI `gh`

## 3. 目录规划

默认项目目录：

```text
/opt/artfetch
```

可通过入口脚本环境变量覆盖：

```bash
ARTFETCH_PROJECT_DIR=/data/artfetch bash install-or-upgrade-latest.sh
```

脚本成功运行后，目录结构类似：

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

`.env` 保存服务器本地密钥和业务配置，不进入 GitHub Release。`.env.release` 由安装/升级脚本根据 manifest 生成，保存本次运行镜像名。

## 4. 敏感信息

以下内容只能保存在服务器本地 `.env`、密码管理器或用户交互输入中：

- PostgreSQL 密码。
- ArtFetch 管理员初始密码。
- 雅昌 Cookie。
- 雅昌账号和密码。
- 对象存储 Access Key、Secret Key。
- 对象存储加密密钥。
- GitHub token。

不要把真实密钥写入：

- `docs/`
- `.env.example`
- `README.md`
- Git commit message
- GitHub issue / PR / review comment
- GitHub Release notes

## 5. 首次安装

在 GitHub Release 页面下载顶层附件 `install-or-upgrade-latest.sh`，或用 `curl` 下载该脚本。

公开仓库：

```bash
bash install-or-upgrade-latest.sh
```

私有仓库：

```bash
GITHUB_TOKEN=<token> bash install-or-upgrade-latest.sh
```

脚本会：

1. 调用 GitHub `releases/latest` API 获取最新正式 Release。
2. 下载 `release-manifest.json`、`artfetch-deploy-<version>.tgz` 和 `.sha256`。
3. 校验部署包 SHA256。
4. 解包并校验 manifest、Compose hash 和所有镜像 tar hash。
5. 创建 `/opt/artfetch` 目录。
6. 如果 `.env` 不存在，从 `.env.example` 创建 `.env`。
7. 自动生成 `POSTGRES_PASSWORD`、`ARTFETCH_ADMIN_PASSWORD`、`ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY`。
8. `docker load` 所有运行镜像。
9. 写入 `.env.release`。
10. 启动 Docker Compose 服务。
11. 验证后端健康检查、前端入口和未登录鉴权 API。

脚本不得打印自动生成的密钥。

## 6. 后续升级

升级仍运行同一个入口脚本：

```bash
bash install-or-upgrade-latest.sh
```

脚本会下载最新正式 Release，并在部署前检查运行中采集任务或上传任务。如果存在运行中任务，升级应停止，等待任务完成后再重新运行脚本。

升级过程会保留用户已有 `.env`，不会覆盖生产密钥。

## 7. 失败处理

安装或升级失败时不做自动回滚。脚本应清理本次失败产生的候选文件、解包目录和未成功启用的容器，然后用户重新运行入口脚本。

默认保留：

- `.env`
- PostgreSQL 数据卷
- 图片目录
- 后端日志
- 部署前数据库备份

只有用户显式设置下面变量时，才允许删除持久化数据并完全重装：

```bash
ARTFETCH_WIPE_DATA=1 bash install-or-upgrade-latest.sh
```

## 8. 生产配置调整

首次安装后，如需配置雅昌 Cookie、雅昌账号密码或对象存储，编辑：

```bash
vi /opt/artfetch/.env
```

然后重启：

```bash
cd /opt/artfetch
docker compose --env-file .env --env-file .env.release -f docker-compose.prod.yml up -d
```

## 9. 验证

查看服务：

```bash
cd /opt/artfetch
docker compose --env-file .env --env-file .env.release -f docker-compose.prod.yml ps
```

查看后端健康检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
```

查看前端：

```bash
curl -fsSI http://127.0.0.1:3000/
```

查看未登录鉴权 API：

```bash
curl -sS -o /tmp/artfetch-auth-body -w '%{http_code}\n' http://127.0.0.1:3000/api/auth/me
```

预期返回 `401`。

## 10. 废弃流程

以下服务器部署方式不再使用：

- 在服务器 `git clone` 仓库后构建。
- 在服务器运行 `npm run build`。
- 在服务器运行 `mvn package`。
- 在服务器运行 `docker compose up --build`。
- 在服务器运行 `docker pull`。
- 通过 GitHub Actions `Deploy Production` SSH 到服务器主动部署。
- 使用 `YYYY.MM.DD.N` 日期版本号。
