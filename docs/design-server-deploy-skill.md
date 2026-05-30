# ArtFetch 服务器部署 Skill 设计文档

编写日期：2026-05-23

## 1. 背景

ArtFetch 目前已经具备 Docker Compose 部署形态，包含 PostgreSQL、Spring Boot 后端、React/Nginx 前端，以及可选的 Jupyter 分析容器。仓库中已有面向具体服务器的部署手册，但每次部署或升级仍需要人工重新串联以下事项：

- 判断本次改动影响前端、后端、Compose 配置还是数据库迁移。
- 在本地执行对应构建检查。
- 在服务器准备 `.env`、持久化目录、Docker、端口和防火墙。
- 备份数据库和记录当前部署 commit。
- 拉取代码、重建容器、重启服务。
- 验证登录页、任务列表、采集任务、艺术品列表和 Excel 导出。
- 避免把服务器密码、数据库密码、管理员密码、雅昌 Cookie、对象存储密钥写入仓库或对话总结。

需要设计一个 Codex Skill，让 Codex 在用户提出“帮我部署到服务器”“升级线上 ArtFetch”“检查服务器部署状态”“回滚上次发布”等请求时，能按稳定流程执行或指导执行，减少遗漏和误操作。

## 2. Skill 定位

建议 skill 名称：

```text
deploy-artfetch-server
```

建议触发描述：

```yaml
name: deploy-artfetch-server
description: Deploy, upgrade, verify, back up, or roll back the ArtFetch application on the fixed production server 124.174.79.81, or another Linux server when explicitly provided, using SSH and Docker Compose. Use when Codex needs to connect to the server, prepare production directories, configure .env values without leaking secrets, build and restart ArtFetch frontend/backend/postgres containers, run deployment preflight checks, inspect service health, collect logs, perform database backups, or produce a safe deployment runbook for ArtFetch.
```

Skill 的核心职责不是替代所有运维判断，而是把 ArtFetch 的部署经验固定成可重复执行的流程：先识别环境和变更范围，再选择最小安全部署动作，最后完成验证、记录和风险提示。

## 3. 目标

- 支持 ArtFetch 在单台 Linux 服务器上的首次部署。
- 支持通过 SSH 连接固定生产服务器 `124.174.79.81` 执行部署、检查、备份和回滚。
- 支持服务器未安装 Docker 时，通过 SSH 在用户确认后安装 Docker Engine、Buildx 和 Docker Compose plugin。
- 支持基于 Git 和 Docker Compose 的日常升级。
- 支持只改前端、只改后端、同时改前后端、只改配置或数据库迁移的分支流程。
- 支持升级前数据库备份、发布后验证和失败回滚指引。
- 支持检查服务器运行状态、容器日志、端口绑定、磁盘占用和关键环境变量是否缺失。
- 强制保护敏感信息，不把生产密码、Cookie、Token、Access Key 写入仓库文件、commit message、PR 描述或最终总结。
- 在执行前明确哪些命令会改变服务器状态，哪些命令只是读取状态。
- 产出简洁、可复制的命令和结论，便于用户确认线上状态。

## 4. 非目标

- 不设计 Kubernetes、多机高可用、蓝绿发布或自动扩缩容。
- 不托管、生成或保存生产密钥。
- 不绕过服务器 SSH、云安全组、DNS、证书等外部权限限制。
- 不在没有用户明确授权的情况下执行破坏性操作，例如删除数据卷、清空数据库、强制重置 Git 分支。
- 不自动开放数据库、Jupyter 或后端调试端口到公网。
- 不新增 ArtFetch 应用内的后端 API、前端页面或权限码。

## 5. 适用场景

用户可能这样触发 skill：

- “把 ArtFetch 部署到这台服务器。”
- “帮我升级线上版本。”
- “检查一下服务器上的 ArtFetch 是否正常。”
- “后端改完了，帮我发布。”
- “前端页面改了，部署到生产。”
- “发布前帮我备份数据库。”
- “线上挂了，帮我看日志。”
- “回滚到上一个 commit。”
- “写一份部署命令给我，我自己在服务器上执行。”

Skill 需要优先识别用户想要的是“直接执行”还是“生成 runbook”。如果无法连接服务器或用户只想要文档，就输出命令清单和检查表；如果已经具备 SSH 连接信息并且用户要求执行，则按流程执行。

## 6. 输入与假设

### 6.1 必要输入

- 服务器 SSH 用户名或本机 `~/.ssh/config` 中的别名。生产服务器 IP 固定为 `124.174.79.81`，可作为默认 host 写死。
- 服务器项目目录，默认 `/opt/artfetch`。
- 目标分支、tag 或 commit，默认使用当前本地分支的 HEAD。
- 部署方式，默认 Docker Compose。

### 6.2 可选输入

- 服务器操作系统类型，默认按 Ubuntu/Debian 处理。
- 是否允许安装 Docker 和 Compose 插件。
- 是否允许使用 `sudo` 安装系统软件包。
- 是否允许修改防火墙。
- 是否需要临时开放后端调试端口。
- 是否需要执行数据库迁移脚本。
- 是否需要启用对象存储、雅昌认证或 Jupyter profile。
- 是否只生成部署文档，不实际执行。

### 6.3 不应要求用户提供给 Codex 明文保存的内容

- 服务器密码。
- PostgreSQL 密码。
- ArtFetch 管理员密码。
- 雅昌 Cookie、账号、密码。
- 对象存储 Access Key 和 Secret Key。

如果必须配置这些值，Skill 应指导用户在服务器交互式编辑 `/opt/artfetch/.env`，或使用已存在的安全密钥管理方式。最终回复只允许说明“已配置”或“仍缺失某变量”，不得回显真实值。

### 6.4 固定服务器 SSH 连接设计

生产服务器 IP 可以写死为：

```text
124.174.79.81
```

建议 skill 使用以下优先级解析 SSH 目标：

1. 用户显式提供的 SSH alias，例如 `artfetch-prod`。
2. 用户显式提供的 `<user>@124.174.79.81`。
3. Skill 默认服务器 profile：`<user>@124.174.79.81`。

其中 `<user>` 不建议写死，除非已经明确生产服务器固定登录用户。更推荐让用户在本机 `~/.ssh/config` 中配置：

```sshconfig
Host artfetch-prod
  HostName 124.174.79.81
  User <user>
  IdentityFile ~/.ssh/<your-key>
```

Skill 默认使用：

```bash
ssh artfetch-prod
```

如果没有 SSH alias，则使用：

```bash
ssh <user>@124.174.79.81
```

执行远程命令时使用非交互形式：

```bash
ssh artfetch-prod 'cd /opt/artfetch && docker compose ps'
```

涉及多行命令时使用：

```bash
ssh artfetch-prod 'bash -s' <<'REMOTE'
set -euo pipefail
cd /opt/artfetch
docker compose ps
REMOTE
```

SSH 连接规则：

- 可以写死 IP，但不能写死服务器密码。
- 优先使用 SSH key 或用户本机已有 SSH agent。
- 如果首次连接遇到 host key 确认，先向用户说明要信任的目标是 `124.174.79.81`。
- 如果只能密码登录，由用户在终端交互输入；skill 不记录、不转述密码。
- 所有远程脚本输出都必须 mask `.env` 中的敏感值。
- 安装 Docker 需要 `root` 或 `sudo` 权限。Skill 可以执行安装命令，但必须先说明会修改系统包仓库、安装软件包并启动 `docker` 服务。

## 7. Skill 目录设计

建议目录结构：

```text
deploy-artfetch-server/
├── SKILL.md
├── agents/
│   └── openai.yaml
├── scripts/
│   ├── local_preflight.sh
│   ├── remote_install_docker.sh
│   ├── remote_inspect.sh
│   ├── remote_backup_db.sh
│   ├── remote_deploy_compose.sh
│   └── remote_verify.sh
└── references/
    ├── server-profile.md
    ├── docker-install.md
    ├── artfetch-compose.md
    ├── production-env.md
    ├── versioning.md
    ├── first-deploy.md
    ├── upgrade-and-rollback.md
    └── troubleshooting.md
```

`SKILL.md` 只保留主流程、分支选择、安全规则和何时读取 reference。具体命令、环境变量说明、故障排查矩阵放到 `references/`，避免 skill 主体过长。

## 8. SKILL.md 内容设计

`SKILL.md` 建议包含以下章节：

1. **Safety Rules**
   - 在正文顶部记录 skill 版本，但 YAML frontmatter 只保留 `name` 和 `description`。
   - 不回显、不保存、不提交密钥。
   - 不执行删除数据卷、清库、强制重置等破坏性命令，除非用户明确要求并确认目标。
   - 生产默认只开放前端端口，数据库、后端、Jupyter 绑定到 `127.0.0.1` 或 Docker 内网。
   - 任何代码改动发布前必须先本地构建对应服务。
   - Docker 安装属于系统级变更，必须在用户确认后执行；生产环境优先使用 Docker 官方 apt/yum/dnf 仓库，不默认使用 `get.docker.com` convenience script。

2. **Request Classification**
   - 首次部署。
   - 日常升级。
   - 状态检查。
   - 备份。
   - 回滚。
   - 故障排查。
   - 只生成 runbook。

3. **Context Gathering**
   - 读取仓库 `docker-compose.yml`、`.env.example`、`README.md`、已有部署文档。
   - 检查 `git status --short`，识别未提交改动。
   - 判断本次部署影响前端、后端、Compose、数据库或文档。
   - 通过 SSH 查询固定服务器 `124.174.79.81` 上的 `docker compose ps`、`git rev-parse --short HEAD`、磁盘和日志状态。

4. **Execution Workflow**
   - 本地 preflight。
   - 服务器 preflight。
   - 如缺少 Docker，按系统类型安装 Docker Engine、Buildx 和 Compose plugin，并验证 `docker compose version`。
   - 备份。
   - 拉取或同步代码。
   - 构建和启动相关服务。
   - 验证。
   - 记录部署历史。

5. **Verification**
   - 目标 commit 已在服务器 checkout 成功。
   - 容器 health 和 restart 状态。
   - `curl -I http://<host>:<frontendPort>`。
   - `curl -I http://<host>:<frontendPort>/api/`，允许业务 404，但不允许连接失败或 Nginx 502。
   - 页面登录、任务列表、小规模采集、艺术品列表、Excel 导出。
   - 后端日志无持续异常。

6. **Failure Handling**
   - Docker 安装失败：停止首次部署，保留错误输出，提示用户检查系统版本、sudo 权限、网络和软件源。
   - 构建失败：停止发布，保留现有线上容器。
   - 后端启动失败：查看日志，必要时回到上一个 commit 并重建 backend。
   - 前端 502：检查 frontend Nginx 到 backend 的容器网络和 backend 健康。
   - 数据库连接失败：检查 `.env`、postgres 容器、volume、网络和日志。
   - 迁移失败：不要继续反复重启，先备份并定位 SQL 或实体变更。

## 9. 脚本设计

脚本应只做可重复、低歧义的检查和命令编排。所有脚本默认 `set -euo pipefail`，输出时必须 mask 敏感变量。

远程脚本建议设计为“本地 wrapper + SSH 执行远程命令”：脚本在 Codex 本地运行，默认连接 `artfetch-prod` 或 `124.174.79.81`，再通过 SSH 在服务器执行命令。只有确实需要复用远程文件时，才把脚本复制到服务器临时目录。

### 9.1 `scripts/local_preflight.sh`

用途：在本地仓库执行发布前检查。

参数：

```text
--scope frontend|backend|full|compose|docs
```

行为：

- 打印当前分支和 commit。
- 打印 `git status --short`。
- 如果 scope 包含 frontend，执行 `npm run build`。
- 如果 scope 包含 backend，执行 `mvn package -DskipTests`。
- 如果 scope 包含 compose，执行 `docker compose config`。
- 不自动提交、不自动推送。

### 9.2 `scripts/remote_inspect.sh`

用途：读取服务器状态，不修改服务器。

参数：

```text
--ssh-target artfetch-prod|<user>@124.174.79.81
--project-dir /opt/artfetch
```

行为：

- 检查 Docker 和 Compose 版本。
- 检查项目目录、`.env` 是否存在及权限是否建议为 `600`。
- 输出当前 Git commit。
- 输出 `docker compose ps`。
- 输出磁盘容量、Docker 镜像和 volume 占用。
- 检查关键变量是否存在，但只输出变量名和是否为空。

### 9.3 `scripts/remote_install_docker.sh`

用途：服务器未安装 Docker 时，按官方仓库安装 Docker Engine、Buildx 和 Docker Compose plugin。

参数：

```text
--ssh-target artfetch-prod|<user>@124.174.79.81
--assume-yes
```

行为：

- 通过 `/etc/os-release` 识别系统。
- 支持 Ubuntu/Debian 作为第一期实现。
- 安装前检查 `docker --version` 和 `docker compose version`；如果均可用则直接退出。
- 检查是否具备 `sudo` 权限。
- 对 Ubuntu/Debian：
  - 安装 `ca-certificates`、`curl`。
  - 添加 Docker 官方 GPG key 到 `/etc/apt/keyrings/docker.asc`。
  - 添加 Docker 官方 apt source 到 `/etc/apt/sources.list.d/docker.sources`。
  - 安装 `docker-ce`、`docker-ce-cli`、`containerd.io`、`docker-buildx-plugin`、`docker-compose-plugin`。
  - 启动并启用 `docker` 服务。
- 验证：
  - `docker --version`
  - `docker compose version`
  - `systemctl is-active docker`
  - 可选执行 `docker run --rm hello-world`。
- 不删除已有 `/var/lib/docker`、镜像、容器、volume 或网络。
- 如果检测到旧版 `docker.io`、`docker-compose` 等冲突包，先报告并要求用户确认是否移除；不静默卸载。

生产约束：

- 不默认使用 `curl https://get.docker.com | sh`。
- 不在不支持的发行版上硬套 Ubuntu/Debian 命令。
- 不自动把普通用户加入 `docker` 组，除非用户明确要求；加入后也提示需要重新登录会话生效。

### 9.4 `scripts/remote_backup_db.sh`

用途：升级前备份数据库。

参数：

```text
--ssh-target artfetch-prod|<user>@124.174.79.81
--project-dir /opt/artfetch
--backup-dir backups
```

行为：

- 从 `.env` 读取 `POSTGRES_DB` 和 `POSTGRES_USER`。
- 执行 `docker compose exec -T postgres pg_dump`。
- 生成 `backups/artfetch-before-deploy-YYYY-MM-DD-HHMMSS.sql`。
- 写入 `backups/last-good-commit.txt`。
- 输出备份文件路径和大小。

### 9.5 `scripts/remote_deploy_compose.sh`

用途：按服务范围部署。

参数：

```text
--ssh-target artfetch-prod|<user>@124.174.79.81
--project-dir /opt/artfetch
--ref <branch-or-commit>
--scope frontend|backend|full|compose
```

行为：

- `git fetch --all`。
- checkout 目标 ref。
- 如果是分支，执行 `git pull --ff-only`。
- 根据 scope 执行最小构建：
  - frontend：`docker compose build frontend && docker compose up -d frontend`
  - backend：`docker compose build backend && docker compose up -d backend`
  - full：`docker compose up -d --build postgres backend frontend`
  - compose：`docker compose up -d --build postgres backend frontend`
- 不启动 `jupyter`，除非用户明确要求 tools profile。

### 9.6 `scripts/remote_verify.sh`

用途：发布后验证。

参数：

```text
--ssh-target artfetch-prod|<user>@124.174.79.81
--project-dir /opt/artfetch
--base-url http://124.174.79.81:3000
```

行为：

- 输出 `docker compose ps`。
- 检查服务器 `git rev-parse --short HEAD` 是否等于目标 ref。
- 检查 frontend 首页 HTTP 响应。
- 检查 `/api/` 是否可连通。
- 检查 `postgres`、`backend`、`frontend` 容器均处于运行状态，且发布后没有进入异常重启循环。
- 输出 backend/frontend 发布后的 ERROR 计数和关键异常摘要。
- 所有自动检查通过后才追加非敏感部署记录到 `backups/deploy-history.log`。
- 任一关键检查失败时返回非零退出码，并明确标记“升级未确认成功”。

## 10. Reference 设计

### 10.1 `references/server-profile.md`

记录固定生产服务器连接配置：

- 生产服务器 IP：`124.174.79.81`。
- 推荐 SSH alias：`artfetch-prod`。
- 默认项目目录：`/opt/artfetch`。
- 默认访问地址：`http://124.174.79.81:3000`。
- SSH 用户名不写死，除非用户明确确认固定用户名。
- 密码、私钥内容和密钥 passphrase 不进入 skill、仓库或部署日志。
- 示例命令同时给出 alias 形式和 `<user>@124.174.79.81` 形式。

### 10.2 `references/docker-install.md`

记录 Docker 安装策略：

- 优先读取 Docker 官方文档中对应发行版的安装方式。
- Ubuntu/Debian 默认使用 Docker 官方 apt repository。
- 安装包包括 `docker-ce`、`docker-ce-cli`、`containerd.io`、`docker-buildx-plugin`、`docker-compose-plugin`。
- 生产环境不默认使用 convenience script；只可作为测试环境或用户明确要求的 fallback。
- Docker 官方文档提醒，Docker 与 UFW/firewalld/nftables 存在防火墙语义差异；安装后仍需单独检查开放端口和云安全组。
- 安装完成后必须验证 `docker --version`、`docker compose version` 和 `systemctl status docker`。
- 如需要免 `sudo` 执行 Docker，可按 Docker post-install 文档把用户加入 `docker` 组，但这会授予接近 root 的容器管理能力，默认不自动做。

参考资料：

- https://docs.docker.com/engine/install/ubuntu/
- https://docs.docker.com/engine/install/debian/
- https://docs.docker.com/compose/install/linux/
- https://docs.docker.com/engine/install/linux-postinstall/

### 10.3 `references/artfetch-compose.md`

记录 ArtFetch Compose 服务拓扑：

- `postgres`：PostgreSQL 16，生产默认绑定 `127.0.0.1:5432`。
- `backend`：Spring Boot，容器内 `8080`，宿主机默认 `127.0.0.1:8080`。
- `frontend`：Nginx，宿主机默认 `3000:80`，代理 `/api/` 到 `backend:8080`。
- `jupyter`：tools profile，可选，不默认启动。
- 持久化目录：`postgres_data`、`./backend/logs`、`./storage/original-images`、`./backups`。

### 10.4 `references/production-env.md`

记录生产 `.env` 变量分类：

- 必填：`POSTGRES_PASSWORD`、`ARTFETCH_ADMIN_PASSWORD`、`ARTFETCH_OBJECT_STORAGE_ENCRYPTION_KEY`。
- 常用：`POSTGRES_DB`、`POSTGRES_USER`、`FRONTEND_PORT`、`BACKEND_BIND_HOST`。
- 抓取认证：`ARTFETCH_AUTH_ARTRON_COOKIE` 或 `ARTFETCH_AUTH_ARTRON_ACCOUNT` / `ARTFETCH_AUTH_ARTRON_PASSWORD`。
- 对象存储：`ARTFETCH_IMAGE_HD_STORAGE_MODE` 等。

只写变量名、用途和示例占位符，不写真实值。

### 10.5 `references/versioning.md`

记录 skill 版本管理策略：

- Skill 的 YAML frontmatter 只写 `name` 和 `description`，不增加 `version` 字段，避免影响 Codex 触发规范。
- 在 `SKILL.md` 正文顶部维护 `Skill version: x.y.z` 和 `Last reviewed: YYYY-MM-DD`。
- 脚本支持 `--version`，输出 skill 版本和脚本版本。
- 使用语义化版本：
  - `MAJOR`：部署流程、默认服务器、破坏性操作策略或安全边界发生不兼容变化。
  - `MINOR`：新增 Docker 安装、回滚、备份、云厂商、验证门禁等能力。
  - `PATCH`：修正文案、命令兼容性、错误处理或非行为性细节。
- 版本发布通过 Git 管理，推荐 tag 格式：`skill/deploy-artfetch-server/v0.1.0`。
- 每次发布前运行 `quick_validate.py`、脚本 smoke test 和至少一个场景验证。
- 如果 `description`、默认 prompt 或 UI metadata 变化，重新生成并校验 `agents/openai.yaml`。
- 不在 skill 目录内新增 `CHANGELOG.md`；版本说明放在 Git tag annotation、release notes 或仓库 PR 中。
- 部署历史必须记录 app commit、skill version、skill source commit、部署时间、备份文件和验证结果。

回滚策略：

- 如果新版 skill 部署逻辑有问题，回到上一个 Git tag 并重新安装或同步 skill。
- 如果只是某个脚本有问题，优先发布 `PATCH` 版本修复。
- 生产部署失败时，区分“应用回滚”和“skill 回滚”：应用回滚操作线上 ArtFetch 代码，skill 回滚只切换部署工具版本。

### 10.6 `references/first-deploy.md`

记录首次部署流程：

- 安装 Docker。
- 如果 Docker 缺失，先按 `references/docker-install.md` 安装并验证。
- 克隆仓库到 `/opt/artfetch`。
- 创建持久化目录。
- 从 `.env.example` 创建 `.env` 并修改权限。
- 启动 `postgres backend frontend`。
- 配置防火墙和云安全组。
- 执行验证清单。

### 10.7 `references/upgrade-and-rollback.md`

记录升级和回滚流程：

- 本地构建检查。
- 服务器备份。
- `git fetch` / `checkout` / `pull --ff-only`。
- 按 scope 重建服务。
- 发布后必须通过升级成功判定门禁，不能只以 `docker compose up -d` 成功作为升级成功。
- 验证失败时回滚到 `backups/last-good-commit.txt` 中的 commit。
- 恢复数据库只在明确需要时执行，且恢复前停止业务写入。

### 10.8 `references/troubleshooting.md`

记录常见问题和排查顺序：

- 前端打不开。
- `/api/` 502。
- Docker 未安装或 `docker compose` 不可用。
- backend 反复重启。
- postgres healthcheck 失败。
- 登录失败或管理员未初始化。
- 抓取任务失败。
- Excel 导出失败。
- 磁盘空间不足。
- 对象存储配置或高清图访问失败。

## 11. 执行流程设计

### 11.1 首次部署

1. 收集服务器 SSH 目标、目标目录和目标分支。
   - 如果用户没有提供 SSH 目标，默认尝试 `artfetch-prod`。
   - 如果没有 alias，则要求用户提供 SSH 用户名，并连接 `<user>@124.174.79.81`。
2. 读取本地 Compose 和 `.env.example`，确认必填变量。
3. 本地执行 frontend/backend 构建检查。
4. 登录服务器检查 Docker；缺失时说明将修改系统软件源并安装 Docker Engine、Buildx、Compose plugin，得到用户确认后执行安装。
5. 创建 `/opt/artfetch`、`backend/logs`、`storage/original-images`、`backups`。
6. 获取代码，优先 Git clone；无法访问仓库时才使用 rsync。
7. 指导用户在服务器创建 `.env`，不在对话中收集密钥。
8. 启动 `docker compose up -d --build postgres backend frontend`。
9. 设置防火墙：开放 `22/tcp`、`3000/tcp`；不开放 `5432/tcp`、`8888/tcp`。
10. 执行验证清单，输出结果和剩余风险。

### 11.2 日常升级

1. 判断 scope。
2. 本地执行对应构建。
3. 检查本地未提交改动，提醒目标 commit 必须可在服务器取得。
4. 通过 SSH 连接 `124.174.79.81`，在服务器执行数据库备份并记录 last good commit。
5. 拉取目标 ref。
6. 按 scope 构建和重启服务。
7. 验证服务和页面。
8. 执行升级成功判定门禁。
9. 门禁全部通过后，写入非敏感部署历史。
10. 任一门禁失败时，不标记升级成功；保留日志摘要并给出继续排查或回滚建议。

### 11.3 升级成功判定

升级必须同时通过自动门禁和人工烟测，才算成功。

自动门禁：

- 本地构建通过：前端变更必须通过 `npm run build`，后端变更必须通过 `mvn package -DskipTests`。
- 服务器代码版本正确：`git rev-parse --short HEAD` 与目标 commit 一致。
- 镜像构建和容器启动命令成功返回。
- `docker compose ps` 显示 `postgres`、`backend`、`frontend` 处于 running/healthy 或等价正常状态。
- 容器发布后没有持续重启，`Restarting`、`Exited`、`unhealthy` 均视为失败。
- 前端入口 `http://124.174.79.81:3000` 可访问。
- `http://124.174.79.81:3000/api/` 可连通；允许 404 或业务错误，不允许连接失败、超时或 Nginx 502。
- backend/frontend 发布后日志没有持续新增 ERROR；如有 ERROR，必须确认与本次发布无关后才可继续。
- 数据库备份文件存在且大小非 0；升级前 last good commit 已记录。

人工烟测：

- 登录页可打开。
- 管理员账号可登录。
- 任务列表可加载。
- 可创建一个小规模测试任务，状态能正常流转。
- 艺术品列表可查询。
- Excel 导出可下载。

判定规则：

- 自动门禁失败：直接判定“升级未确认成功”，停止写入成功部署记录，优先排查或回滚。
- 自动门禁通过但人工烟测未做：只能标记“自动验证通过，等待人工确认”，不能宣称完整成功。
- 自动门禁和人工烟测都通过：标记“升级成功”，记录部署时间、目标 commit、备份文件和验证结果。

### 11.4 状态检查

1. 通过 SSH 读取 `124.174.79.81` 的容器状态。
2. 检查端口绑定和磁盘。
3. 检查 `.env` 必填变量是否为空。
4. 查看 backend/frontend 最近日志。
5. 对外访问 frontend 和 `/api/`。
6. 输出“正常 / 有风险 / 已故障”的结论和下一步建议。

### 11.5 回滚

1. 读取 `backups/last-good-commit.txt` 或用户指定 commit。
2. 明确回滚代码还是同时恢复数据库。
3. 先备份当前数据库。
4. checkout 上一个 commit。
5. 重建受影响服务。
6. 验证。
7. 只有在用户明确确认时才恢复旧数据库备份。

## 12. 安全和权限设计

本 skill 不新增 ArtFetch 应用内接口、页面或按钮，因此不需要新增 Sa-Token 权限码、路由守卫或审计日志。

如果后续把部署能力做成 ArtFetch 应用内页面或 API，则必须重新设计权限边界：

- 新增稳定权限码，例如 `system:deploy:view`、`system:deploy:execute`、`system:deploy:rollback`。
- 后端控制器使用 `@SaCheckPermission`。
- 发布、回滚、备份、配置变更必须写审计日志。
- 前端只隐藏按钮不能替代后端权限检查。
- 任何日志和审计内容都必须 mask 密钥。

Skill 自身的安全边界：

- 不在仓库写入生产 `.env`。
- 不把真实密钥写入 `docs/`、commit、PR 或最终回复。
- 不在 skill 中写死服务器密码、私钥内容或密钥 passphrase。
- 不运行 `docker compose down -v`、`docker volume rm`、`rm -rf /opt/artfetch` 等破坏性命令。
- 不自动把 `POSTGRES_BIND_HOST`、`BACKEND_BIND_HOST`、`JUPYTER_BIND_HOST` 改成 `0.0.0.0`。
- 不默认启动 `jupyter` profile。

## 13. Skill 版本管理设计

Codex skill 没有强制内建版本字段；本 skill 采用“正文版本号 + Git tag + 部署记录”的方式管理版本。

版本记录位置：

- `SKILL.md` 正文顶部写 `Skill version: x.y.z`。
- `scripts/* --version` 输出同一个版本号。
- Git tag 固化可安装版本，例如 `skill/deploy-artfetch-server/v0.1.0`。
- 服务器 `backups/deploy-history.log` 记录每次部署使用的 skill version 和 skill source commit。

版本发布流程：

1. 修改 `SKILL.md`、references 或 scripts。
2. 更新 `Skill version`。
3. 运行 `quick_validate.py`。
4. 运行脚本 smoke test。
5. 如涉及部署行为变化，在测试服务器 forward test。
6. 生成 Git tag。
7. 同步到 `$CODEX_HOME/skills/deploy-artfetch-server` 或对应安装目录。

兼容性规则：

- 改变默认服务器、默认端口、安全确认策略、回滚策略或会影响生产状态的行为，必须升 `MAJOR` 或至少在 release notes 中明确标记。
- 新增能力但不改变现有默认行为，升 `MINOR`。
- 修正文档、日志、错误处理和兼容性问题，升 `PATCH`。
- 未完成或未验证的版本标记为 pre-release，例如 `0.2.0-rc.1`，不得默认用于生产部署。

## 14. 验证设计

Skill 创建完成后需要验证三类能力：

### 14.1 静态验证

- 使用 `quick_validate.py` 校验 skill frontmatter 和命名。
- 检查 `SKILL.md` 是否小于 500 行左右。
- 检查 references 是否都被 `SKILL.md` 直接引用。
- 检查脚本是否可执行且 shellcheck 无严重问题。
- 检查 `SKILL.md`、脚本 `--version` 和 Git tag 版本一致。

### 14.2 脚本验证

在本地或测试服务器验证：

```bash
scripts/local_preflight.sh --scope frontend
scripts/local_preflight.sh --scope backend
scripts/remote_inspect.sh --project-dir /opt/artfetch
scripts/remote_verify.sh --project-dir /opt/artfetch --base-url http://127.0.0.1:3000
```

备份和部署脚本必须先在测试服务器运行，不直接拿生产首测。

### 14.3 场景验证

用真实请求进行 forward test：

- “帮我首次部署 ArtFetch 到一台 Ubuntu 服务器，先给 runbook。”
- “我只改了前端，帮我发布到服务器。”
- “线上 `/api/` 502，帮我排查。”
- “发布失败了，帮我回滚到上一个 commit。”

验证重点是 Codex 是否会先做备份、是否避免泄露密钥、是否只重启必要服务、是否能给出明确结论。

## 15. 交付阶段

### 阶段一：文档型 Skill

- 创建 `SKILL.md` 和 references。
- 不包含自动部署脚本。
- 适合生成部署计划、检查清单和人工执行命令。

### 阶段二：半自动 Skill

- 增加 read-only 的 `remote_inspect.sh` 和 `remote_verify.sh`。
- 增加本地 `local_preflight.sh`。
- 只自动执行检查，不自动改服务器。

### 阶段三：可执行部署 Skill

- 增加备份和部署脚本。
- 支持明确 scope 的自动部署。
- 每次变更服务器前输出动作摘要。
- 高风险动作仍要求用户明确确认。

建议先做阶段一和阶段二，等流程在测试服务器跑顺后再进入阶段三。

## 16. 开放问题

- 生产服务器是否固定使用 `/opt/artfetch`，还是需要支持多环境目录，例如 `/opt/artfetch-staging` 和 `/opt/artfetch-prod`。
- 线上代码获取方式是 Git clone、rsync，还是 CI/CD 产物拉取。
- 是否需要支持域名、HTTPS 证书和 Nginx 反向代理到 `localhost:3000`。
- 是否需要把数据库迁移从手工 SQL 升级为 Flyway/Liquibase。
- 是否需要把对象存储配置检查纳入部署后验收。
- 是否需要为部署历史建立统一格式，例如 `backups/deploy-history.log` 或独立 `deployments/` 目录。
- skill 源码最终放在 ArtFetch 仓库、个人 `$CODEX_HOME/skills` Git 仓库，还是独立 skills 仓库。

## 17. 成功标准

该 skill 落地后，应满足：

- 新机器首次部署可以按 skill 输出的步骤完成。
- 日常升级不会漏掉本地构建、服务器备份、服务重启和发布验证。
- 前端、后端、Compose 配置变更能选择不同的最小部署路径。
- 遇到常见线上问题时，Codex 能先收集证据再给结论。
- 敏感信息不会出现在仓库、日志摘要、commit 或最终回复里。
- 用户能从最终回复中看到部署是否成功、当前 commit、服务状态、验证结果和剩余风险。
- 每次部署都能追溯使用的 skill version 和 skill source commit。
