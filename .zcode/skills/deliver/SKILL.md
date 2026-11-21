---
name: deliver
description: 交付 APK 的完整一条龙流程:commit → push(断连重试)→ CI 触发确认 → 轮询构建 → 失败拉日志 → release 资产确认 → 测速 → 纯文本直链交付。用户说「构建/出包/交付/发版/推上去/给我链接」或代码自查通过后使用;不按本流程走就是每次重新踩断连/跳号/测速的坑。
---

# 交付(出包一条龙)

## 1. commit

单行 message,`feat/fix(rN): 一句话` 前缀;只 add 相关文件,不 `git add -A`。
纯文档/`.zcode/**` 改动不触发构建(workflow paths-ignore),代码改动照常。

## 2. push(直连间歇阻断是常态)

- 失败先挂**后台重试循环**(50s 步进,20 次);直连恢复通常几分钟内(先例:第 3 次成功常见,也有一连失败 8 次的)。
- 禁急转 REST 推送;禁 `reset --hard` 回旧基(会删工作区文件)。
- 推完 45s 查触发:`gh run list --workflow custom-build.yml --branch custom-2.5.6 --limit 3`;
  没触发就手动补 `gh workflow run custom-build.yml --ref custom-2.5.6`。

## 3. 轮询与失败处理

- 轮询用 `gh run view <run_id> --json status,conclusion` 循环;**禁用 `gh run watch`**(经管道吞
  --exit-status 退出码,误报成功;网络抖动中断误报失败)。
- 失败拉日志:`gh run view --job <job_id> --log-failed -R luobo240524/rikkahub`
  (未认证 API 读不到日志 403;gh CLI 用 GH_TOKEN 方式,GCM 的 gho_ token 缺权限)。
- CI 失败的 run 也占 run_number(跳号常态);先修编译错(对照 ui-impl 清单)再推。

## 4. 产物与链接

- 产物名:`gh release view custom-build --repo luobo240524/rikkahub --json assets -q '.assets[].name'`,
  取 `app-arm64-v8a-dist-rN.apk`;**以 release 实际资产名为准**,不按 run_number 推算(跳号)。
- 测速:直连 + `https://gh-proxy.com/` + `https://ghfast.top/` 前缀各采样
  `curl -L --max-time 8 -o /dev/null -w "%{speed_download} %{http_code}"`,挂了(http=000)剔除。
- 交付格式:**纯文本完整 URL 按实测速度排序**,不用 markdown 链接;直连往往起步慢会爬坡,备注一句。

## 5. 版本确认

versionName=2.5.6-rN / versionCode=run_number(工作流 -P 注入);关于页可查,覆盖安装是否生效一眼便知。
