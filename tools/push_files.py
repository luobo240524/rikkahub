# -*- coding: utf-8 -*-
"""
推送文件到 custom-2.5.6 分支（GitHub API，无需本地构建环境）。

用法：
  1. 设置环境变量 GITHUB_TOKEN（repo 权限）——令牌不入库（本仓库公开）
     Windows:  setx GITHUB_TOKEN "ghp_xxx"  然后重开终端
  2. 在下方 FILES 填入要推送的仓库相对路径
  3. python tools/push_files.py "提交说明"

推送后 GitHub Actions 自动构建（约 5-12 分钟），
APK 出现在 Releases：custom-build tag。
记得同步更新根目录 PROJECT_REPORT.md。
"""
import urllib.request, json, os, time, sys

TOKEN = os.environ.get('GITHUB_TOKEN', '')
OWNER, REPO, BRANCH = 'luobo240524', 'rikkahub', 'custom-2.5.6'
LOCAL_ROOT = os.path.join(os.path.dirname(os.path.abspath(__file__)), '..')

# ↓↓↓ 每次改成要推送的文件（仓库相对路径）↓↓↓
FILES = [
    # 'app/src/main/java/me/rerere/rikkahub/service/ChatService.kt',
]
COMMIT_MSG = sys.argv[1] if len(sys.argv) > 1 else 'update'

def api(method, path, body=None, retries=6):
    data = json.dumps(body).encode() if body is not None else None
    last = None
    for i in range(retries):
        req = urllib.request.Request('https://api.github.com' + path, data=data,
                                     headers=HDR, method=method)
        try:
            r = urllib.request.urlopen(req, timeout=60)
            txt = r.read().decode()
            return r.status, (json.loads(txt) if txt else {})
        except urllib.error.HTTPError as e:
            return e.code, json.loads(e.read().decode() or '{}')
        except Exception as e:
            last = e
            print(f'  网络错误({i+1}/{retries}): {e}，5秒后重试...', flush=True)
            time.sleep(5)
    raise last

HDR = {'Authorization': 'Bearer ' + TOKEN, 'Accept': 'application/vnd.github+json', 'User-Agent': 'x'}

if not TOKEN:
    sys.exit('请先设置环境变量 GITHUB_TOKEN（repo 权限）')
if not FILES:
    sys.exit('请先在 FILES 里填入要推送的文件')

cur = api('GET', f'/repos/{OWNER}/{REPO}/git/ref/heads/{BRANCH}')[1]
cur_sha = cur['object']['sha']
c = api('GET', f'/repos/{OWNER}/{REPO}/commits/{cur_sha}')[1]
base_tree = c['commit']['tree']['sha']

tree_entries = []
for path in FILES:
    content = open(os.path.join(LOCAL_ROOT, path), encoding='utf-8').read()
    st, blob = api('POST', f'/repos/{OWNER}/{REPO}/git/blobs',
                   body={'content': content, 'encoding': 'utf-8'})
    if st != 201 or not blob.get('sha'):
        print('blob 失败', path, blob); sys.exit(1)
    tree_entries.append({'path': path, 'mode': '100644', 'type': 'blob', 'sha': blob['sha']})
    print('blob OK:', path.split('/')[-1], flush=True)

tree = api('POST', f'/repos/{OWNER}/{REPO}/git/trees', body={
    'base_tree': base_tree, 'tree': tree_entries})[1]
print('tree ok', flush=True)

nc = api('POST', f'/repos/{OWNER}/{REPO}/git/commits', body={
    'message': COMMIT_MSG,
    'tree': tree['sha'],
    'parents': [cur_sha],
})[1]
print('commit:', nc.get('sha', '')[:12], flush=True)
api('PATCH', f'/repos/{OWNER}/{REPO}/git/refs/heads/{BRANCH}', body={'sha': nc['sha']})
print('ref updated, Actions 将自动构建', flush=True)
