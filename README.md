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

### 方式一：Docker Compose

确保本机已安装 Docker 和 Docker Compose，然后在项目根目录执行：

```bash
docker compose up -d --build
```

启动完成后可访问：

- 前端：`http://localhost:3000`
- 后端：`http://localhost:8080`
- PostgreSQL：`localhost:5432`

停止服务：

```bash
docker compose down
```

### 方式二：本地开发运行

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

如果需要通过 `.env` 覆盖 Compose 环境变量，请确保：

- `ARTWORK_SOURCE_URL` 指向实际可用的数据源检索地址
- PostgreSQL 相关变量与本地实际数据库保持一致

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

如果你是在旧版本数据库基础上升级，请在重启应用前手动执行迁移脚本：

- [docs/migrate.sql](docs/migrate.sql)

当前迁移主要包括：

- `year` 重命名为 `auction_date`
- `collection` 重命名为 `auction_house`
- 新增 `lot_number`
- 删除旧的 `category`

新环境首次启动时，`spring.jpa.hibernate.ddl-auto=update` 会自动补充大部分表结构；已有历史数据时仍建议先执行迁移脚本。

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
