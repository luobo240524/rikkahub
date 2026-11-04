---
summary: RikkaHub 二改维护:目标、结构与任务状态
created: 2026-11-01 22:09
updated: 2026-11-04
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

- [2026-11-04] **一期已完成交付(r192 构建成功)**:材质下拉+磨砂/清透度/不透明度条件滑杆+分侧边框美化
  (四角贴纸/HSL取色器/全半包)+贴角圆角形状+功能收纳壳+mini实时预览+120条模糊降级守卫;
  代码 034ededb,编译修复 c3c93225/009ab1da/0c4d5f61,等用户手机验收
- [2026-11-04] **二期待开工(蓝图已定稿)**:发现聚合页+朋友圈/打卡独立页面+助手四工具
  (mood_checkin/moments_post/mood_read/moments_read);蓝图全文 `phase2-blueprint.md`
  (含 navigation3 路由/抽屉/Room 手写迁移 DDL/工具注册路径等已探明底座事实,照单执行)
- [2026-11-04] 三期候选:打卡提醒限频/深色适配/预设主题包/热力图与连续徽标/朋友圈图片发帖
- r188(唤醒修复)/r192(一期)均已交付;远程 HEAD=0135af2c 与本地同步
