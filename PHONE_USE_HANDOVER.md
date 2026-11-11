# Phone Use 移植交接报告

> 交接人：WorkBuddy（辅助开发通道）
> 日期：2026-11-11
> 目标：把官方 RikkaHub 3.2.0 的 Phone Use（AI 操作手机）移植到本分支
> 状态：**未动工**，本文档为唯一前期成果（含已完成的源码侦察）

---

## 一、结论速览

- 上游实现是**自包含模块**：1 个无障碍服务 + 1 个本地工具 + 1 个工具卡片 UI，共 7 个文件约 60KB
- 与聊天主流程唯一耦合点：注册一个名为 `phone_use` 的本地工具（与本分支已有的 moments_post / secrets_write 等同一注册路径）
- 预估工作量：顺利 2~3 轮迭代；主要风险是 3.2.0 与本分支 2.5.6 底座的 AI/Tool 接口差异

## 二、上游源码位置（rikkahub/rikkahub，tag 3.2.0）

获取方式：`git clone --branch 3.2.0 --depth 1 https://github.com/rikkahub/rikkahub.git`
或 contents API 按 sha `e033a47d395fe57d16db2144069c3abdb7117547`（tag 对象，需解析到 commit）逐文件拉取。

| 文件 | 大小 | 说明 |
|---|---|---|
| `app/src/github/java/me/rerere/rikkahub/phoneuse/PhoneUseAccessibilityService.kt` | 14.7KB | 无障碍服务：读屏+手势执行；**PhoneUseManager/PhoneElement/PhoneScreen/PhoneKey 等类定义就在相关文件内** |
| `app/src/github/java/me/rerere/rikkahub/phoneuse/GestureIndicator.kt` | 6.9KB | 悬浮窗动作指示器（可裁剪，见 §五） |
| `app/src/github/res/xml/phone_use_accessibility.xml` | 0.5KB | 无障碍服务声明 |
| `app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/PhoneUseTool.kt` | 20.4KB | phone_use 工具本体（11 个动作） |
| `app/src/main/java/me/rerere/rikkahub/phoneuse/PhoneController.kt` | 4.1KB | 控制器 |
| `app/src/main/java/me/rerere/rikkahub/ui/components/message/tools/PhoneUseToolUI.kt` | 5.9KB | 聊天里的工具卡片 |
| `app/src/test/java/me/rerere/rikkahub/data/ai/tools/PhoneUseToolTest.kt` | 11.5KB | 单测（含依赖替身，值得读） |

⚠️ 官方把服务放在 `github` flavor（应为应用市场合规），本分支构建 dist 变体，**需把服务相关文件挪到 main 源集**。

## 三、官方设计要点（读代码前先看这个）

1. **11 个动作**：`observe`（读屏元素列表）/ `screenshot`（截图缩到最长边 1568px 发模型）/ `tap` / `long_press` / `swipe` / `scroll` / `type` / `press` / `open_app` / `list_apps` / `wait`
2. **坐标用 0~1000 网格**（`GRID = 1000`）：截图发模型前会缩放，像素坐标对不上，所有坐标按网格换算
3. **节奏参数**：操作后等界面稳定再读屏——`SETTLE_MS=800`、open_app 后 `1500`、tap `60ms`、长按 `700ms`、滑动 `350ms`；悬浮窗通知消失要过 `120ms` 一帧。动作结果直接带回新屏幕元素列表，省模型一次 observe
4. **安全黑名单**（硬编码 12 个包名，不能打开、跳转进去也不能读屏/操作）：支付宝、云闪付、PayPal、Google Wallet/Pay、工商/建设/农业/中国/交通/招商/邮储银行
5. **元素定位约定**：模型优先按最新元素列表的 index 定位，index 只对当次列表有效（提示词里已写明）

## 四、本分支集成点

1. **工具注册**：`app/src/main/java/me/rerere/rikkahub/data/ai/tools/local/` 下注册 `phone_use`，与本分支现有本地工具同路径（参考 WakeRecordsTool.kt 的注册方式）
2. **工具卡片**：`ui/components/message/tools/` 下挂 PhoneUseToolUI
3. **Manifest**：无障碍服务 + `phone_use_accessibility.xml` 进 main 源集；悬浮窗需 `SYSTEM_ALERT_WINDOW`；截图走无障碍 takeScreenshot（**API 30+，注意 minSdk 判断**）
4. **接口对齐（主要工作量）**：上游 3.2.0 的 `Tool`/`InputSchema`/`UIMessagePart` 等类型与本分支 2.5.6 底座有差异，**不能纯复制**；重点核对 Tool 构造签名、InputSchema 表达方式、工具结果回填格式

## 五、建议的裁剪与安全措施

- **第一期砍掉悬浮窗指示器**（GestureIndicator + SYSTEM_ALERT_WINDOW 整条链）：少一个权限、少一片调试面，第二期再加回
- **必须加总闸**：本分支有「自主唤醒」链路（后台无人值守生成），phone_use 若被唤醒链调用 = 模型无人值守操作手机。建议工具入口处判断 `isSelfInForeground`（上游工具签名里已预留该参数），**后台触发时直接拒绝**，仅前台手动对话允许用
- 黑名单不要删，建议追加可配置能力（后续）

## 六、工作计划与验收

| 阶段 | 内容 | 验收标准 |
|---|---|---|
| ① 对齐 | 拉上游 3.2.0 全部 7 文件，核对 Tool/工具注册/结果回填与本分支底座差异 | 差异清单成文 |
| ② 移植 | 服务+工具+卡片+Manifest 进 main 源集，注册 phone_use | CI 构建（Self-Check compileDistKotlin）通过 |
| ③ 权限引导 | 无障碍开启引导 + 截图权限判断 + 仅前台总闸 | 未开无障碍时工具返回明确错误提示 |
| ④ 真机 | 设置页/微信等简单界面跑通「读屏→点按→输入」；黑名单验证：open_app 支付宝被拒 | 全链路可用 |

## 七、工程纪律（本仓库惯例，务必遵守）

1. **推送前核对 ref**：`GET /repos/luobo240524/rikkahub/git/ref/heads/custom-2.5.6` 确认 tip 是预期 commit 再推（2026-11-11 出现过 GitHub 吐过期 ref 广告的只读异常）
2. **CI**：push custom-2.5.6 自动构建，run_number 即版本号；Self-Check 步 compileDistKotlin 快速失败；纯 md 文档提交被 paths-ignore 跳过（本文档即如此，不会触发构建）
3. **每次改完**：括号配平 + 逐文件断言校验（strip_code 状态机脚本在 .workbuddy/memory/2026-10-14.md）；诊断代码用完必删
4. **不要动**：唤醒链（ChatService 唤醒/静默提醒）、消息分条、气泡渲染——这些是二改最深区，与 Phone Use 无交集
5. 设置项如需用户开关，沿用既有模式：全局放 DisplaySetting（界面偏好），每助手覆盖放 Assistant 字段（三态 null=跟随全局，参考 showAsrWaveButton / backgroundVeilOverride）

## 八、环境备忘（WorkBuddy 侧）

- 本机（公司电脑）已下载的上游 7 文件在 `r240_pull/upstream_phoneuse/`；上游 3.2.0 全部 kt/xml 路径清单在 `r240_pull/_upstream_paths.txt`
- 稀疏克隆可用：`git clone --depth 1 --filter=blob:none --sparse` + `sparse-checkout set --no-cone '*.kt' '*.xml'`（codeload tarball 与普通浅克隆在弱网下会断）
- 推送脚本模板：根目录 `gh_fix_r245_veil2.py`（blobs/trees/commit 直推 + ref assert + 构建盯梢 `gh_watch_r245.py`）
