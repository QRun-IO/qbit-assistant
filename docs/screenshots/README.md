# Assistant app home and provider settings screenshots

Taken 2026-10-13 against the local `AssistantDemoServer` (qbit-assistant-provider-anthropic test sources), never production.
- Memory backend and the Next UI 1.0.0-RC.16 jar.
- The Anthropic API is the recorded test server: real HTTP through the real adapter, engine, Test connection and usage recording, with canned answers and no key.

Light and dark, 390 and 1440 px:
- `home-*`: the Assistant app home.
- `provider-*`: the provider settings screen, with the masked key and the model-call child list.
- `provider-not-set-*`: a provider whose key variable is not set.
- `provider-edit-*`: the edit form.
- `prices-*`: the model price table.

0.1.0-RC.6 additions, taken 2026-10-13 with the same demo server and the Next UI 1.0.0-RC.17 jar. The demo seeds a profile ("Ada"), two skills, and data access rows (page READ_WRITE, media NONE). Its tools run through the `/qqq/v1` UI API.

Light and dark, 390 and 1440 px:
- `home-denials-*`: the home's "Recent denials" card. The analyst's `count_records` on Blog Post got HTTP 403 from `POST /table/{tableName}/count`.
- `home-access-*`: the "Data access: what the assistant can do for you" widget (Explain access), with each table's access and each tool allowed or denied, with the reason.
- `data-access-*`: the Data access table.
- `profile-*`: a profile (persona), with its personality, its scope and its Skills child list.
- `skill-*`: a skill: its instructions and when it is used.
- `policy-*`: the policy, with its "Tools and data" section.
- `panel-persona-*`: the panel with the configured persona. This is the Next UI build of qqq-frontend-next#94; the demo's admin matches the permission-scoped "Ada (analytics)" profile.
- `panel-pending-*`: the assistant proposed `update_record` on page 12, which is READ_WRITE. The card shows the change with Save and Cancel; nothing is saved yet.
- `panel-confirmed-*`: after Save, the write ran once through `PATCH /qqq/v1/table/page/12` with the user's session. The card says Done, and the record screen behind the panel shows the new value.
