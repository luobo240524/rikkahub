---
summary: 记忆库索引,活跃任务与最近快照指针
created: 2026-11-01 22:09
updated: 2026-11-14
status: active
---

# 记忆索引

> 本文件是记忆库唯一入口,≤80 行。只放指针与状态,细节在分层文件与快照里。

## 活跃任务

- [2026-11-14] 语音通话十期 r266 交付待验收 | 挂后台/按住说话/提速(工具全清+软切句+专用模型生效)/断句1200ms/记忆召回修复 | project.md / handoffs/通话提速与记忆修复r266交付待验收.memo.md

## 下一步

- 验收 r266:通话延迟(预期30-60s→10s上下)、通话专用模型切换生效、断句手感、记忆召回、按住说话、挂后台悬浮球/通知、MiMo 白桦固定
- 用户若反馈通话需要个别工具(如 music_control),做成通话轮工具白名单
- 构建失败先拉 `gh run view --log-failed`;push 断连重试(45-60s 步进);交付链接纯文本代码块

## 最近快照

- [2026-11-14] handoffs/通话提速与记忆修复r266交付待验收.memo.md — active
- [2026-11-11] handoffs/七期r240交付与九期可行性评估.memo.md — absorbed
- [2026-11-10] handoffs/五期六期迭代交付r230待验收.memo.md — absorbed
- [2026-11-08] handoffs/四期交付r214与通话入口迭代.memo.md — absorbed
- [2026-11-08] handoffs/r209交付与自检机制落地.memo.md — absorbed
