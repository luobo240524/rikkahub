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
- [2026-11-03] 气泡优化+心情打卡+朋友圈:交互预览已完成三轮迭代(可视化 HTML),**等用户说"构建"再写实机代码**;要点:材质/形状折叠下拉;磨砂(新,磨砂程度滑杆仅磨砂显示);玻璃优化(hyalite液态玻璃,清透度仅玻璃显示);不透明度移助手页仅默认材质显示;边框美化分侧配置(助手/用户气泡分开,四角贴纸统一,emoji自动趴框线,颜色下拉+自定义取色器,粗细,全包/半包顶底留白);新形状"贴角圆角"(靠头像一角收小);功能收纳(layout-grid图标,可在设置隐藏);打卡弹层=Emoji/表情库/自定义+月历视图(用户实心点/助手圆环,月份翻页);朋友圈用户+助手双向;助手工具:mood_checkin/moments_post(写)+mood_read/moments_read(读)+打卡摘要同步记忆库+当日状态注入系统提示;**通用原则:长列表选项一律收纳进下拉/弹层**
- r187 已交付;记忆轨 a6fbf7e3 已推;c04fc516 已上云(远程HEAD=dcd9109c)
- [2026-11-03] 已交建议清单(分期构建/模糊降级守卫/实机mini预览/预设主题包/打卡提醒限频/朋友圈一期砍图片/深色适配/贴纸防裁切/收纳隐藏保留唤醒提示)待用户挑选
- [2026-11-04凌晨] 一期代码已全部实现并本地提交(034ededb):材质下拉+磨砂/清透度/不透明度条件滑杆+分侧美化(四角贴纸/HSL取色器/半包)+贴角圆角+功能收纳+mini预览+120条降级守卫;push因网络封锁待补推(patch备份: phase1-backup-1104.patch);r189构建未开始,下次开机先push
