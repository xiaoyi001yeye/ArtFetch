# ArtFetch 制品发布与服务器部署历史文档

状态：历史文档，已被取代

更新日期：2026-06-01

ArtFetch 发布制品与服务器安装升级逻辑的唯一事实来源是：

- [GitHub Actions 离线制品发布与服务器安装升级设计](design-github-actions-release-deployment.md)

本文原先记录过 registry/GHCR、服务器拉镜像、服务器拉代码构建、本地发布脚本演练、日期版本号等早期方案。这些方案不再作为实现依据。

## 当前有效口径

- GitHub Actions 构建发布制品。
- GitHub Release 附件承载完整离线部署包。
- 目标服务器运行 `install-or-upgrade-latest.sh` 下载最新正式 Release 附件。
- 目标服务器只执行 `docker load` 和 `docker compose up`。
- 目标服务器不执行 `docker pull`、`docker build`、`npm run build`、`mvn package`。
- 发布版本使用 `Vx.y.z`，当前从 `V1.0.0` 开始。
- 发布版本必须等于最新 Flyway 迁移版本。
- 运行所需全部镜像都必须作为 tar 包进入 Release 部署包，包括 `postgres:16-alpine`。

## 废弃口径

以下内容如在旧提交、旧评论或外部笔记中出现，均应视为废弃：

- `YYYY.MM.DD.N` 日期版本号。
- GHCR 或其它 registry 作为生产服务器运行镜像来源。
- 生产服务器执行 `docker pull` 获取镜像。
- 生产服务器执行源码构建。
- GitHub Actions 通过 SSH 主动部署生产服务器。
- 手工 SQL 作为长期数据库迁移机制。

需要查看具体制品结构、manifest schema、GitHub Actions 设计和服务器脚本行为时，只阅读主设计文档。
