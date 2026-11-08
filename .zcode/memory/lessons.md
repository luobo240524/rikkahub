---
summary: 踩坑记录:现象、根因与规避方式
created: 2026-11-01 22:09
updated: 2026-11-07
status: active
---

# 教训

- [2026-11-01] **r180 构建失败(Gradle Build 步骤)**:SafeModeActivity.kt 用了 `db.rawQuery(...)`,而 Room 的 SupportSQLiteDatabase 接口只有 query()/execSQL();规避:返回结果行的 PRAGMA 一律 `query("...")`,参考 data/sync/DatabaseBackup.kt:42
- [2026-11-01] **静默安装 git 失败(退出码 2)**:普通 shell 无管理员权限;规避:PowerShell `Start-Process -Verb RunAs` 提权安装
- [2026-11-01] **未认证 API 读不到 Actions 日志(403)**:日志下载接口必须认证;规避:gh CLI + GH_TOKEN 读 `--log-failed`
- [2026-11-02] **UIMessage.modelId 是 `Uuid?` 不是 String**——r185 编译失败根因:`message.modelId?.takeIf { it.isNotBlank() }` 编不过;兜底用 `?.toString()`
- [2026-11-02] **CI 偶发 SDK 组件下载损坏(ZipException: Archive is not a ZIP archive)**——r187 失败根因,GitHub 运节点网络抖动,非代码问题;`gh run rerun <id>` 重跑即可
- [2026-11-03] **唤醒"已触发却无消息"第二形态**:wakeReplyHasContent 把 reasoning/工具调用也算实质内容,流在只吐思考阶段断掉→标记FIRED但用户看不到消息;修复=只认"用户可见内容"(非空白Text/Image/Video/Audio/Document),仅思考/工具调用→回滚重试(dcd9109c)
- [2026-11-04] **Compose BOM 2026.09 移除了 drawscope.ClipMode/drawOutline,clipRect 无 clipMode 参数**——r189/r190 连败根因;写 DrawScope 扩展时只用稳定 API(drawPath+Path.arcTo/drawRoundRect/drawLine);MessagePartsBlock 是 ChatMessage 内独立私有 composable,顶层新参数要手动穿透
- [2026-11-04] **androidx.core.graphics.ColorUtils 才有 HSL 工具**,android.graphics.ColorUtils 在本项目解析失败(参考 SettingThemePage 导入)
- [2026-11-07] **本地工具 execute 的 args 是 JsonElement**,取字段必须先 `args as? JsonObject` 再 get——直接 `args?.get()` 编不过(r197 根因)
- [2026-11-07] **PaddingValues.plus 是项目自定义扩展**(utils/ComposeExt.kt),用 `contentPadding + PaddingValues(...)` 需 import `me.rerere.rikkahub.utils.plus`(r197 根因)
- [2026-11-07] **设置页列表是 CardGroup 自定义 DSL**:item(onClick/modifier/overline/supporting/leading/trailing/colors/headlineContent) 无尾 lambda,滑杆类用 formItem(content)——标准 LazyListScope.item 写法编不过(r193 根因)
- [2026-11-06] **RoundRect 无"四裸Float+四角命名参数"重载**:用 RoundRect(rect=Rect(l,t,r,b), topLeft/topRight/bottomRight/bottomLeft)(r195 根因)
- [2026-11-06] **形状圆角在矮气泡里被系统按比例钳制**,手算钳制必然走样——描边圆角一律从 `shape.createOutline(size, layoutDirection, this)` 的 RoundRect 取(单行气泡框线不贴合的根因)
- [2026-11-06] **Screen 定义在 RouteActivity.kt 文件级**(L627 起),import 路径 `me.rerere.rikkahub.Screen` 而非 RouteActivity.Screen
- [2026-11-06] **push 成功但 workflow 未触发偶发**(cbe32160):push 后 45s 查 `gh run list`,没触发就 `gh workflow run custom-build.yml --ref custom-2.5.6` 手动补
- [2026-11-07] **HugeIcons 图标必须 `HugeIcons.前缀`+对象导入**(第二次踩)——裸图标名报 receiver mismatch;新图标名只能用代码库已用集合验证
- [2026-11-07] **joinToString 的 transform 非内联**——lambda 里不能调 @Composable;先 `map {}`(内联)再 joinToString
- [2026-11-08] **const val 初始化器禁止前向引用**——引用后声明的 const 报"must be initialized";被引用常量必须声明在前
- [2026-11-08] **顶层 const 跨包不能「文件名.属性」寻址**——`xxx.service.WakeProtocol.NAMING_GUIDE` 报 Unresolved(facade 类不存在);用 `import xxx.service.NAMING_GUIDE` 直引
- [2026-11-08] **matchParentSize 不是顶层 import**(第二次踩)——它是 BoxScope 成员,Box 直接子项免导入;顶层导入直接 Unresolved
- [2026-11-08] **Actions run_number 会跳号**——成功 run 42442649272 产物叫 r204(没有 r203);交付链接以 release 实际资产名为准
- [2026-11-07] **图标显小不一定是容器小**——Settings03 glyph 视口自带留白,加容器尺寸无效;换图标(Settings02)才治本
- [2026-11-08] **github 直连断连处置**——后台循环重试(60s 步进)通常能过;REST API 兜底(blob→tree→commit→ref)已验证;后台循环报失败先核 remote ref 再动(曾白建 orphan commit)
