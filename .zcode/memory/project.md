---
summary: RikkaHub 二改维护:目标、结构与任务状态
created: 2026-11-01 22:09
updated: 2026-11-01 22:09
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

- [2026-11-03] 唤醒bug第二形态修复(已推 dcd9109c,r188 构建中):仅"用户可见内容"才截断保留,仅思考/工具调用→回滚重试
- [2026-11-03] 气泡优化+心情打卡+朋友圈:交互预览已完成两轮迭代(可视化 HTML),**等用户说"构建"再写实机代码**;要点:材质/形状折叠下拉;磨砂(新,磨砂程度滑杆仅磨砂显示);玻璃优化(hyalite液态玻璃,清透度仅玻璃显示);不透明度移助手页仅默认材质显示;边框美化分侧配置(助手/用户气泡分开,四角贴纸统一,emoji自动趴框线,颜色下拉+自定义,粗细,全包/半包);新形状"贴角圆角"(靠头像一角收小);功能收纳(layout-grid图标,可在设置隐藏,内含自动唤醒/心情打卡/朋友圈);打卡支持Emoji/表情库/自定义;朋友圈用户+助手双向可见
- r187 已交付;记忆轨 a6fbf7e3 已推;c04fc516 已上云(远程HEAD=dcd9109c)
