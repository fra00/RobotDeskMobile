# Web search and fetch URL

## Tools

| Tool | Backend | Config |
|------|---------|--------|
| `web_search` | SearXNG JSON API + DDG HTML fallback | `SEARX_BASE_URL` in `local.properties` or DataStore |
| `fetch_url` | OkHttp + Readability4J + legacy Jsoup fallback | None |

## SearXNG

Request: `GET {base}/search?q=...&format=json&language=it`

Default instance: `https://searx.be` (via `SEARX_BASE_URL`). **Many public instances return 403/429 to automated clients.**

The app tries configured SearXNG URLs plus built-in fallbacks, then **DuckDuckGo HTML lite** automatically if SearXNG fails.

Pick another instance from [searx.space](https://searx.space) or self-host on LAN for stable SearXNG-only search.

`web_search` defaults to **5** results (max 5).

## fetch_url — extraction and limits

Implementation: [`FetchUrlTool.kt`](../app/src/main/java/com/example/mydeskrobot/integration/tool/remote/FetchUrlTool.kt), [`WebArticleExtractor.kt`](../app/src/main/java/com/example/mydeskrobot/integration/tool/remote/WebArticleExtractor.kt).

| Stage | Limit | Notes |
|-------|-------|-------|
| HTML download | **4 MB** hard cap | Full document downloaded and parsed; error `PAGE_TOO_LARGE` if exceeded (no mid-HTML truncation) |
| Article extraction | Readability4J | Mozilla Reader View algorithm on complete HTML; legacy `article/main/body` if Readability returns empty |
| Text to LLM | default **3500** char, hard cap **4500** | Only extracted article text is truncated for the LLM context |

### Parameters

| Param | Default | Description |
|-------|---------|-------------|
| `url` | required | HTTPS/HTTP page URL |
| `max_chars` | 3500 | Characters returned in `content` (200–4500) |
| `start_char` | 0 | Offset into full extracted article (chunking) |

### Success response fields

`url`, `title`, `content`, `excerpt` (optional), `chars_total`, `chars_returned`, `start_char`, `truncated`, `extractor` (`readability` | `legacy`).

### LLM retry policy (prompt)

- `fetch_url` error or empty body → next URL from `web_search`
- `truncated: true` → `start_char + chars_returned` or second source (max 2 `fetch_url` per user question)
- Deep research → up to 2 article URLs before `complete`

**Not supported:** JavaScript-rendered SPAs (no headless browser). Use another URL or `open_browser`.

## Extending search

`WebSearchTool` depends on `WebSearchEngine`. Chain today:

1. `SearxngWebSearchEngine` (multiple instances)
2. `DuckDuckGoHtmlWebSearchEngine` (fallback, no API key)

To add Brave/SerpApi, implement `WebSearchEngine` and insert it in `ChainedWebSearchEngine` in `ReasoningModule`.

## Typical LLM chain

1. `web_search` (`max_results: 5`, `await_result: true`)
2. `fetch_url` on best article URL (`max_chars: 4000`, `await_result: true`)
3. Summarize in Italian (`chain_status: complete`)

Do not use `fetch_url` on Google/Bing search result pages.

## Security

`fetch_url` blocks localhost and private IPs (SSRF). HTTPS preferred; HTTP allowed.

## Dependency

Article extraction: [Readability4J](https://github.com/dankito/Readability4J) `1.0.8` (Jsoup transitive excluded; project uses Jsoup `1.18.3`).
