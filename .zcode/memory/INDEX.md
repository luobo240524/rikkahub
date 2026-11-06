---
summary: 记忆库索引,活跃任务与最近快照指针
created: 2026-11-01 22:09
updated: 2026-11-07
status: active
---

# 记忆索引

> 本文件是记忆库唯一入口,≤80 行。只放指针与状态,细节在分层文件与快照里。

## 活跃任务

- [2026-11-07] rikkahub 二改维护 | 二期已交付 r198(含一期修复三轮),等用户合并验收 | project.md
- [2026-11-07] 三期候选:打卡提醒限频/深色适配/预设主题包/热力图徽标/朋友圈图片 | 等用户拍板 | project.md

## 下一步

- 用户验收 r198(arm64,gh-proxy 24KB/s):升级前先备份;验收玩法=助手打卡/发朋友圈/读心情
- 三期功能全部进发现页;打卡摘要同步记忆库+当日状态注入系统提示(遗留项)
- 构建失败先拉 `gh run view --log-failed`;push 后 45s 查触发,没触发就 workflow_dispatch 手动补(lessons.md)

## 最近快照

- [2026-11-07] handoffs/二期发现页交付r198一期修复三轮.memo.md — active
- [2026-11-04] handoffs/气泡一期落地r192与二期蓝图.memo.md — absorbed
- [2026-11-01 22:09] handoffs/r181修复生图事故并结案.memo.md — absorbed
