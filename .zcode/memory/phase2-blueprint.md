---
summary: 二期蓝图:发现聚合页+朋友圈+心情打卡(进阶版架构,已全部落地r198)
created: 2026-11-04
updated: 2026-11-07
status: absorbed
---

# 二期蓝图（用户已拍板：进阶版架构，为后续加功能留位）

> **[2026-11-07] A-G 已全部落地交付(r198,aed11a07+19a76dca),蓝图吸收进 lessons/facts,本文件留档。**
> 实际落地的 DDL 用了 snake_case 列名(created_at/conversation_id),与下方模板的 camelCase 不同——以
> data/db/migrations/Migration_27_28.kt 为准。三期功能(打卡提醒限频/主题包/热力图/图片发帖)沿用本架构
> 直接进发现页。

## 已定决策

- **进阶版两层收纳**：功能收纳菜单/抽屉 → 「发现」聚合页 → 具体功能独立页；以后新增功能都进发现页，菜单不膨胀
- 朋友圈**砍图片发帖**（一期纯文字+emoji+可选心情标签）；助手**不自动评论**用户帖
- 打卡提醒限频一天一次、深色适配、预设主题包、热力图/连续徽标 → 三期候选
- 交互样式参照已验证的预览：`D:\zcode项目存储文件\.zcode\v2\visualizations\d2f59077bc6d7eaa3994eb36b1876f346f3a271507eb8507274bc402c8e7041a\bubble-optimization-preview.html`（内含打卡弹层+朋友圈弹层交互稿）

## 底座事实（已探明，直接照做，勿再摸索）

1. **导航 = androidx.navigation3（非 NavHost 字符串路由）**
   - 路由定义：`app/src/main/java/me/rerere/rikkahub/RouteActivity.kt` L612-767 `sealed interface Screen : NavKey`，全部 `@Serializable` data class/object
   - 页面注册：同文件 L345-562 `entryProvider { entry<Screen.XXX> { key -> XxxPage(...) } }`
   - 导航方式：`ui/context/NavContext.kt` 的 `Navigator`，页面内 `LocalNavController.current.navigate(Screen.XXX)`
   - 新页面三步：Screen 加 object → entryProvider 加 entry → 写页面 composable
2. **抽屉**：`ui/pages/chat/ChatDrawer.kt` L376-468 一排 `DrawerAction(icon, label, onClick)`（定义 L809-838）；挂载在 ChatPage.kt L231-241 ModalNavigationDrawer
3. **Room**：`data/db/AppDatabase.kt` entities+DAO+version=27
   - **新表用手写 Migration(27,28)**（`data/db/AppDatabaseFactory.kt` L21 addMigrations 链），勿用 AutoMigration（需要 schema json，手写最稳）
   - 整库备份（DatabaseBackup `VACUUM main INTO`）自动覆盖新表，备份代码零改动
   - DDL 模板（必须精确匹配 Room 预期）：
     ```sql
     CREATE TABLE IF NOT EXISTS `mood_checkins` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `date` TEXT NOT NULL, `subject` TEXT NOT NULL, `mood` TEXT NOT NULL, `phrase` TEXT NOT NULL, `createdAt` INTEGER NOT NULL);
     CREATE UNIQUE INDEX IF NOT EXISTS `index_mood_checkins_date_subject` ON `mood_checkins` (`date`, `subject`);
     CREATE TABLE IF NOT EXISTS `moments_posts` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `author` TEXT NOT NULL, `conversationId` TEXT, `content` TEXT NOT NULL, `mood` TEXT, `createdAt` INTEGER NOT NULL);
     ```
   - 打卡表 (date,subject) 唯一索引 → 重复打卡用 OnConflictStrategy.REPLACE 覆盖
4. **助手工具四件套注册路径**（参考 TimeInfoTool.kt 44 行模板）：
   - `data/ai/tools/local/LocalToolOption.kt` 加 `@SerialName("mood_checkin") data object MoodCheckin` 等 4 个
   - 新建 builder 文件：`Tool(name, description, parameters=InputSchema.Obj(...), execute={... 返回 listOf(UIMessagePart.Text(...))})`；execute 内 `withContext(Dispatchers.IO)` 读写 DB
   - `LocalTools.kt`：lazy 属性 + `getTools()` 里 `assistant.localTools.contains(...)` 分支；`di/AppModule.kt` L34 `LocalTools(get(), ...)` 需注入 AppDatabase
   - `ui/pages/assistant/detail/AssistantLocalToolPage.kt`：每工具一个 CardGroup 开关（TimeInfo 参考 L147-160，toggleLocalTool L106-121）
5. **助手页开关模板**：AssistantBasicPage.kt L590-610（autonomousWake 的 FormItem+Switch）
6. **字符串**：`discovery_`/`moments_`/`mood_` 前缀在 res 下完全未占用；图标用 find-hugeicons skill 查，已验证存在：Earth、InLove、Clock02、Menu03

## 实现清单（按序）

- **A. Room**：MoodCheckinEntity/MomentsPostEntity + 两 DAO（flowAll/recent/upsert/delete）+ Migration_27_28 + version=28
- **B. DI**：AppModule 注入 AppDatabase 到 LocalTools；页面数据可直接 collect DAO Flow（koinViewModel 模式照抄现有简单页）
- **C. 三页面**：Screen.Discover（聚合列表：朋友圈/每日心情打卡，带描述行，后续功能往后加）/ Screen.Moments（feed：作者 user✏️小助手🤖+时间+正文+心情标签，底部发布框常驻，空态）/ Screen.MoodCheckin（月历视图：用户实心点·助手圆环·月份翻页 + emoji 选择器 + 短语输入 + 打卡按钮 + 历史列表）
- **D. 入口**：WakeCapsule 菜单「自动唤醒」保留现有 sheet，新增「发现」项 navigate(Screen.Discover)（替换现有"即将推出"占位）；ChatDrawer 抽屉行加 DrawerAction(Earth) → Discover
- **E. 助手工具×4**：mood_checkin(mood,phrase)/moments_post(content,mood?)/mood_read(days=30)/moments_read(limit=20)
- **F. strings×3**：discovery_title/entry 系 + moments_ 系 + mood_ 系 + assistant_page_local_tools_mood_*（values/values-zh/values-zh-rTW）
- **G. 推送 → r193+ 构建验收 → 测速链接**

## 本期明确不做

图片发帖、点赞评论、入口小红点、年度热力图、连续徽标、聊天内快捷打卡弹层（直接进独立页）、朋友圈图片展示

## 一期成果（已完成，r192 构建成功）

材质下拉+磨砂/清透度/不透明度条件滑杆、分侧边框美化（四角贴纸/HSL取色器/半包）、贴角圆角、功能收纳壳（Menu03）、mini 实时预览、120 条降级守卫——用户验收后进入二期。
