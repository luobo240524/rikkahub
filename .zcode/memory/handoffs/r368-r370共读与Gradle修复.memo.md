---
summary: r368-r370共读与Gradle修复
created: 2026-11-27
updated: 2026-11-27
status: active
---

# r368-r370共读与Gradle修复

## Summary

本窗口先修 r368：留灯页左卡从「碎碎念」换回「留灯」，邮局写信弹层放大并加草稿自动保存。随后按方案 A 交付 r369/r370：共同阅读增加章节结构、章节摘要、读书笔记、长期记忆与技能提案（读这章/读整本/自动学习），技能必须弹卡确认后才创建并启用。最后定位本地单测失败根因——中文路径下 Gradle worker 的 UTF-8 @argfile 被 JDK 按 GBK 解码，新增 subst ASCII 盘符脚本，12 条单测全绿。

## Done

- [2026-11-27] **r368 留灯页改回留灯**：`envelope.html` 左卡由碎碎念改为留灯（灯泡图 + 「N 盏灯亮着」，点进 `lights`），碎碎念只保留首页入口；右卡仍是一封信；REGIONS 同步改名。commit `cc6039bf`，run 45996685106 success。
- [2026-11-27] **r368 写信页**：`postoffice.html` 写信弹层改 430 宽 × `min(86%,640)` 高，输入区 118px→约 426px；新增草稿自动保存：停止输入 400ms 写原生 SharedPreferences（每助手一份，`commit()` 同步落盘），重开显示「✎ 接着写」并恢复正文+送达时间，寄出成功清草稿、失败保留并提示。浏览器 mock 跑通「输入→保存→重载恢复→失败保留→成功清空」。
- [2026-11-27] **r369 章节结构**：`ReadTogetherChaptering`（纯函数）提供 `ChapterMeta/PagedText/chunkIntoPages` 页映射；EPUB 每个 spine 文档一章（标题优先 h1-h3 再 `<title>`）、TXT 按 `第X章/Chapter N/序章…` 正则、PDF 每 10 页一块；`BookMeta.chapters` 默认空，旧书首次打开 `ensureChapters` 从已存页文本懒重建并写回。
- [2026-11-27] **r369 学习产物存储**：书目录新增 `digests.json`（章节摘要，按章覆盖）、`notes.json`（读书笔记，同章同标题覆盖）、`skill_proposals.json`（技能提案 pending/created/dismissed）；统一临时文件+rename 原子写；`chapterSourceHash` 用于跳过未变化章节。
- [2026-11-27] **r369 学习服务**：`ReadTogetherStudyService`（Koin）支持读单章（助手聊天模型）、读整本/自动学习（快速模型）；`withTimeout(180s)`、reasoning OFF、章节正文截断 16k；一次调用产出 recap/notes/memory/skill；解析走 `StudyResultParser`（去围栏/尾逗号 + 容错 JSON）；记忆仅在 `assistant.enableMemory` 时写 `/notes/books/<书名>-<id>.md`；技能只写 pending 提案，名字 `book-<slug>` 且自动避重名。
- [2026-11-27] **r369 阅读页 UI**：顶栏新增「TA 的笔记」（Bookshelf01）与「让 TA 读」（MagicWand01），副标题带当前章节；「读这章/读整本/重读」底部面板 + 整本成本确认；笔记面板含技能提案卡、进度/停止、全书总览、按章摘要/人物/伏笔/金句/TA 笔记；技能确认弹卡后 `saveSkill` 并加入当前助手 `enabledSkills`；助手设置「共同阅读」下新增「共读时自动学习」（默认关）。
- [2026-11-27] **r369 工具/上下文**：`read_together` 新增 `list_chapters/get_recap/save_note`，systemPrompt 补「聊前章先 get_recap、值得记的用 save_note」；`ChatService` 共同阅读注入补当前章节与是否有摘要；`socialAuthorName` 兼容 `author=="ai"`。
- [2026-11-27] **r370 整本健壮性**：读整本时单章失败不中断（记录失败数继续），合并总览用 `runCatching`；完成后提示失败章数，可再点一次重跑补章（已读且 hash 未变的章跳过）。commit `99fc05bb`，run 46017906196 success。
- [2026-11-27] **Gradle 单测修复**：定位 `testDebugUnitTest` 失败根因=中文路径 + JDK25 `sun.jnu.encoding=GBK` 读 Gradle 的 UTF-8 worker @argfile 乱码，找不到 `GradleWorkerMain`；新增 `tools/gradlew-ascii.ps1`（subst 给项目与 GRADLE_USER_HOME 挂 ASCII 盘符，退出清理），AGENTS.md 记录；12 条 readtogether 单测全绿（章节 6 + 解析 6）。commit `01fcb547`。
- [2026-11-27] 顺手修 `VoiceSessionControllerTest` 与当前 `start(speak: (String, Boolean))` 签名不匹配的旧调用，测试源才能编译。
- [2026-11-27] 交付：r368 APK 51,220,724 B sha256 `7383d07b…`；r370 APK 51,263,744 B sha256 `88a371c5…`；r370 测速 gh-proxy 64–87KB/s、直连 22–44KB/s、ghfast 000。

## Decisions

- [2026-11-27] **留灯保留（用户拍板）**：信封页左卡恢复留灯（跳 `lights`），碎碎念只从首页进；不再考虑退休或第三卡。
- [2026-11-27] **r368 写信体验**：大纸面弹层 + 按助手草稿自动保存；寄出成功才清，失败保留；输入框放大属参数级改动直接做。
- [2026-11-27] **共读学习四问**：手动按钮 + 设置可开自动；产物=读书笔记/长期记忆/技能；技能弹卡确认后创建并启用；单章聊天模型、整本与自动快速模型。
- [2026-11-27] **共读章节与学习口径**：EPUB spine/TXT 标题正则/PDF 10 页块；整本 N+1 次调用、单章失败不中断可重跑；摘要按 hash 跳过；笔记同章同标题覆盖；技能名 `book-<slug>` 避重名；记忆仅 enableMemory 时写；`read_together` 目录不进备份。
- [2026-11-27] **本地单测统一走 ASCII 盘符脚本**：中文路径下 Gradle test worker 必挂，`tools/gradlew-ascii.ps1` 为本地测试入口；普通编译任务不受影响。

## Pitfalls

- [2026-11-27] **JDK25 中文 Windows 的 @argfile 编码坑**：`sun.jnu.encoding=GBK`，Gradle 写的 worker classpath 是 UTF-8，路径含 `文`/中文项目名就乱码；`-Dsun.jnu.encoding`、`JAVA_TOOL_OPTIONS`、`JDK_JAVA_OPTIONS` 都改不动；只能换 ASCII 路径（subst/迁移）或系统 UTF-8 区域设置。
- [2026-11-27] **subst 不接受带尾反斜杠的盘符**（`subst S:\ path`=无效参数）；PowerShell 里直接 `& subst.exe S: $path` 传参，别拼 cmd 字符串（内嵌引号会被二次转义）。
- [2026-11-27] **Windows PowerShell 读无 BOM 的 UTF-8 .ps1 按 GBK 解码**，中文提示变乱码；脚本存 UTF-8 with BOM，运行时加 `-ExecutionPolicy Bypass`。
- [2026-11-27] **签名改动要连测试调用点一起 grep**：`VoiceSessionController.start` 的 speak 改双参后测试没跟上，测试源编译失败；CI 不跑测试所以长期未暴露。
- [2026-11-27] **IAB fullPage 超高页仍会平铺错乱**：预览五屏时改用 412 宽小视口逐屏滚动截图才正常；DOM 探针定真伪。
- [2026-11-27] **旧书没有原文件**：章节只能从已存页文本重建（页首标题或 10 页块），不能指望重新解析 TOC；PDF 章节粒度就是 10 页。
- [2026-11-27] **整本成本线性增长**：300 章≈301 次快速模型调用，开始前必须弹成本确认；中断后重跑只补未完成章。
- [2026-11-27] **记忆写入依赖 `enableMemory`**：没开记忆功能的助手只产笔记/摘要/技能提案，不写记忆库。
- [2026-11-27] **技能保存前先校验**：`SkillManager.saveSkill` 先落盘后解析，frontmatter 缺 name/description 会留不可见目录；服务端已先生成合法 frontmatter，UI 重名检查仍在提案时做。

## Next

- [2026-11-27] 实机验收 r370：导入带「第X章」的 TXT/EPUB → 顶栏章节名 → ✨读这一章 → 笔记面板看摘要/笔记；找有方法的章节看技能提案→确认→技能页与助手启用列表；开「共读时自动学习」翻新章；短书试「读整本」看进度/成本/重跑跳过。
- [2026-11-27] 实机验收 r368：首页留灯卡 → 留灯页 → lights；邮局写信大输入框；写长信后杀 app 再进 → 「接着写」恢复；寄信失败草稿保留、成功清空。
- [2026-11-27] 观察项：整本摘要质量与费用、PDF 章节粒度是否够用、`read_together`/笔记是否要纳入备份；旧账 r365-r367 验收项一并过。
- [2026-11-27] 若 TA 读整本某章持续失败：看 StudyState.error 与 logcat `ReadTogetherStudy`，确认快速模型可用、JSON 解析未空；重跑会跳过已完成章。

## Refs

- 提交：`cc6039bf`(r368)、`ed34cb52`(r369)、`99fc05bb`(r370)、`01fcb547`(Gradle 单测脚本/文档)。
- CI：r368 run 45996685106；r369 run 46017581534；r370 run 46017906196。
- 产物：`app-arm64-v8a-dist-r368.apk` 51,220,724 B sha256 `7383d07be34c45aa0462381034d05c7a8dafb06db544f1278a46bb6fa5feab43`；`app-arm64-v8a-dist-r370.apk` 51,263,744 B sha256 `88a371c5e98adee34e29e2f910f57e4c96f170b3e778cec942178a8ed3489192`。
- 共读代码：`data/readtogether/{ReadTogetherChaptering,ReadTogetherStudyModels,StudyResultParser}.kt`、`service/ReadTogetherStudyService.kt`、`data/readtogether/ReadTogetherRepository.kt`、`data/ai/tools/local/ReadTogetherTool.kt`、`ui/pages/read/ReadTogetherPage.kt`、`Assistant.readTogetherAutoStudy`、`di/AppModule.kt`。
- 写信/留灯：`app/src/main/assets/your-world/{envelope,postoffice}.html`、`ui/pages/world/WorldJsBridge.kt`、`data/world/WorldPageModels.kt`。
- 单测工具：`tools/gradlew-ascii.ps1`；测试 `app/src/test/java/me/rerere/rikkahub/data/readtogether/{ReadTogetherChapteringTest,StudyResultParserTest}.kt`。
- 预览/计划：`.zcode/v2/visualizations/readtogether-study-v1.html`、`.zcode/plans/plan-sess_cd8fc253-b5c8-48c8-8407-13c7d15bceb1.md`。
- DB version=40 不变；APK 直链前缀 `https://github.com/luobo240524/rikkahub/releases/download/custom-build/`。
