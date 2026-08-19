# v2.1.0 实现进度记录

记录日期：2026-08-19

## 已完成的本地能力提交

- `347c904` — 建立 v2.1.0 内部开发基线。
- `d04db12` — 移除数据库升级中的破坏性 `DROP TABLE` fallback，保留 versioned migration skeleton。
- `003a40d` — STOP 正确消费 `OVERRIDE_END_TIME`，修复延迟结算结束时间。
- `c43b999` — 清理重复通知权限，并为动态 Receiver 使用显式兼容 flag。
- `0a18b1a` — 默认 CSV 备份改为应用可控路径，并增加应用内备份列表入口。
- `92f5081` — 加强 MediaStore 失败回退、半成品清理、文件大小验证和 CSV 字段转义。
- `a87e865` — 暖米白低饱和主题整理，保留原有 `orange` preference key。
- `68cd0ec` — 增加独立 BYD API Probe，使用 reflection 只读探测 OEM 接口。

## 当前备份行为

默认“立即备份”顺序：

1. Android 10+ 尝试 `Download/CarTimer/`。
2. MediaStore 创建或写入失败时，回退到应用专用 `files/backups/`。
3. UI 显示阶段、错误类型、记录数、文件名和位置。
4. 系统文件选择器保留为可选导出/导入路径。
5. “查看可恢复备份”扫描公共 Download/CarTimer 和应用专用目录。

## 尚未由车机确认的事项

- 目标车机实际 MediaStore 写入结果。
- 目标车机实际应用专用目录可见性与恢复结果。
- v2.0.0 真实历史日志的导出数量和 CSV 完整性。
- 自动启停、悬浮窗、划掉任务和关机结算的实车回归。
- BYD OEM API 的普通签名权限结果。

上述事项仍标记为 `UNVERIFIED`，不能作为发布通过条件的替代证据。BYD Probe 必须保持独立，不接入正式计时、数据库或 UI 业务。

Probe 已可本地构建，但尚未在目标车机安装运行，因此 class、权限和 raw mileage 结果仍为 `UNVERIFIED`。

## 提交纪律

上述提交均为本地提交，未主动推送。后续修改继续按单一能力拆分，避免同时修改 TimerService、数据库、BYD 代码、UI 和 CSV。
