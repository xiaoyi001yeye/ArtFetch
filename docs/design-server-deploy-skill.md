# ArtFetch 服务器部署 Skill 设计文档

状态：需按离线 Release 部署链路更新

更新日期：2026-06-01

发布制品与服务器安装升级逻辑以 [GitHub Actions 离线制品发布与服务器安装升级设计](design-github-actions-release-deployment.md) 为准。本文只描述 Codex Skill 在服务器部署相关请求中应遵守的行为边界。

## 1. Skill 定位

`deploy-artfetch-server` 用于协助 ArtFetch 的服务器安装、升级、检查和故障排查。

Skill 不再指导或执行：

- 服务器拉取源码。
- 服务器运行 `npm run build`。
- 服务器运行 `mvn package`。
- 服务器运行 `docker build`。
- 服务器运行 `docker pull`。
- GitHub Actions 通过 SSH 主动部署生产服务器。

Skill 应优先指导用户使用 GitHub 最新正式 Release 顶层附件：

```text
install-or-upgrade-latest.sh
```

## 2. 目标

- 指导用户在目标服务器运行最新正式 Release 的安装/升级脚本。
- 检查服务器是否具备脚本依赖：`curl`、`tar`、`sha256sum`、`python3`、`docker`、`docker compose`。
- 协助检查 `/opt/artfetch` 或用户指定 `ARTFETCH_PROJECT_DIR` 的状态。
- 协助检查 `.env`、`.env.release`、`release-manifest.json` 是否存在且权限合理。
- 协助查看容器状态、日志、端口绑定、磁盘空间和健康检查。
- 协助用户安全编辑服务器 `.env`，但不得回显密钥。
- 在失败后指导用户重新运行入口脚本，或在明确设置 `ARTFETCH_WIPE_DATA=1` 时执行完全重装。

## 3. 非目标

- 不设计 Kubernetes、多机高可用、蓝绿发布或自动扩缩容。
- 不托管、生成或保存生产密钥。
- 不自动开放数据库、Jupyter 或后端调试端口到公网。
- 不新增 ArtFetch 应用内 API、页面或权限码。
- 不在没有明确授权时删除 PostgreSQL volume、本地图片或日志。

## 4. 推荐交互流程

当用户要求部署或升级时，Skill 应先确认目标是：

- 只给服务器命令。
- 通过 SSH 协助检查服务器状态。
- 在用户明确授权后，通过 SSH 执行安装/升级脚本。

如果用户只需要命令，给出：

```bash
bash install-or-upgrade-latest.sh
```

私有仓库：

```bash
GITHUB_TOKEN=<token> bash install-or-upgrade-latest.sh
```

自定义目录：

```bash
ARTFETCH_PROJECT_DIR=/data/artfetch bash install-or-upgrade-latest.sh
```

## 5. 服务器检查清单

只读检查：

```bash
docker --version
docker compose version
df -h /opt/artfetch
ls -la /opt/artfetch
```

服务状态：

```bash
cd /opt/artfetch
docker compose --env-file .env --env-file .env.release -f docker-compose.prod.yml ps
```

健康检查：

```bash
curl -fsS http://127.0.0.1:8080/actuator/health
curl -fsSI http://127.0.0.1:3000/
curl -sS -o /tmp/artfetch-auth-body -w '%{http_code}\n' http://127.0.0.1:3000/api/auth/me
```

预期 `/api/auth/me` 未登录返回 `401`。

## 6. 敏感信息规则

Skill 可以检查 `.env` 中某个变量是否存在，但不得输出真实值。

允许输出：

```text
POSTGRES_PASSWORD=set
ARTFETCH_ADMIN_PASSWORD=missing_or_empty
```

禁止输出：

```text
POSTGRES_PASSWORD=<真实密码>
```

## 7. 版本与制品规则

- 发布版本使用 `Vx.y.z`。
- 当前版本从 `V1.0.0` 开始。
- 发布版本必须等于最新 Flyway 迁移版本。
- 服务器只部署 GitHub 最新正式 Release。
- Draft 和 prerelease 不作为默认安装/升级来源。
- 所有运行镜像必须来自 Release 部署包内的 tar 文件。

## 8. 失败处理

安装或升级失败后，不做自动回滚。Skill 应指导用户：

1. 查看脚本输出和容器日志。
2. 修正 `.env`、磁盘空间或 Docker 环境问题。
3. 重新运行 `install-or-upgrade-latest.sh`。

只有用户明确要求完全清空并重装，才使用：

```bash
ARTFETCH_WIPE_DATA=1 bash install-or-upgrade-latest.sh
```

执行该模式前必须提醒用户会删除 PostgreSQL 数据卷、本地图片和日志等持久化数据。
