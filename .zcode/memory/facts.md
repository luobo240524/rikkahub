---
summary: 环境约束、关键路径与可复用命令
created: 2026-11-01 22:09
updated: 2026-11-07
status: active
---

# 事实

## 环境

- [2026-11-01] 工作机只有 C:/D 两盘(无 F 盘,旧交接文档里的 F:\zcode\rikkahub x 是旧机器路径)
- [2026-11-01] 本机无 Android SDK/JDK,不能本地编译;编译验证全靠 GitHub Actions CI
- [2026-11-01] github.com 直连间歇阻断(api.github.com 与 raw.githubusercontent 通常可达);推送失败先重试再等等
- [2026-11-01] gh CLI 用 GH_TOKEN 方式调用(GCM 的 gho_ token 缺 read:org,`gh auth login` 会拒)

## 路径与位置

- [2026-11-01] 本地克隆 D:\zcode项目存储文件\rikkahub x;git 在 C:\Program Files\Git
- [2026-11-01] gh CLI 在 C:\Users\文\tools\gh-cli\bin(已入用户 PATH);GH_TOKEN 已设为用户环境变量
- [2026-11-01] Context7 MCP 配在 ~/.zcode/cli/config.json 的 mcp.servers,查 Compose/Material3/Room API 文档用

## 命令与操作

- [2026-11-01] 发布新版本:改动后 `git push origin custom-2.5.6`,CI 自动构建,版本号 rN=run_number
- [2026-11-01] 纯文档/记忆提交不触发构建:custom-build.yml 配了 paths-ignore(**.md、docs/**、.zcode/**、.gitignore),commit 68549ed0;代码改动照常构建
- [2026-11-01] 读构建日志:`gh run view --job <job_id> --log-failed -R luobo240524/rikkahub`
- [2026-11-01] 交付下载链接:先实测速度(直连/gh-proxy.com 等),按速度排序给纯文本完整 URL,不用 markdown 链接
- [2026-11-01] 用户手机装 arm64 变体 app-arm64-v8a-dist-rN.apk,来自 custom-build prerelease
- [2026-11-04] HugeIcons 是 jitpack 依赖(com.github.rikkahub:hugeicons-compose),本机无 gradle 缓存,新图标名只能从代码库已用集合验证(已用名含 Earth/InLove/Clock02/Menu03)
- [2026-11-04] 导航是 androidx.navigation3(NavDisplay+Screen:NavKey 类型化路由,非 NavHost);Room 新表用手写 Migration 免 schema json;整库备份自动覆盖新表——细节在 phase2-blueprint.md
- [2026-11-07] **数据库 version=28**:mood_checkins((date,subject) 唯一索引,重复打卡 REPLACE 覆盖)+moments_posts 两表;DAO 已进 DataSourceModule,LocalTools 10 参注入(AppModule)
- [2026-11-07] **发现体系入口**:收纳菜单 WakeCapsule.kt(发现/打卡/朋友圈三项可导航)+抽屉 ChatDrawer Earth 行;路由 Screen.Discover/Moments/MoodCheckin;页面在 ui/pages/{discover,moments,mood}/,VM 走 koinViewModel+ViewModelModule
- [2026-11-07] **安全模式自动通道**:连续 2 次启动失败后冷启直接进 SafeMode(RouteActivity shouldEnterSafeMode),也可长按应用图标;SafeMode 有一键修复(重置词典+清理库+WAL压缩+VACUUM)
- [2026-11-07] 设置页间距滑杆用 CardGroupScope.formItem;气泡颜色预设 BubbleColorPresets 硬编码中文在 AssistantBasicPage.kt L1594(不走 strings)
- [2026-11-08] **DB version=30**:secrets 加 author 列(user/assistant,纯增量 ALTER);发现体系支持助手写心里话(secrets_write 工具)
- [2026-11-08] **发现页主题系统**:tokens 在 ui/pages/discover/DiscoverTheme.kt(七套:BASELINE_LIGHT/DARK,JOURNAL,GREEN,BLUE,EDITORIAL,STARRY);选择存 DisplaySetting.discoverTheme;装饰全 drawBehind(点阵/星点/水彩晕染/叶形/虚线框);心里话便签纸色=DiscoverTheme.noteColors
- [2026-11-08] **后台提醒链路**:SocialNotifyController(选最近会话+15s去抖+无社交工具跳过)→ChatService.triggerSocialNotify→handleMessageComplete(requireVisibleReply=false);通知摘要文案在三个 VM,"用户"已全部改 TA/对方
- [2026-11-08] NAMING_GUIDE 常量在 service/WakeProtocol.kt——助手提示词称呼指引(自定昵称,禁叫「用户」),声明必须在引用它的 const 之前
- [2026-11-08] r204 交付:直连 1.27MB/s(app-arm64-v8a-dist-r204.apk);Actions run_number 跳号,产物无 r203 直接 r204
