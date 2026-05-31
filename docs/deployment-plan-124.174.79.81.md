# ArtFetch 服务器部署与升级手册

目标服务器：`124.174.79.81`

编写日期：2026-05-15

本文档用于指导 ArtFetch 在 `124.174.79.81` 上的首次部署、日常升级、回滚、备份和故障排查。服务器登录密码、数据库密码、管理员密码、雅昌 Cookie 等敏感信息不得写入本文档、Git commit、PR 描述或任何会提交到 GitHub 的文件。

## 1. 部署目标

将 ArtFetch 以 Docker Compose 方式部署到单台服务器，对外提供稳定 Web 访问，并保证以下能力：

- 首次部署可按步骤复现。
- 后续升级有固定命令和检查清单。
- 数据库、图片、日志持久化。
- 发布前可备份，发布失败可回滚。
- 服务器密码和业务密钥不进入 GitHub。

## 2. 推荐部署形态

生产环境使用仓库现有 Docker Compose 方案，Compose 中定义的服务默认都启动：

- `postgres`：PostgreSQL 16，保存业务数据。
- `backend`：Spring Boot 后端，容器内监听 `8080`。
- `frontend`：Nginx 托管 React 静态资源，并代理 `/api/` 到 `backend:8080`。
- `jupyter`：用于内网分析和成交价预测调试，生产随 Compose 默认启动，但 `8888` 默认只绑定到 `127.0.0.1`，避免公网暴露。

首期访问入口：

- 前端：`http://124.174.79.81:3000`
- 后端调试：`http://124.174.79.81:8080`

正式对外建议只开放前端入口。后端和数据库通过 Docker 内部网络访问：

- 对外开放：`22/tcp`、`3000/tcp`
- 调试期临时开放：`8080/tcp`
- 不对公网开放：`5432/tcp`、`8888/tcp`

## 3. 服务器目录规划

统一使用 `/opt/artfetch` 作为项目目录：

```bash
/opt/artfetch
├── .env                         # 服务器本地生产配置，不提交 Git
├── docker-compose.yml
├── backend/
│   └── logs/                    # 后端日志挂载目录
├── storage/
│   └── original-images/         # 图片持久化目录
└── backups/                     # 手动数据库备份目录
```

首次部署前创建目录：

```bash
mkdir -p /opt/artfetch/backend/logs
mkdir -p /opt/artfetch/storage/original-images
mkdir -p /opt/artfetch/backups
```

## 4. 敏感信息管理

### 4.1 绝不提交的内容

以下内容只能保存在服务器本地 `.env`、密码管理器或临时交互输入中：

- 服务器 SSH 密码。
- PostgreSQL 密码。
- ArtFetch 管理员初始密码。
- 雅昌 Cookie。
- 雅昌账号和密码。

不要把真实密码写进：

- `docs/`
- `.env.example`
- `README.md`
- `docker-compose.yml`
- Git commit message
- GitHub issue / PR / review comment

### 4.2 推荐登录方式

短期可以使用密码登录服务器，但后续建议改为 SSH key：

```bash
ssh-copy-id <user>@124.174.79.81
```

然后在本机 `~/.ssh/config` 配置别名，别名文件不要包含密码：

```sshconfig
Host artfetch-prod
  HostName 124.174.79.81
  User <user>
  IdentityFile ~/.ssh/<your-key>
```

后续连接：

```bash
ssh artfetch-prod
```

## 5. 生产配置

### 5.1 服务器 `.env`

在服务器 `/opt/artfetch/.env` 创建生产配置：

```bash
POSTGRES_DB=artfetch
POSTGRES_USER=artfetch
POSTGRES_PASSWORD=<生产数据库强密码>

ARTWORK_SOURCE_URL=https://artso.artron.net/auction/search_auction.php
REQUEST_DELAY_MS=300

ARTFETCH_AUTH_ARTRON_COOKIE=
ARTFETCH_AUTH_ARTRON_ACCOUNT=
ARTFETCH_AUTH_ARTRON_PASSWORD=

ARTFETCH_ADMIN_USERNAME=admin
ARTFETCH_ADMIN_PASSWORD=<生产管理员强密码>
```

创建方式：

```bash
cd /opt/artfetch
cp .env.example .env
chmod 600 .env
vi .env
```

### 5.2 Compose 密码读取

当前 `docker-compose.yml` 已改为从 `.env` 读取 PostgreSQL、后端管理员和雅昌认证配置，便于以后部署和换密：

```yaml
postgres:
  environment:
    POSTGRES_DB: ${POSTGRES_DB:-artfetch}
    POSTGRES_USER: ${POSTGRES_USER:-artfetch}
    POSTGRES_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}

backend:
  environment:
    SPRING_DATASOURCE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-artfetch}
    SPRING_DATASOURCE_USERNAME: ${POSTGRES_USER:-artfetch}
    SPRING_DATASOURCE_PASSWORD: ${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
    ARTFETCH_ADMIN_USERNAME: ${ARTFETCH_ADMIN_USERNAME:-admin}
    ARTFETCH_ADMIN_PASSWORD: ${ARTFETCH_ADMIN_PASSWORD:?ARTFETCH_ADMIN_PASSWORD is required}
```

同时，PostgreSQL、后端和 Jupyter 默认只绑定到 `127.0.0.1`，前端默认对外开放 `3000`。如果确实需要临时公网调试后端，可在服务器 `.env` 中临时设置 `BACKEND_BIND_HOST=0.0.0.0`，验证完成后再改回 `127.0.0.1` 并重启服务。

## 6. 首次部署流程

### 6.1 本地构建检查

在本机部署前先确认代码可构建：

```bash
cd /Users/wyn/code/ArtFetch/frontend
npm run build

cd /Users/wyn/code/ArtFetch/backend
mvn package -DskipTests
```

确认工作区状态：

```bash
cd /Users/wyn/code/ArtFetch
git status --short
git rev-parse --short HEAD
```

记录本次部署 commit，便于回滚。

### 6.2 服务器安装基础软件

登录服务器：

```bash
ssh <user>@124.174.79.81
```

Ubuntu/Debian 示例：

```bash
apt-get update
apt-get install -y ca-certificates curl git
install -m 0755 -d /etc/apt/keyrings
curl -fsSL https://download.docker.com/linux/ubuntu/gpg -o /etc/apt/keyrings/docker.asc
chmod a+r /etc/apt/keyrings/docker.asc
echo "deb [arch=$(dpkg --print-architecture) signed-by=/etc/apt/keyrings/docker.asc] https://download.docker.com/linux/ubuntu $(. /etc/os-release && echo "$VERSION_CODENAME") stable" > /etc/apt/sources.list.d/docker.list
apt-get update
apt-get install -y docker-ce docker-ce-cli containerd.io docker-buildx-plugin docker-compose-plugin
```

验证：

```bash
docker --version
docker compose version
```

### 6.3 获取代码

推荐服务器直接拉取 Git 仓库：

```bash
mkdir -p /opt
cd /opt
git clone <仓库地址> artfetch
cd /opt/artfetch
git checkout <待部署分支或commit>
```

如果服务器无法访问代码仓库，从本机同步代码：

```bash
rsync -av \
  --exclude node_modules \
  --exclude target \
  --exclude .git \
  /Users/wyn/code/ArtFetch/ \
  <user>@124.174.79.81:/opt/artfetch/
```

同步方式不会把 Git 历史带到服务器，后续升级更推荐使用 Git 拉取。

### 6.4 写入服务器配置

```bash
cd /opt/artfetch
cp .env.example .env
chmod 600 .env
vi .env
```

填入生产数据库密码、管理员密码、可选雅昌 Cookie 或账号密码。不要把这些值复制回本地仓库。

### 6.5 启动核心服务

```bash
cd /opt/artfetch
docker compose up -d --build postgres backend frontend jupyter
docker compose ps
```

查看日志：

```bash
docker compose logs --tail=200 backend
docker compose logs --tail=100 frontend
```

Jupyter 随 Compose 默认启动，但端口默认绑定到 `127.0.0.1`，不要改成公网绑定。

### 6.6 防火墙

如果使用 UFW：

```bash
ufw allow 22/tcp
ufw allow 3000/tcp
ufw deny 5432/tcp
ufw deny 8888/tcp
ufw enable
ufw status
```

调试期如需直连后端：

```bash
ufw allow 8080/tcp
```

验证完成后关闭公网后端端口：

```bash
ufw deny 8080/tcp
```

云服务器还需要在安全组里同步开放或关闭对应端口。

## 7. 部署验证清单

每次首次部署或升级后都按这个清单验证：

```bash
cd /opt/artfetch
docker compose ps
docker compose logs --tail=200 backend
curl -I http://124.174.79.81:3000
curl -I http://124.174.79.81:3000/api/
```

`/api/` 可能返回 `404` 或业务错误，但不应出现连接失败或 Nginx `502`。

页面验证：

- 登录页可打开。
- 管理员账号可登录。
- 任务列表可加载。
- 创建一个小规模测试任务，状态能从 `PENDING` / `RUNNING` 正常流转。
- 艺术品列表可查询。
- Excel 导出可下载。
- 后端日志无持续异常。

## 8. 日常升级流程

以后每次升级按固定步骤执行。

### 8.1 升级前本地检查

```bash
cd /Users/wyn/code/ArtFetch/frontend
npm run build

cd /Users/wyn/code/ArtFetch/backend
mvn package -DskipTests

cd /Users/wyn/code/ArtFetch
git status --short
git rev-parse --short HEAD
```

如本次改了前端、后端或 Compose 配置，确认对应构建通过后再部署。

### 8.2 升级前服务器备份

```bash
ssh <user>@124.174.79.81
cd /opt/artfetch
mkdir -p backups
docker compose exec -T postgres pg_dump -U artfetch -d artfetch > backups/artfetch-before-upgrade-$(date +%F-%H%M%S).sql
git rev-parse --short HEAD > backups/last-good-commit.txt
```

如果以后 Compose 已改成读取 `.env` 的数据库用户名，备份命令里的 `artfetch` 应与 `.env` 中 `POSTGRES_USER` / `POSTGRES_DB` 保持一致。

### 8.3 拉取代码并重建

服务器使用 Git 部署时：

```bash
cd /opt/artfetch
git fetch --all
git checkout <目标分支>
git pull --ff-only
docker compose build backend frontend
docker compose up -d backend frontend
docker compose ps
```

如果 `docker-compose.yml`、环境变量或数据库配置有变化，使用：

```bash
docker compose up -d --build postgres backend frontend
```

如果本次只改前端：

```bash
docker compose build frontend
docker compose up -d frontend
```

如果本次只改后端：

```bash
docker compose build backend
docker compose up -d backend
```

### 8.4 升级后验证

执行第 7 节完整验证清单。验证通过后记录：

- 部署时间。
- 部署 commit。
- 是否有数据库备份。
- 是否有配置变更。

推荐记录到服务器本地文件，不写敏感信息：

```bash
cd /opt/artfetch
{
  echo "$(date '+%F %T') deployed $(git rev-parse --short HEAD)"
  docker compose ps
} >> backups/deploy-history.log
```

## 9. 快速命令速查

进入项目：

```bash
cd /opt/artfetch
```

查看服务：

```bash
docker compose ps
```

查看后端日志：

```bash
docker compose logs -f backend
```

查看前端日志：

```bash
docker compose logs -f frontend
```

重启后端：

```bash
docker compose restart backend
```

重启前端：

```bash
docker compose restart frontend
```

重启核心服务：

```bash
docker compose restart backend frontend
```

停止核心服务：

```bash
docker compose stop backend frontend
```

启动核心服务：

```bash
docker compose up -d postgres backend frontend
```

查看磁盘：

```bash
df -h
docker system df
```

清理未使用镜像：

```bash
docker image prune
```

不要使用会删除数据卷的清理命令，除非已经备份并明确知道后果。

## 10. 备份与恢复

### 10.1 手动数据库备份

```bash
cd /opt/artfetch
mkdir -p backups
docker compose exec -T postgres pg_dump -U artfetch -d artfetch > backups/artfetch-$(date +%F-%H%M%S).sql
```

### 10.2 恢复数据库

恢复前先停止业务写入：

```bash
cd /opt/artfetch
docker compose stop backend frontend
```

恢复：

```bash
cat backups/<backup-file>.sql | docker compose exec -T postgres psql -U artfetch -d artfetch
```

恢复后启动并验证：

```bash
docker compose up -d backend frontend
docker compose ps
```

### 10.3 图片和日志备份

建议定期备份：

- `/opt/artfetch/storage/original-images`
- `/opt/artfetch/backend/logs`
- `/opt/artfetch/backups`

示例：

```bash
tar -czf backups/artfetch-files-$(date +%F-%H%M%S).tar.gz storage/original-images backend/logs
```

## 11. 回滚流程

### 11.1 应用代码回滚

回滚到上一个稳定 commit：

```bash
cd /opt/artfetch
git checkout <上一个稳定commit>
docker compose build backend frontend
docker compose up -d backend frontend
docker compose ps
```

然后执行第 7 节验证清单。

### 11.2 数据回滚

只有当数据库结构或数据被错误修改时才恢复数据库：

```bash
cd /opt/artfetch
docker compose stop backend frontend
cat backups/<backup-file>.sql | docker compose exec -T postgres psql -U artfetch -d artfetch
docker compose up -d backend frontend
```

如果只是前端或后端代码问题，优先做代码回滚，不恢复数据库。

## 12. 配置变更流程

修改服务器 `.env` 后，需要重启相关服务：

```bash
cd /opt/artfetch
vi .env
docker compose up -d backend frontend
```

数据库用户名、密码、库名变更更谨慎，建议流程：

1. 备份数据库。
2. 停止后端和前端。
3. 修改 `.env` 和数据库账号。
4. 重启 PostgreSQL、后端、前端。
5. 执行完整验证。

示例：

```bash
docker compose stop backend frontend
docker compose up -d postgres
docker compose up -d backend frontend
```

## 13. 故障排查

### 13.1 前端打不开

```bash
cd /opt/artfetch
docker compose ps
docker compose logs --tail=100 frontend
curl -I http://127.0.0.1:3000
```

重点检查：

- `frontend` 容器是否运行。
- 服务器防火墙或安全组是否开放 `3000`。
- 端口是否被其他服务占用。

### 13.2 前端能打开但接口 502

```bash
docker compose ps
docker compose logs --tail=200 backend
docker compose exec frontend wget -S -O - http://backend:8080/api/
```

重点检查：

- `backend` 是否启动成功。
- 后端是否连上 PostgreSQL。
- `frontend/nginx.conf` 代理目标是否仍是 `http://backend:8080`。

### 13.3 后端启动失败

```bash
docker compose logs --tail=300 backend
docker compose logs --tail=100 postgres
```

重点检查：

- `.env` 中数据库密码是否和 PostgreSQL 初始化密码一致。
- PostgreSQL 是否 healthy。
- 是否有 JPA 建表或字段迁移错误。

### 13.4 数据库空间不足

```bash
df -h
docker system df
du -sh storage/original-images backend/logs backups
```

处理建议：

- 先备份，再清理旧日志和旧备份。
- 可执行 `docker image prune` 清理未使用镜像。
- 不要删除 PostgreSQL volume。

## 14. 安全基线

- 部署后立刻修改默认管理员密码。
- `.env` 权限设置为 `600`。
- 服务器 SSH 密码不要写入任何仓库文件。
- 优先使用 SSH key，后续可关闭密码登录。
- PostgreSQL `5432` 不对公网开放。
- Jupyter `8888` 在生产默认启动，但只绑定 `127.0.0.1`，不对公网开放。
- 后端 `8080` 只在调试期临时开放。
- 长期对外访问建议绑定域名并配置 HTTPS。
- 雅昌 Cookie 或账号密码只写服务器 `.env`。
- 数据导出、用户、角色、权限等敏感操作继续依赖现有 Sa-Token 权限和审计机制。

## 15. 后续自动化建议

为进一步降低升级成本，建议后续新增但不强制：

- `scripts/deploy-prod.sh`：封装拉代码、备份、构建、重启、验证。
- `scripts/backup-prod.sh`：封装数据库和文件备份。
- `docker-compose.prod.yml`：覆盖生产端口、资源限制、日志策略。
- Git tag 发布：每次稳定版本打 `vYYYYMMDD-N` 标签。
- 定时备份：使用 cron 每天导出数据库，并保留最近 7 到 14 天。

建议自动化脚本仍然不要写入任何真实密码，只读取服务器本地 `.env`。

## 16. 实施顺序建议

1. 本地执行前后端构建验证。
2. 登录服务器安装 Docker 和 Compose。
3. 在 `/opt/artfetch` 拉取或同步代码。
4. 在服务器手动创建 `.env`，填入真实生产密码。
5. 启动 `postgres backend frontend`。
6. 按验证清单检查页面、接口、任务、导出。
7. 调试完成后确认公网只保留前端访问入口。

## 17. 2026-05-15 实际部署记录

本次在 `124.174.79.81` 上已完成部署，公网访问地址：

```text
http://124.174.79.81/
```

实施过程中服务器访问 Docker Hub 超时，且本机 Apple Silicon 导出的 Docker 镜像为 `arm64`，无法在服务器 `amd64` 架构上运行。因此本次生产实例最终采用原生 systemd + Nginx + PostgreSQL 部署，而不是 Docker Compose 运行。

当前服务器运行方式：

- PostgreSQL：系统服务 `postgresql`
- 后端：systemd 服务 `artfetch-backend`
- 前端：Nginx 静态站点，监听 `80` 和 `3000`
- API 代理：Nginx `/api/` 转发到 `127.0.0.1:8080`

关键路径：

```bash
/opt/artfetch/.env                         # 生产敏感配置，权限 600，不提交 Git
/opt/artfetch/backend/app.jar              # 后端 JAR
/opt/artfetch/backend/backend.env          # 后端 systemd 环境变量，权限 600
/opt/artfetch/frontend-dist/               # 前端静态资源
/opt/artfetch/storage/original-images/     # 图片持久化目录
/etc/systemd/system/artfetch-backend.service
/etc/nginx/sites-available/artfetch
```

验证结果：

```bash
curl -I http://124.174.79.81/
# HTTP/1.1 200 OK

curl -I http://124.174.79.81/api/
# HTTP/1.1 401
```

`/api/` 返回 `401` 是预期结果，表示请求已到达后端且受登录认证保护。

### 17.1 当前生产实例常用命令

查看服务状态：

```bash
systemctl status postgresql --no-pager
systemctl status artfetch-backend --no-pager
systemctl status nginx --no-pager
```

查看后端日志：

```bash
journalctl -u artfetch-backend -f
```

重启后端：

```bash
systemctl restart artfetch-backend
```

重载 Nginx：

```bash
nginx -t
systemctl reload nginx
```

查看管理员初始密码：

```bash
grep '^ARTFETCH_ADMIN_PASSWORD=' /opt/artfetch/.env
```

该密码只保存在服务器本地 `.env`，不要复制到 GitHub、文档或提交记录中。首次登录后建议在系统内修改管理员密码。

### 17.2 原生部署升级流程

本地构建：

```bash
cd /Users/wyn/code/ArtFetch/frontend
npm run build

cd /Users/wyn/code/ArtFetch
docker build backend -t artfetch-backend:latest
cid=$(docker create artfetch-backend:latest)
docker cp "$cid:/app/app.jar" /tmp/artfetch-backend-app.jar
docker rm "$cid"
tar -czf /tmp/artfetch-frontend-dist.tar.gz -C frontend/dist .
```

上传到服务器：

```bash
scp /tmp/artfetch-backend-app.jar root@124.174.79.81:/opt/artfetch/backend/app.jar
scp /tmp/artfetch-frontend-dist.tar.gz root@124.174.79.81:/opt/artfetch/backups/artfetch-frontend-dist.tar.gz
```

服务器更新并重启：

```bash
cd /opt/artfetch
rm -rf frontend-dist/*
tar -xzf backups/artfetch-frontend-dist.tar.gz -C frontend-dist
systemctl restart artfetch-backend
nginx -t
systemctl reload nginx
curl -I http://127.0.0.1/
curl -I http://127.0.0.1/api/
```

如果 SSH 大文件传输不稳定，可继续采用本次使用的 20 MB 分块上传方式。
