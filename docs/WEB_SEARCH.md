# Web search and fetch URL

## Tools

| Tool | Backend | Config |
|------|---------|--------|
| `web_search` | SearXNG JSON API | `SEARX_BASE_URL` in `local.properties` or DataStore |
| `fetch_url` | OkHttp + Jsoup | None |

## SearXNG

Request: `GET {base}/search?q=...&format=json&language=it`

Default instance: `https://searx.be` (via `SEARX_BASE_URL`). **Many public instances return 403/429 to automated clients.**

The app tries configured SearXNG URLs plus built-in fallbacks, then **DuckDuckGo HTML lite** automatically if SearXNG fails.

Pick another instance from [searx.space](https://searx.space) or self-host on LAN for stable SearXNG-only search.

## Extending search

`WebSearchTool` depends on `WebSearchEngine`. Chain today:

1. `SearxngWebSearchEngine` (multiple instances)
2. `DuckDuckGoHtmlWebSearchEngine` (fallback, no API key)

To add Brave/SerpApi, implement `WebSearchEngine` and insert it in `ChainedWebSearchEngine` in `ReasoningModule`.

## Typical LLM chain

1. `web_search` (`await_result: true`)
2. `fetch_url` on best result URL (`await_result: true`)
3. Summarize in Italian (`chain_status: complete`)

Do not use `fetch_url` on Google/Bing search result pages.

## Security

`fetch_url` blocks localhost and private IPs (SSRF). HTTPS preferred; HTTP allowed.
