# -*- coding: utf-8 -*-
"""
查询最新构建状态与 Releases 最新 APK。
用法：python tools/check_build.py
（Token 可选；仓库转私有后必须设置 GITHUB_TOKEN）
"""
import urllib.request, json, os, sys

TOKEN = os.environ.get('GITHUB_TOKEN', '')
OWNER, REPO, BRANCH = 'luobo240524', 'rikkahub', 'custom-2.5.6'

HDR = {'Accept': 'application/vnd.github+json', 'User-Agent': 'x'}
if TOKEN:
    HDR['Authorization'] = 'Bearer ' + TOKEN

def api(p):
    return json.load(urllib.request.urlopen(urllib.request.Request('https://api.github.com' + p, headers=HDR), timeout=60))

data = api(f'/repos/{OWNER}/{REPO}/actions/runs?branch={BRANCH}&per_page=5')
print('=== 最近构建 ===')
for r in data.get('workflow_runs', [])[:5]:
    print(f"{r['created_at']} | {r['conclusion'] or r['status']:10} | {r['head_commit']['message'].splitlines()[0][:60]}")

rel = api(f'/repos/{OWNER}/{REPO}/releases')
if rel:
    latest = rel[0]
    print('=== 最新安装包 ===')
    for a in latest.get('assets', []):
        if a['name'].endswith('.apk'):
            print(f"{a['name']} | {a['size']/1048576:.1f} MiB | {a['browser_download_url']}")
