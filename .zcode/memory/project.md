---
summary: RikkaHub 二改维护:目标、结构与任务状态
created: 2026-11-01 22:09
updated: 2026-11-07
status: active
---

# 项目状态

## 目标

维护 luobo240524/rikkahub 二改版(上游 rikkahub/rikkahub,v2.5.6 基础):消息分条(按助手开关)、
MD3 Expressive 风格、安全模式急救等自定义功能;GitHub Actions 云端构建 APK 发布供手机覆盖安装。

## 结构

- 工作分支 `custom-2.5.6`(master 仅上游参考);push 该分支自动触发 `.github/workflows/custom-build.yml`
- 多模块:app(UI/逻辑)、ai(厂商抽象)、common、document、search、speech、web、workspace 等
- `app/src/dist/` 源集:应用名"RikkaHub X"、X 角标图标、包名 me.rerere.rikkahub.debug
- 根目录 `HANDOFF_IMGGEN_FREEZE.md`:生图卡死事故完整交接(按节追加,勿重写旧节)
- `.zcode/skills/` meow-handoff/meow-recall;`.zcode/memory/` 本记忆库

## 进行中

- [2026-11-07] **二期已交付(r198 构建成功),等用户合并验收**:发现聚合页(收纳菜单+抽屉双入口)+朋友圈
  (文字feed+常驻发布框)+心情打卡(月历实心点/圆环+emoji+短语+历史)+助手四工具(mood_checkin/mood_read/
  moments_post/moments_read,LocalToolOption 两组开关)+Room 27→28 纯增量迁移(mood_checkins/moments_posts);
  代码 aed11a07+19a76dca;交付 gh-proxy 24KB/s;验收前提醒备份
- [2026-11-07] 一期修复三轮已并入 r198:r194(贴合/材质tint/贴纸自定义/编辑对象三选/深浅/间距滑杆)
  +r196(编辑对象语义/种子😀/瓷白/outline描边/唤醒带note)
- [2026-11-04] 三期候选:打卡提醒限频/深色适配/预设主题包/热力图与连续徽标/朋友圈图片发帖;
  新功能一律进发现页;打卡摘要同步记忆库+当日状态注入系统提示(遗留项可归三期)
- 远程 HEAD=19a76dca 与本地同步;r188/r192/r194/r196/r198 均已交付
