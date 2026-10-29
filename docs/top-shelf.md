# Top Shelf / 顶部内容

The shelf fills the screen above the dock while a supported app tile is focused
in the **first dock row**. OK on that tile still opens the app. The shelf itself
is not a list you can move through item by item, and a per-item link in the JSON
is ignored.

顶部内容在 **Dock 第一排** 的对应图标被选中时盖住壁纸。在图标上按确定仍然打开那个应用。
顶部内容不能逐条点选；JSON 里如果多写了条目链接，会被忽略。

Built-in feeds and a custom URL use the same cache: 15 minutes after a successful
fetch. Connect timeout is 5 seconds, read timeout is 8 seconds. A failure does
not crash. If that package already has a built-in feed, the built-in feed stays.
Otherwise the shelf stays hidden.

内置来源和自定义地址共用 15 分钟缓存。连接超时 5 秒，读取超时 8 秒。失败不会闪退：
这个应用如果本来就有顶部内容，就继续用原来的；否则不显示。

## Built-in apps / 内置应用

| Package | Feed |
|---|---|
| `com.xiaodianshi.tv.yst` | Bilibili popular videos / 哔哩哔哩热门 |
| `com.tutu.myblbl` | The same Bilibili feed / 同一个哔哩哔哩热门接口 |
| `com.douyu.xl.douyutv` | Douyu live rooms / 斗鱼正在直播 |

`com.tutu.myblbl` is a third-party Bilibili TV client. It does not have its own
catalogue API here; focusing it shows the same public popular list as 云视听小电视.

There is no YouTube shelf.

`com.tutu.myblbl` 是第三方哔哩哔哩 TV 客户端，和云视听小电视共用公开热门接口。
没有 YouTube 顶部内容。

## Custom source / 自定义来源

Settings → **Top Shelf** / 主屏幕设置 → **顶部内容**.

1. **Custom source / 自定义来源.** Enter an `http` or `https` address the TV can reach,
   including an address on your LAN.
2. **Linked app / 关联应用.** Pick one installed app. The shelf is shown only when
   that app's tile is focused in the first dock row. One custom source is stored;
   it replaces the built-in feed for that package while the URL returns usable items.

The address must return a JSON **array**, not a Jellyfin/Emby object:

```json
[
  {"title": "Film", "meta": "2024", "image": "https://media.example/film.jpg"}
]
```

| Field | Required | Notes |
|---|---|---|
| `title` | yes | Trimmed. Empty items are skipped. Cut at 120 characters. |
| `meta` | no | Cut at 180 characters. |
| `image` | yes | Absolute `http` or `https` URL. `file:`, `data:` and relative paths are skipped. |

At most **12** usable items are kept. The JSON body is capped at **512 KB**.
Each image download is capped at **2 MB**, and a picture whose long edge is above
1920 pixels is sampled down before it is shown. Items that fail these checks are
dropped; they do not fail the whole list. A body that is not a JSON array, an
empty list, a timeout, or an HTTP error falls back as described above.

The address is saved on the TV. Prefer not to put an API key in it. Image URLs
are requested by the TV as well, so those URLs should be reachable from the TV
without a secret.

地址由电视自己去请求，保存在本机。不要把 API 密钥写进这个地址。图片地址也由电视拉取，
需要电视能直接打开，并且不要在图片地址里放密钥。

## Jellyfin and Emby

Jellyfin and Emby do **not** speak this format. Their library call looks more like
`GET /Users/{id}/Items?...` and returns `{"Items":[...]}` with image ids, not
`image` URLs. Tigris does not call that API and does not ask for an API key.

Point the custom source at a small proxy that reads the server and writes the
array above. [`docs/examples/jellyfin-emby-shelf.py`](examples/jellyfin-emby-shelf.py)
is one example. It is not installed or started by Tigris. It uses the first user
returned by `/Users`, keeps the API key in its own environment, and serves both
`/shelf` and the pictures so the key is not stored in the app.

```sh
JELLYFIN_URL=http://192.168.1.10:8096 JELLYFIN_API_KEY=your-key \
  python3 docs/examples/jellyfin-emby-shelf.py
```

Paste the printed `http://<this-computer>:8766/shelf` into **Custom source**, then
link the Jellyfin or Emby tile (or any other dock app that should show this library).
The computer must stay reachable from the TV. Emby uses the same `/Users` and
`/Items` routes and the `X-Emby-Token` header; set `JELLYFIN_URL` to whichever
server you run.

Jellyfin 和 Emby 的原生接口不是上面的数组。Tigris 不会直接填服务器地址或 API 密钥。
示例脚本只是一个本地转换：密钥留在运行脚本的电脑上，电视只访问脚本打印出来的地址。
把关联应用选成 Jellyfin、Emby，或任何一个你希望显示这份片库的 Dock 图标。

Apps that only show a personal cloud-drive library (for example 网易爆米花 or 夸克)
still have nothing Tigris can read. A Jellyfin proxy is the path that works
without those accounts.
