# 任务助理 · 同步服务与控制台

TaskMate（Android 待办 App）的个人自用同步服务：SQLite 存储 + 手机端同步接口 + 电脑端网页控制台。
默认监听 `0.0.0.0:8788`，同一 Wi-Fi 下手机可直接访问；网页控制台的 CSS/JS 全部内联，断网也能用。

- 兼容系统 Python **3.9**（不使用 `str | None`、`match` 等 3.10+ 语法）
- 运行依赖只有 `fastapi`、`uvicorn`，可选 `python-dotenv`
- 业务层 `service.py` / `store.py` 不 import fastapi，可完全离线跑 `unittest`

## 目录结构

```
server/
├── app.py             # FastAPI 路由、鉴权、静态页面
├── service.py         # 业务层：sync 合并、CRUD、统计（无 fastapi 依赖）
├── store.py           # sqlite3 持久化层（建表、事务、查询）
├── config.py          # 环境变量 / API Key / 数据库路径
├── main.py            # 启动入口：python3 main.py
├── static/index.html  # 自包含网页控制台（无任何外链）
├── tests/
│   ├── test_service.py  # 离线业务测试
│   └── test_http.py     # HTTP 层测试（未装 fastapi 时自动跳过）
├── requirements.txt
├── .env.example
└── data/taskmate.db   # 首次启动自动创建（已 gitignore）
```

## 快速开始

```bash
cd TaskMateAndroid/server

# 1. 可选：创建虚拟环境（推荐，避免污染系统 Python）
python3 -m venv .venv
source .venv/bin/activate

# 2. 安装依赖（已装过可跳过）
pip install -r requirements.txt

# 3. 启动（默认 0.0.0.0:8788）
python3 main.py
```

启动日志会打印：

```
  API Key : tm_xxxxxxxxxxxxxxxxxxxxxxxx
  本机控制台: http://127.0.0.1:8788/
  局域网地址: http://192.168.x.x:8788/
  健康检查 : http://127.0.0.1:8788/healthz
```

浏览器打开控制台，粘贴 API Key 即可。

> 没有网络安装依赖时，`python3 -m py_compile` 与 `python3 -m unittest discover -s tests` 仍可离线运行；HTTP 层测试会自动跳过。

## 如何拿到 / 固定 API Key

1. **固定（推荐）**：复制 `.env.example` 为 `.env`，把 `API_KEY` 改成一串足够长的随机字符串：

   ```bash
   cp .env.example .env
   # 生成一个随机 Key
   python3 -c "import secrets; print('tm_' + secrets.token_urlsafe(24))"
   ```

   编辑 `.env` 填入该值，重启服务。装了 `python-dotenv` 时自动读取；没装也可以直接：

   ```bash
   API_KEY='你的Key' python3 main.py
   ```

2. **临时**：不配置 `API_KEY` 时，每次启动随机生成并打印在日志第一屏。重启后会变，已连接的手机/网页需要重新填。

所有 `/api/*` 请求都要带 `X-API-Key`；`/` 与 `/healthz` 不需要。
网页控制台的 Key 只保存在本机浏览器的 `localStorage`，不会上传到别处。

## 手机端怎么填

1. 电脑与手机连**同一个 Wi-Fi**。
2. 在电脑上查看局域网 IP（启动日志已打印，也可以手动查）：

   ```bash
   ipconfig getifaddr en0     # Wi-Fi
   # 或
   ipconfig getifaddr en1     # 有线/部分机型
   ```

3. 手机 App 的服务器地址填 `http://192.168.x.x:8788`（不要带 `/api`），API Key 填启动日志里那串。
4. 先在手机浏览器打开 `http://192.168.x.x:8788/healthz`，能看到 `{"ok":true,...}` 说明网络通。
5. 端口默认 **8788**，不要用 8787（那是另一个已有服务）。手机端请求示例：

   ```bash
   curl -H 'X-API-Key: 你的Key' http://192.168.x.x:8788/api/tasks
   ```

## 网页控制台怎么用

- **连接**：首次打开是居中的「连接」卡片，粘贴 API Key 后立刻拉数据；顶部输入框可随时换 Key。
- **快速添加**：主区输入框输入标题，回车即添加。点右侧滑杆图标展开「日期 / 时间 / 优先级」，再回车保存。
- **完成 / 删除**：点左侧圆圈立即 PATCH `done`；鼠标移到任务行右侧出现删除按钮，删除是软删除，底部提示条可「撤销」。
- **编辑标题**：点击任务标题变成输入框，回车或失焦保存，Esc 取消。
- **筛选**：左侧 全部 / 待办 / 已完成 / 逾期；统计卡显示 今日待办、逾期、本周、已完成。
- **自动刷新**：每 10 秒拉一次 `/api/tasks`，右上开关可暂停（页面切到后台时也会暂停，回来自动补一次）。
- **深浅色**：跟随系统 `prefers-color-scheme`。
- 窄屏（手机浏览器 / 小窗口）时侧栏会折叠到顶部，统计变两列。

## 数据与备份

- 数据库默认 `server/data/taskmate.db`（WAL 模式，会额外出现 `-wal`、`-shm` 文件）。
- 备份：停服务后复制整个 `data/` 目录，或 `sqlite3 data/taskmate.db ".backup 'backup.db'"`。
- 删除是软删除（`deleted=1`），墓碑会同步给手机；需要彻底清理时手动执行 SQL。
- 修改库位置：环境变量 `TASKMATE_DB=/绝对路径/taskmate.db`。

## 环境变量

| 变量 | 默认值 | 说明 |
| --- | --- | --- |
| `API_KEY` | 随机生成并打印 | 所有 `/api/*` 的鉴权 Key |
| `HOST` | `0.0.0.0` | 监听地址 |
| `PORT` | `8788` | 监听端口（不要用 8787） |
| `TASKMATE_DB` | `data/taskmate.db` | SQLite 路径，相对路径基于 `server/` |

## HTTP 接口

统一返回 `{"ok": true/false, ...}`；鉴权失败固定为 `401 {"ok":false,"error":"unauthorized"}`。

| 方法 | 路径 | 说明 |
| --- | --- | --- |
| POST | `/api/sync` | 请求 `{device_id, since, tasks[], platform?}`；合并后返回 `{ok, now, tasks}`（`updated_at > since`，含墓碑，按 `updated_at` 升序） |
| GET | `/api/tasks?include_deleted=0&filter=all\|active\|done` | 列表，按 `due_at` 升序（NULL 最后）、`created_at` 升序 |
| POST | `/api/tasks` | 新建，`updated_at = 服务器当前毫秒` |
| PATCH | `/api/tasks/{id}` | 任意子集 `title/notes/due_at/remind_at/priority/done/deleted`，不存在返回 404 |
| DELETE | `/api/tasks/{id}` | 软删除（`deleted=1`） |
| GET | `/api/stats` | `total/active/done/overdue/today/week`（排除 deleted） |
| GET | `/healthz` | `{ok, tasks, devices}`，无需鉴权 |
| GET | `/` | 网页控制台，无需鉴权 |

`TaskJSON`：

```json
{
  "id": "uuid",
  "title": "买牛奶",
  "notes": "",
  "due_at": 1790000000000,
  "remind_at": null,
  "priority": 2,
  "done": false,
  "deleted": false,
  "updated_at": 1790000000000
}
```

字段名与手机端严格一致；`created_at` 仅存库，不出现在接口返回里。

### 冲突与合并规则

- 手机端 `sync` 逐条按 `id` 合并：不存在则插入；`incoming.updated_at > 服务端 updated_at` 才覆盖；相等或更旧一律忽略（服务端为准）。
- 服务端时间戳不倒退；`created_at` 以首次写入为准（手机端若带 `created_at` 会采用）。
- 删除用 `deleted=1` 墓碑同步，墓碑本身也参与 `updated_at` 比较，因此旧设备不会把已删除任务“复活”。
- 统计口径：`overdue` = 未完成且 `due_at < 今天 0 点`；`today` = `[今天0点, 明天0点)`；`week` = `[今天0点, 今天+7天)`，均排除 deleted。

## 测试

```bash
cd TaskMateAndroid/server

# 语法检查（不产生项目内 pycache）
PYTHONPYCACHEPREFIX=$PWD/.pycache python3 -m py_compile app.py service.py store.py config.py main.py

# 业务层离线测试
python3 -m unittest discover -s tests
```

`tests/test_http.py` 需要 `fastapi` + `httpx`；未安装时会显示 skip，不影响业务层测试。

## 端口 / 防火墙提示

- 只允许同一局域网访问；路由器若开了「AP 隔离」，手机连不上电脑，需要关掉。
- macOS 第一次运行 Python 监听端口时，系统会弹「是否允许接受传入网络连接」，选**允许**。
  如果误点了拒绝：系统设置 → 网络 → 防火墙 → 选项，允许 `python3`（或终端）传入连接。
- 公司/酒店 Wi-Fi 常屏蔽设备互访，可改用手机热点。
- 服务没有 HTTPS，仅在可信的家庭局域网使用；不要把 8788 端口映射到公网。
- 端口冲突时换端口：`PORT=8790 python3 main.py`（同时改手机端与网页地址）。

## 常见问题

**Q：页面一直显示「连接」卡片 / 提示 Key 不正确？**
A：Key 必须与当前启动日志里的一致；服务重启且没有 `.env` 时 Key 会变。到 `server/.env` 固定一个。

**Q：手机请求超时？**
A：确认手机和电脑同一网段、地址用 `http://IP:8788`、macOS 防火墙放行 Python，并先用手机浏览器访问 `/healthz` 验证。

**Q：用 uvicorn 直接启动？**
A：`uvicorn 'app:create_app' --factory --host 0.0.0.0 --port 8788`（`main.py` 只是多了启动横幅和自动 IP 提示）。