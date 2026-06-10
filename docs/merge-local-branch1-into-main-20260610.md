# local-branch1 合并到 main 决策记录

日期：2026-06-10

## 背景

`local-branch1` 的目标是增加高清大图 V2 canonical TOS 读取能力。`main` 后续已经通过 `710ef23 Add canonical TOS HD image flow` 引入了大部分同方向能力，并通过 `3eab95d docs: forbid direct HD image reads in test` 增加了测试环境约束。

## 冲突处理

- canonical object key 测试保留 `main` 的最终规则：固定 `artfetch/hd-images/v2` 前缀、按 `sourceProvider + artCode` 做 hash 分片，不依赖 `object_storage_configs.path_prefix`。
- 作品详情页保留 `main` 的“高清大图”文案和单个 V2 按钮，去掉合并产生的重复按钮。
- 图片查看页保留 `高清大图` 作为默认高清入口名称，并把错误提示合并为同时覆盖 `TOS_CANONICAL`、`DUAL_READ` 和 `LEGACY` 模式。
- 保留 `local-branch1` 的全量 canonical 升级工具 `HdImageCanonicalUpgradeAllTest`，但明确标记为操作员手动触发的数据升级 helper，默认不会运行。
- 保留 `local-branch1` 为升级工具需要的 `HdImageObjectStorageService.copyObject(...)`。

## 注意事项

- `HdImageCanonicalUpgradeAllTest` 只有在显式设置 `ARTFETCH_HD_CANONICAL_UPGRADE_ALL=true` 时才会运行。
- 常规测试或测试环境验证不应直接读取高清图大文件流；验证上传结果优先使用 metadata、object key、size、ETag 等非流式信息。
- 默认高清图读取仍由 `artfetch.image.hd-display-mode` 决定，当前默认值是 `TOS_CANONICAL`。
