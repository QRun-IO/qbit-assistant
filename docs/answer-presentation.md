# Assistant answer presentation contract (v1)

Owner priority (2026-10-13): answers must ALWAYS be easy to read and act on. Bad example the owner saw, rendered on ONE line:
"| Type | Count | |---|---| | JPEG (image/jpeg) | 81 | | PNG (image/png) | 20 | | WebP, GIF, SVG | 0 |" followed by "You can open the lists filtered by JPEG or PNG" with NO links.

Root cause found in Next: `AssistantMarkdown` uses react-markdown WITHOUT `remark-gfm`, so GFM tables are not parsed and the table's lines collapse into one paragraph (soft line breaks). Backend streaming preserves `\n` (events are JSON).

## New message part: `presentation`

An assistant message's `parts` (persisted in `assistant_message.parts_json`, streamed as an SSE event, returned by the conversation API) may contain, besides `text`, `notice`, `tool`/`render` parts etc., ONE part per `present_results` call:

```json
{
  "type": "presentation",
  "summary": "101 media files are images: 81 JPEG and 20 PNG.",
  "markdown": "<server-built GFM fallback of the whole presentation, for clients that do not render blocks (CLI/TUI)>",
  "blocks": [
    { "kind": "metrics", "items": [ { "label": "Images", "value": 101, "display": "101", "href": "/app/media?filter=..." } ] },
    { "kind": "table", "title": "Media by type",
      "columns": [ { "key": "type", "label": "Type", "align": "left" },
                   { "key": "count", "label": "Count", "align": "right" },
                   { "key": "share", "label": "Share", "align": "right" } ],
      "rows": [ { "cells": { "type": "JPEG (image/jpeg)", "count": 81, "share": 0.802 },
                  "display": { "type": "JPEG (image/jpeg)", "count": "81", "share": "80.2%" },
                  "href": "/app/media?filter=..." } ] },
    { "kind": "chart", "chartType": "pie", "title": "Share by type",
      "points": [ { "label": "JPEG", "value": 81, "display": "81", "href": "/app/media?filter=..." },
                  { "label": "PNG", "value": 20, "display": "20", "href": "/app/media?filter=..." } ] },
    { "kind": "filterLinks", "items": [ { "label": "Open JPEG media (81)", "tableName": "media", "href": "/app/media?filter=..." } ] },
    { "kind": "recordLinks", "items": [ { "label": "Sunset.jpg", "tableName": "media", "recordId": "12", "href": "/app/media/12" } ] },
    { "kind": "actions", "items": [ { "label": "Show the PNG files", "prompt": "Show me the PNG media files" },
                                    { "label": "Open media", "href": "/app/media" } ] }
  ]
}
```

Rules:
- `summary` is required, one sentence, the direct answer first.
- `chartType` ∈ `bar`, `pie`, `line`; ≤ 12 points; values are numbers.
- Every `href` is built SERVER-SIDE from metadata and is an `/app` path (same rules as `TurnEngine.isAppHref`). Filtered list links are `/app/<tableName>?filter=<URL-encoded JSON {"criteria":[{"fieldName":..,"operator":..,"values":[..]}],"booleanOperator":"AND"}>`, which Next's `deserializeFilter` accepts (JSON starting with `{`). The model never writes URLs.
- `display` strings are formatted server-side from field metadata (`displayFormat`, e.g. `%,d`, `$%,.2f`; percent columns as `80.2%`); clients show `display` when present, else the raw value.
- `actions` items have either `prompt` (send as the user's next message) or `href` (navigate), never both.
- Unknown `kind`s must be ignored by clients (forward compatible); clients that do not render blocks render `markdown`.
- The SSE event is `presentation` with `{ "messageId": <id>, "part": <the part above> }`; Next's reducer appends it to the assistant message's parts.

## Tool input and implementation

The input schema is [present-results.schema.json](../qbit-assistant/src/main/resources/present-results.schema.json). `table.rowKeys` supplies one raw filter value per row; `table.rowFilter` names the table, field and optional operator (EQUALS by default). Columns receive stable server keys `c0`, `c1`, etc. Input cells remain raw scalars; each row has a parallel display map. The example keys above are illustrative.

`PresentResultsTool` is instantiated per turn. It caches `GET /metaData` with that turn's session, checks table read visibility and instance fields, validates QQQ criteria operators and builds paths using RC.5 `AiLinkBuilder`. Filter links use URL-encoded JSON accepted by Next. Record ids are encoded as opaque path segments. Metadata denial or invalid item drops that item (an invalid table drops its table/chart), with a short explanation to the model; other items survive. No data query or permission elevation happens here.

Limits: summary 300 characters, display text 2,000 characters, table 20 columns/200 rows, chart 12 points, item lists 100 entries, filters 20 criteria/200 values. Chart truncation is reported to the model. Percent columns reference a raw numeric column; a zero total yields 0.0%. Numbers still must originate in earlier data tool results; presentation is formatting, not a new source of facts.

The schema uses small named, nonrecursive definitions to avoid deeply inlined structures. Each object component is at most two levels of inline containers; the resolved table/criteria structure necessarily has the nesting shown in the owner contract. Strict is off by default. Opting `present_results` into strict mode requires all properties (empty arrays/strings, empty table and -1 for unused percentage indexes), removes provider-unsupported length constraints, and retains server validation. This has zero optional parameters and one shared scalar union in the existing strict-budget test.

Presentation parts are persisted with the assistant message and replayed as `presentation` events with `{messageId, part}` (plus the standard event sequence). They are removed by record-data retention along with tool renders. Model history and text-only consumers can use the server-built Markdown. Unknown block kinds should be ignored. Plain labels must be rendered as text, never parsed as links or HTML; fallback Markdown escapes them. Tool audit input records the accepted presentation only, excluding dropped values.
