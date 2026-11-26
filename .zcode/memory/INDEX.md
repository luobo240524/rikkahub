---
summary: 记忆库索引,活跃任务与最近快照指针
created: 2026-11-01 22:09
updated: 2026-11-27
status: active
---

# 记忆索引

> 本文件是记忆库唯一入口,≤80 行。只放指针与状态,细节在分层文件与快照里。

## 活跃任务

- [2026-11-27] 你的世界:r365 已交付待实机验收(信件到点通知+深链/冷却弹层/封面头像/留灯邮局字体壁纸/打卡键盘) | 进行中 | handoffs/r364-r365交付与四项验收修复.memo.md
- [2026-11-27] 留灯(lights)去留待定:数据与 light_lamp 工具仍在,envelope 入口暂无 lights 卡 | 待决定 | project.md
- [2026-11-21] 内心世界:方案B已拍板三问待拍板(心声源=兜底分析器扩 inner_voice) | project.md
- [2026-11-21] ASR 录音态卡死观察线(发送按钮无声消失,用户观察复现中) | project.md
- [2026-11-21] 验收欠账:r280 九项+r316-r336 全欠;候选池=通话轮工具白名单/免提静音/来电通知直达/纪念日早晨问候 | project.md

## 下一步

- 实机验收 r365:先 WebDAV 备份 → 装包 → 信件到点通知/深链自动开信、宠物冷却弹层、封面头像、留灯邮局字体壁纸、打卡键盘
- 若封面头像仍空白:logcat `WebView` 标签 + `.ava img` naturalWidth/avatarUrl 探针;备选=首帧后延迟设图或暂时禁用 glow 动画
- 决定 lights 页去留:envelope 加第三张卡,或彻底退休 `world_lights`/`light_lamp`

## 最近快照

- [2026-11-27] handoffs/r364-r365交付与四项验收修复.memo.md — active
- [2026-11-26] handoffs/r361验收修复与信件一期未完成.memo.md — absorbed
- [2026-11-25] handoffs/你的世界r336交付与TA工具接入.memo.md — absorbed
- [2026-11-22] handoffs/你的世界四页预览与壁纸兜底.memo.md — absorbed
- [2026-11-22] handoffs/你的世界拍板与花园宠物页定稿.memo.md — absorbed
