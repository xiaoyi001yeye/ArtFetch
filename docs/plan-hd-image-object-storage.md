# ArtFetch 高清大图对象存储支持实施任务计划

编写日期：2026-05-17

关联设计文档：`docs/design-hd-image-object-storage-migration.md`

## 1. 计划目标

本计划用于把“高清大图迁移到对象存储”拆成可执行任务，覆盖后端、前端、数据库、权限、迁移、部署和验证。

实施完成后，系统应具备：

- 管理员可以在页面保存、测试、启用对象存储配置。
- 高清大图可以从本地迁移到对象存储。
- 管理员可以看到迁移任务进度、成功数、失败数和失败明细。
- 迁移任务支持全量、增量、失败重试。
- 已迁移高清图仍通过现有鉴权接口访问。
- 新下载高清图可按配置写入本地、对象存储或本地加对象存储。

第一期对象存储供应商暂定为火山引擎 TOS。页面和后端暂不做多云选择，后续再扩展到 S3 兼容或其他云厂商。

## 2. 推荐排期

建议按 5 个阶段实施。第一阶段到第三阶段为必须交付，第四阶段建议紧随其后，第五阶段作为稳定后增强。

| 阶段 | 名称 | 目标 | 建议优先级 |
|---|---|---|---|
| P0 | 数据与配置基础 | 数据库、权限码、配置 API、连接测试 | 必做 |
| P1 | 对象存储读取能力 | 后端支持从对象存储读取高清图 | 必做 |
| P2 | 迁移任务与进度 | 支持历史高清图迁移和进度页面 | 必做 |
| P3 | 新高清图写入对象存储 | 下载高清图时支持本地/对象/双写 | 建议 |
| P4 | 清理、运维与优化 | 本地清理、批量重试、性能优化 | 稳定后做 |

## 3. P0 数据与配置基础

### 3.1 数据库 Schema 扩展

任务：

- 新增 `object_storage_configs` 表。
- 扩展 `artworks` 表高清图对象存储字段。
- 新增 `hd_image_migration_tasks` 表。
- 新增 `hd_image_migration_items` 表。
- 新增必要索引。
- 在启动维护逻辑或迁移 SQL 中保证老数据兼容默认值。

涉及模块：

- `backend/src/main/java/com/artfetch/entity`
- `backend/src/main/java/com/artfetch/config/SchemaMaintenanceService.java`
- `backend/src/main/resources/application.yml`
- `docs/migrate.sql`，如果项目继续维护手动 SQL

验收标准：

- 老库升级后应用能正常启动。
- 老高清图记录默认 `hd_image_storage_type=LOCAL`。
- 老高清图记录默认 `hd_image_migration_status=NOT_MIGRATED`。
- 现有艺术品列表、详情、高清图查看不受影响。

### 3.2 权限码和审计动作

任务：

- 新增权限码：
  - `settings:object-storage:view`
  - `settings:object-storage:manage`
  - `hd-image:migration:view`
  - `hd-image:migration:manage`
- 在 `AuthDataInitializer` 中初始化权限。
- 默认只授予管理员角色。
- 新增审计 action 常量或约定。

涉及模块：

- `backend/src/main/java/com/artfetch/auth/config/AuthDataInitializer.java`
- `backend/src/main/java/com/artfetch/auth/support/PermissionCodes.java`
- `frontend/src/auth/permissions.ts`
- `docs/design-auth-sa-token.md`
- `docs/prd-users-roles-permissions.md`

验收标准：

- 管理员可看到对象存储配置和迁移菜单。
- 非授权用户无法访问新增 API。
- 配置保存、启用、禁用、测试连接、迁移启动、取消、重试均记录审计日志。
- 审计日志不包含密钥明文。

### 3.3 对象存储配置后端

任务：

- 新增 `ObjectStorageConfig` 实体。
- 新增 Repository。
- 新增 DTO：
  - 配置详情 DTO
  - 创建/更新请求 DTO
  - 测试连接结果 DTO
- 新增 `ObjectStorageConfigService`。
- 新增 `ObjectStorageConfigController`。
- 支持配置保存、更新、启用、禁用、测试连接。
- Secret Key 加密保存，前端不回显。

建议 API：

```http
GET /api/settings/object-storage
POST /api/settings/object-storage
PUT /api/settings/object-storage/{id}
POST /api/settings/object-storage/{id}/test
POST /api/settings/object-storage/{id}/enable
POST /api/settings/object-storage/{id}/disable
```

验收标准：

- 可以保存火山引擎 TOS 配置。
- 编辑配置时 Secret Key 留空可以保留原值。
- 启用配置前必须测试成功。
- 同一时刻最多一个配置为 `enabled=true`。
- 密钥字段不会出现在 API 响应和日志中。

### 3.4 火山 TOS 客户端封装

任务：

- 引入火山引擎 TOS Java SDK 依赖。
- 新增 `ObjectStorageClientFactory`。
- 新增 `HdImageObjectStorageService`。
- 支持：
  - 测试 bucket 访问。
  - 上传对象。
  - 检查对象是否存在。
  - 获取对象 metadata。
  - 读取对象流。
  - 删除测试对象。

验收标准：

- 能连接火山引擎 TOS。
- 测试连接会写入并删除临时对象。
- endpoint、region、bucket 配置生效。
- 对象存储错误能转换成清晰中文错误信息。

火山 TOS 配置约束：

- `provider` 固定为 `VOLCENGINE_TOS`。
- `sdk_mode` 固定为 `VOLCENGINE_TOS_SDK`。
- Region 必填，例如 `cn-beijing`、`cn-shanghai`。
- Endpoint 默认使用普通 TOS Endpoint，例如 `tos-cn-beijing.volces.com`。
- 如果未来改用 AWS S3 SDK 兼容方式，才使用 `tos-s3-{region}.volces.com` 这类 S3 Endpoint。

## 4. P1 对象存储读取能力

### 4.1 高清图读取逻辑改造

任务：

- 扩展 `Artwork` 实体高清图对象存储字段。
- 修改 `HdImageService.loadHdImage`。
- 当 `hd_image_storage_type=LOCAL` 时继续读取本地文件。
- 当 `hd_image_storage_type=OBJECT` 时读取对象存储。
- 当 `hd_image_storage_type=LOCAL_OBJECT` 时优先读取对象存储，失败时回退本地。
- 保持 `GET /api/artworks/{id}/hd-image` 接口不变。

验收标准：

- 老数据仍可以正常查看高清图。
- 手动构造对象存储字段后，可以通过原接口读取对象存储图片。
- 对象存储失败但本地文件存在时，`LOCAL_OBJECT` 可以回退成功。
- 对象存储失败且无本地回退时，返回清晰错误。

### 4.2 前端兼容验证

任务：

- 确认 `ArtworkDetailPage` 不需要改 URL。
- 确认评估专家页点击高清图仍能走受保护接口。
- 如新增状态展示，补充对象存储迁移状态标签。

验收标准：

- 详情页“查看高清无损图”行为不变。
- 权限仍由 `artwork:image:view` 控制。
- 前端不直接拼对象存储 URL。

## 5. P2 迁移任务与进度

### 5.1 迁移任务后端

任务：

- 新增 `HdImageMigrationTask` 实体。
- 新增 `HdImageMigrationItem` 实体。
- 新增 Repository。
- 新增 DTO 和 Controller。
- 新增 `HdImageMigrationService`。
- 支持创建任务、启动、暂停、恢复、取消、查询详情、查询明细、重试失败项。

建议 API：

```http
GET /api/hd-image-migrations
POST /api/hd-image-migrations
GET /api/hd-image-migrations/{id}
POST /api/hd-image-migrations/{id}/start
POST /api/hd-image-migrations/{id}/pause
POST /api/hd-image-migrations/{id}/resume
POST /api/hd-image-migrations/{id}/cancel
GET /api/hd-image-migrations/{id}/items
POST /api/hd-image-migrations/{id}/retry-failed
```

验收标准：

- 可以创建 `FULL`、`INCREMENTAL`、`RETRY_FAILED` 三种模式任务。
- 可以限制范围为全部或指定检索任务。
- RUNNING 任务能持续更新进度。
- 单件失败不导致整个任务立即失败。
- 支持暂停、恢复、取消。
- 失败项可以重试。

### 5.2 增量候选扫描

任务：

- 实现全量扫描：
  - `hd_image_status=DOWNLOADED`
  - `hd_image_path` 非空
- 实现增量扫描：
  - 未迁移
  - 迁移失败
  - 对象 key 为空
  - 当前仍为 `LOCAL`
- 实现失败重试扫描：
  - `hd_image_migration_status=FAILED`

验收标准：

- 已成功迁移的数据不会在增量任务中重复上传。
- 对象存储中已存在且大小一致的对象会标记成功并跳过上传。
- 对象存储中已存在但大小不一致时不覆盖，记录失败。

### 5.3 迁移执行器

任务：

- 支持上传并发配置，默认 4。
- 支持批量读取待迁移项。
- 每个 item 独立事务更新状态。
- 上传成功后更新 `artworks` 对象存储字段。
- 上传失败后记录 item 错误和 artwork 迁移错误。
- 连续失败超过阈值时 fail fast。

验收标准：

- 大量数据迁移时进度准确。
- 中断后可以通过增量任务继续迁移。
- 同一时刻最多运行一个高清图迁移任务。
- 应用重启后 RUNNING 任务不会永久卡住，需恢复为 PAUSED 或 FAILED。

### 5.4 迁移前端页面

任务：

- 新增页面 `/hd-image-migrations`。
- 新增菜单项“高清图迁移”。
- 列表展示迁移任务。
- 创建任务弹窗支持：
  - 迁移模式
  - 迁移范围
  - 目标检索任务
  - 对象存储配置
  - 并发数
- 详情页展示进度和明细。
- 支持按明细状态筛选。
- RUNNING 状态轮询刷新。

验收标准：

- 可以看到任务总数、已处理、成功、跳过、失败。
- 进度条百分比正确。
- 可以查看失败项错误信息。
- 有权限的管理员可以启动、暂停、恢复、取消、重试失败。
- 无权限用户看不到菜单或操作按钮，且 API 返回 403。

## 6. P3 新高清图写入对象存储

### 6.1 后端配置

任务：

- 新增配置项：

```yaml
artfetch:
  image:
    hd-storage-mode: LOCAL_ONLY
    migration:
      max-concurrent-tasks: 1
      upload-concurrency: 4
      batch-size: 100
      fail-fast-threshold: 50
      delete-local-after-migrated: false
```

- 在 `AppProperties` 中建模。
- 支持环境变量覆盖。

验收标准：

- 默认仍为 `LOCAL_ONLY`，不改变当前行为。
- 切换为 `LOCAL_AND_OBJECT` 后，新高清图保存本地并上传对象存储。
- 切换为 `OBJECT_ONLY` 后，新高清图只保存对象存储。

### 6.2 高清图下载服务改造

任务：

- 修改 `HdImageService.ensureHdImageStoredWithMetrics`。
- 拼接 PNG 后按模式保存：
  - `LOCAL_ONLY`
  - `LOCAL_AND_OBJECT`
  - `OBJECT_ONLY`
- 上传对象存储后写入 `hd_image_object_*` 字段。
- 双写模式下设置 `hd_image_storage_type=LOCAL_OBJECT`。
- 对象模式下设置 `hd_image_storage_type=OBJECT`。

验收标准：

- 当前补高清图任务不受影响。
- 对象存储未启用时，不能切换到对象写入模式。
- 双写失败时，业务状态应清晰：
  - 本地成功、对象失败：高清图仍可用，但迁移状态为 FAILED。
  - 本地失败：整体失败。

## 7. P4 清理、运维与优化

### 7.1 本地文件清理任务

任务：

- 新增本地高清图清理任务。
- 清理前检查对象存储对象存在且大小一致。
- 删除后将 `hd_image_storage_type` 从 `LOCAL_OBJECT` 改为 `OBJECT`。
- 提供 dry-run 模式。

验收标准：

- 不会删除未迁移成功文件。
- 不会删除对象存储缺失或大小不一致的文件。
- 清理操作有审计日志和明细。

### 7.2 运维统计

任务：

- 增加迁移统计接口：
  - 本地高清图数量
  - 已迁移数量
  - 失败数量
  - 对象存储总大小
  - 本地可清理大小
- 在迁移页面顶部展示概览。

验收标准：

- 管理员能看到迁移总体覆盖率。
- 能估算本地磁盘可释放空间。

### 7.3 上传性能优化

任务：

- 根据单张文件大小评估是否启用 multipart upload。
- 增加上传超时、重试、限速配置。
- 大批量迁移时减少数据库频繁写入，可按批更新任务聚合进度。

验收标准：

- 大图迁移不会长时间阻塞业务请求。
- 对象存储临时波动时可自动重试。
- 迁移速度和失败率可观测。

## 8. 测试计划

### 8.1 后端单元测试

覆盖：

- 对象 key 生成规则。
- Secret Key 加密和脱敏。
- 增量候选扫描条件。
- 迁移状态流转。
- 本地、对象、双存储读取分支。

### 8.2 后端集成测试

建议使用火山引擎 TOS 测试 bucket，或在开发环境准备一个隔离的测试 bucket：

- 保存对象存储配置。
- 测试连接。
- 上传高清图。
- 从对象存储读取高清图。
- 迁移任务成功。
- 迁移任务失败重试。

### 8.3 前端测试

覆盖：

- 对象存储配置页面表单校验。
- Secret Key 不回显。
- 测试连接状态展示。
- 迁移任务创建。
- 迁移进度轮询。
- 失败明细筛选。
- 权限控制。

### 8.4 回归测试

必须验证：

- 搜索任务创建、启动、暂停、恢复、取消。
- 高清图补充任务。
- 艺术品详情页高清图查看。
- 专家评估页高清图查看。
- Excel 导出不受影响。
- 权限和审计日志正常。

## 9. 发布计划

### 9.1 发布前

- 备份 PostgreSQL。
- 备份 `storage/original-images` 目录。
- 准备对象存储 bucket。
- 准备最小权限 access key。
- 在测试环境完成一次小范围迁移。

### 9.2 发布步骤

1. 发布 P0 到 P1 代码，保持 `hd-storage-mode=LOCAL_ONLY`。
2. 在生产页面保存对象存储配置。
3. 测试连接通过后启用配置。
4. 抽取一个小检索任务执行增量迁移。
5. 抽查高清图访问。
6. 执行全量增量迁移。
7. 失败项重试。
8. 稳定后开启 `LOCAL_AND_OBJECT`。

### 9.3 回滚步骤

如果对象存储访问异常：

1. 禁用对象存储配置。
2. 将 `hd-storage-mode` 改回 `LOCAL_ONLY`。
3. 将 `LOCAL_OBJECT` 记录临时切回 `LOCAL`：

```sql
update artworks
set hd_image_storage_type = 'LOCAL'
where hd_image_storage_type = 'LOCAL_OBJECT';
```

4. 重启后端服务。
5. 验证本地高清图访问。

## 10. 建议 Issue 拆分

### 后端

- BE-01 新增对象存储配置实体、表结构和权限码。
- BE-02 实现对象存储配置 API、密钥加密、审计日志。
- BE-03 引入火山 TOS Java SDK 客户端并实现连接测试。
- BE-04 扩展 artworks 高清图对象存储字段。
- BE-05 改造高清图读取逻辑，支持本地和对象存储。
- BE-06 新增迁移任务实体、明细实体和 API。
- BE-07 实现增量扫描和迁移执行器。
- BE-08 实现迁移暂停、恢复、取消、失败重试。
- BE-09 改造高清图下载服务，支持对象存储写入模式。
- BE-10 增加测试和生产回滚脚本。

### 前端

- FE-01 新增对象存储配置 API 类型和请求封装。
- FE-02 新增对象存储配置页面。
- FE-03 新增迁移任务 API 类型和请求封装。
- FE-04 新增高清图迁移列表页。
- FE-05 新增迁移明细页和失败项筛选。
- FE-06 接入权限控制、菜单显示和按钮显示。
- FE-07 完成前端构建和页面回归。

### 文档与部署

- DOC-01 更新权限设计文档。
- DOC-02 更新部署文档，补充对象存储环境变量和 bucket 准备步骤。
- DOC-03 编写生产迁移操作手册。
- OPS-01 准备火山引擎 TOS bucket、AK/SK 和最小权限策略。
- OPS-02 准备备份和回滚检查清单。

## 11. 第一版最小可交付范围

如果需要压缩范围，第一版建议只做：

- 对象存储配置保存和测试连接。
- 高清图对象存储字段。
- 原高清图接口支持对象存储读取。
- 增量迁移任务。
- 迁移任务列表和进度。
- 失败明细和失败重试。
- 新图仍保持 `LOCAL_ONLY`，等历史迁移稳定后再做双写。

不建议第一版做：

- 删除本地文件。
- 前端直接访问对象存储 URL。
- 多对象存储配置同时生效。
- CDN 加速。
- 原图迁移。

## 12. 完成定义

本项目可以认为完成，当且仅当：

- 管理员能配置并测试对象存储。
- 至少一个历史检索任务的高清图可以迁移成功。
- 迁移进度和失败明细可见。
- 已迁移高清图可以通过原受保护接口访问。
- 增量迁移不会重复上传已成功迁移对象。
- 失败项可以重试。
- 权限、审计、构建、后端测试均通过。
- 生产回滚路径已验证。

## 13. 火山 TOS 官方参考

- 火山引擎对象存储 TOS Java SDK 简介：`https://www.volcengine.com/docs/6349/79895`
- 火山引擎 TOS 地域和访问域名：`https://www.volcengine.com/docs/6349/107356`
- 火山引擎 TOS 使用 AWS S3 SDK 访问说明：`https://www.volcengine.com/docs/6349/2387330`
