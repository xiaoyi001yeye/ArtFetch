# ArtFetch

ArtFetch 是一个艺术品数据采集与管理系统，包含 Spring Boot 后端、React 前端和 PostgreSQL 数据库。它支持按关键词创建采集任务、批量抓取拍品详情、在页面中检索和查看结果，并将数据导出为 Excel。

## 功能特性

- 按关键词创建采集任务，支持启动、暂停、恢复、取消和删除
- 自动抓取列表页与详情页，并按 `externalId` 去重更新
- 采集拍品编号、作者、材质、形制、尺寸、估价、拍卖信息、预展信息、描述等字段
- 详情页优先解析 `window.__INITIAL_STATE__` 中的结构化 JSON，DOM 解析作为兜底
- 支持按任务、关键词、作者、拍卖日期、拍品编号筛选结果
- 支持将筛选结果导出为 `.xlsx`

## 技术栈

- 后端：Spring Boot 3.2、Spring Data JPA、Jsoup、Apache POI
- 前端：React 18、TypeScript、Vite、Ant Design 5
- 数据库：PostgreSQL 16
- 部署：Docker Compose

## 系统架构

前端通过 `/api` 调用后端接口；后端负责任务调度、抓取解析、数据存储和 Excel 导出；PostgreSQL 持久化任务和拍品数据。

核心链路如下：

1. 创建检索任务
2. 抓取检索结果列表页，提取拍品基础信息和详情页链接
3. 逐条抓取详情页，优先从 `window.__INITIAL_STATE__` 提取结构化字段
4. 将结果按 `externalId` 写入或更新到 PostgreSQL
5. 在前端查看、筛选和导出数据

## 快速启动

### 方式一：服务器制品安装

适用于已经安装 Docker 和 Docker Compose plugin 的 Linux 服务器。该方式不需要 `git clone`，会从 GitHub Release 下载最新正式离线制品，校验后加载镜像并启动服务：

```bash
curl -fsSL https://raw.githubusercontent.com/xiaoyi001yeye/ArtFetch/main/scripts/deploy/install-or-upgrade-latest.sh | sudo env ARTFETCH_GITHUB_REPOSITORY=xiaoyi001yeye/ArtFetch ARTFETCH_PROJECT_DIR=/opt/artfetch bash
```

如果当前已经是 `root` 用户，可以去掉 `sudo env`：

```bash
curl -fsSL https://raw.githubusercontent.com/xiaoyi001yeye/ArtFetch/main/scripts/deploy/install-or-upgrade-latest.sh | ARTFETCH_GITHUB_REPOSITORY=xiaoyi001yeye/ArtFetch ARTFETCH_PROJECT_DIR=/opt/artfetch bash
```

安装完成后服务目录位于 `/opt/artfetch`。首次安装会自动生成 `/opt/artfetch/.env`，建议立即检查并妥善保存管理员密码、数据库密码和对象存储加密密钥。

### 方式二：Docker Compose

确保本机已安装 Docker 和 Docker Compose，然后在项目根目录执行：

```bash
docker compose up -d --build
```

启动完成后可访问：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`
- PostgreSQL：`localhost:5432`
- JupyterLab：`http://localhost:8888`

停止服务：

```bash
docker compose down
```

### 方式三：本地开发运行

#### 1. 启动 PostgreSQL

你可以直接使用本仓库的 Compose 仅启动数据库：

```bash
docker compose up -d postgres
```

默认数据库配置：

- 数据库名：`artfetch`
- 用户名：`artfetch`
- 密码：`artfetch123`

#### 2. 启动后端

建议使用 JDK 17+。

```bash
cd backend
mvn spring-boot:run
```

打包命令：

```bash
mvn package -DskipTests
```

运行测试：

```bash
mvn test
```

#### 3. 启动前端

```bash
cd frontend
npm install
npm run dev
```

前端开发服务默认运行在 `http://localhost:5173`，并代理 `/api` 到 `http://localhost:8080`。

## 配置说明

后端主要配置位于 [backend/src/main/resources/application.yml](backend/src/main/resources/application.yml)。

关键配置项：

- `artfetch.source.base-url`：上游数据源检索地址
- `artfetch.source.fetch-interval-seconds`：任务轮询间隔，`0` 表示只抓取一次
- `artfetch.source.request-delay-ms`：请求间隔毫秒数，用于控制抓取频率
- `artfetch.task.max-concurrent-tasks`：最大并发任务数
- `artfetch.task.thread-pool-size`：抓取线程池大小
- `artfetch.auth.artron-cookie`：雅昌登录后的完整 Cookie Header，用于抓取会员可见的成交价等字段
- `artfetch.auth.artron-account` / `artfetch.auth.artron-password`：如未直接提供 Cookie，可由后端自动登录换取雅昌 Cookie

如果需要通过 `.env` 覆盖 Compose 环境变量，请确保：

- `ARTWORK_SOURCE_URL` 指向实际可用的数据源检索地址
- PostgreSQL 相关变量与本地实际数据库保持一致
- 如需抓取登录后可见字段，可设置 `ARTFETCH_AUTH_ARTRON_COOKIE`
- 或设置 `ARTFETCH_AUTH_ARTRON_ACCOUNT`、`ARTFETCH_AUTH_ARTRON_PASSWORD`，由后端自动登录并刷新 Cookie

示例：

```bash
export ARTFETCH_AUTH_ARTRON_COOKIE='name1=value1; name2=value2; ...'
```

设置后，列表页、详情页、成交价补充和原图解析请求都会自动携带这个登录 Cookie。

如果你不想手动维护 Cookie，也可以：

```bash
export ARTFETCH_AUTH_ARTRON_ACCOUNT='your-account'
export ARTFETCH_AUTH_ARTRON_PASSWORD='your-password'
```

后端会在请求雅昌前自动登录并缓存会话 Cookie。

## 数据字段

当前拍品核心字段包括：

- `title`：拍品名称
- `lotNumber`：拍品编号
- `artist`：作者
- `medium`：材质
- `format`：形制
- `dimensions`：尺寸
- `description`：拍品描述
- `valuation`：估价
- `auctionHouse`：拍卖公司
- `auctionName`：拍卖会
- `auctionSession`：拍卖专场
- `auctionDate`：拍卖日期
- `auctionLocation`：拍卖地点
- `previewTime`：预展时间
- `previewLocation`：预展地点
- `imageUrl`：图片地址
- `sourceUrl`：来源页面地址
- `extraData`：未完全结构化但保留的附加信息

## 数据库迁移

数据库结构将统一交给 Flyway 管理。发布版本必须与最新 Flyway 迁移版本一致，例如当前版本从 `V1.0.0` 开始，对应 `backend/src/main/resources/db/migration/V1.0.0__baseline_artfetch_schema.sql`。

旧环境首次引入 Flyway 时按发布设计启用 baseline；生产环境不再依赖 `spring.jpa.hibernate.ddl-auto=update` 自动改表。历史手工迁移脚本 [docs/migrate.sql](docs/migrate.sql) 仅作为旧版本背景资料保留。

发布制品、服务器安装升级和版本规则见 [docs/design-github-actions-release-deployment.md](docs/design-github-actions-release-deployment.md)。

## Jupyter 成交价预测调试

仓库新增了一个独立的 `jupyter` 容器，用于直接连接 PostgreSQL，做成交价预测实验，不影响现有 Spring Boot 和前端服务。

启动方式：

```bash
docker compose up -d postgres jupyter
```

如果你也想在本地开发环境把整套应用一起拉起：

```bash
docker compose up -d --build
```

访问地址：

- `http://localhost:8888`

主要目录：

- `ml/notebooks/price_prediction_debug.ipynb`：人工调试 notebook 入口
- `ml/src/artfetch_ml/db.py`：数据库查询
- `ml/src/artfetch_ml/parsers.py`：中文金额、尺寸、日期解析
- `ml/src/artfetch_ml/features.py`：特征工程
- `ml/src/artfetch_ml/train.py`：XGBoost 训练与评估

当前 notebook 的训练标签来自 `artworks.extra_data` 中的 `transactionPrice`。也就是说，第一版不会改业务数据库结构，而是直接从已抓到的 JSON 附加字段里提取“成交价”。

默认流程：

1. 从 PostgreSQL 读取 `artworks`
2. 从 `extra_data.transactionPrice` 解析成交价
3. 从 `valuation`、`dimensions`、`artist`、`medium`、`auctionHouse` 等字段构造特征
4. 训练 `XGBRegressor`
5. 在 notebook 里查看指标、特征重要性和预测样例

如果你想调参，直接在 notebook 里修改 `xgb_params` 重新运行即可。

## 接口概览

### 任务接口

- `POST /api/tasks`：创建任务
- `GET /api/tasks`：分页查询任务
- `GET /api/tasks/{id}`：查询任务详情
- `POST /api/tasks/{id}/start`：启动任务
- `POST /api/tasks/{id}/pause`：暂停任务
- `POST /api/tasks/{id}/resume`：恢复任务
- `POST /api/tasks/{id}/cancel`：取消任务
- `DELETE /api/tasks/{id}`：删除任务及相关数据

### 拍品接口

- `GET /api/artworks`：分页查询拍品
- `GET /api/artworks/{id}`：查询拍品详情
- `GET /api/artworks/export`：导出 Excel

支持的查询参数：

- `taskId`
- `keyword`
- `artist`
- `auctionDate`
- `lotNumber`

## 模拟评估流程回归

如果你想把“创建评估项目 → 专家评估 → 驳回重提 → 审核通过”这条链路手动跑一遍，并自动生成一份评估报告，可以直接执行：

```bash
./scripts/run-evaluation-scenario.sh
```

默认行为：

- 调用本地后端 `http://localhost:8080/api`
- 使用 `admin / 12345678` 登录
- 按作者关键词 `周春芽` 自动挑选 2 件艺术品
- 自动创建临时专家、临时审核人、临时指标和模板
- 自动生成：
  - `docs/generated/evaluation-scenario-report-<timestamp>.md`
  - `docs/generated/evaluation-scenario-report-<timestamp>.json`

可选环境变量：

```bash
ARTFETCH_BASE_URL=http://localhost:8080/api \
ARTFETCH_ADMIN_USERNAME=admin \
ARTFETCH_ADMIN_PASSWORD=12345678 \
ARTFETCH_SCENARIO_ARTIST=周春芽 \
ARTFETCH_REPORT_OUTPUT_DIR=docs/generated \
./scripts/run-evaluation-scenario.sh
```

说明：

- 脚本会自动禁用临时用户，并删除临时指标与模板。
- 已完成项目不会自动删除，因为系统规则已限制完成态项目不可删除；报告中会保留项目 ID 方便后续追踪。

## 采集逻辑说明

当前抓取流程分为两阶段：

1. 列表页抓取：提取拍品标题、基础估价、拍卖公司、拍卖日期、详情页地址和缩略图
2. 详情页补全：优先解析页面中的 `window.__INITIAL_STATE__` JSON，补全详细字段；若结构化数据缺失，再由 DOM 提取器兜底

这一策略可以提升字段完整性，并减少页面结构轻微变化带来的解析失败。

## 目录结构

```text
ArtFetch/
├── backend/                # Spring Boot 后端
├── frontend/               # React + Vite 前端
├── docs/                   # 设计文档与迁移脚本
├── download/               # 样例页面与实验性提取代码
├── docker-compose.yml      # 本地一键启动
└── README.md
```

## 开发建议

- 抓取频率不要设置过高，避免目标站点限流
- 优先使用样例 HTML 回归验证字段提取逻辑
- 修改字段定义时，同步检查实体、DTO、筛选条件、导出逻辑和前端展示

## License

当前仓库未单独声明开源许可证。如需公开分发，建议补充明确的 License 文件。
