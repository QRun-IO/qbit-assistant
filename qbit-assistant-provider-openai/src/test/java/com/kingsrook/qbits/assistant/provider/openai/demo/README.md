# OpenAI-compatible provider registry demo

Run from the repository root in PowerShell with Java 21. This is a manual,
memory-backed screenshot server, not a Surefire test. Nothing is persisted
between runs. No API keys or external model services are needed: the secret
resolver deliberately returns no keys, including when the shell has credentials.

Build the test classpath (the local reactor class directories come first below,
so the demo uses this checkout's code and fixture):

```powershell
.\mvnw.cmd -q -pl qbit-assistant-provider-openai -am test-compile dependency:build-classpath '-Dmdep.includeScope=test' '-Dmdep.outputFile=target/demo-classpath.txt'
if ($LASTEXITCODE -ne 0) { throw 'Demo compilation/classpath generation failed' }

$demoDependencies = (Get-Content qbit-assistant-provider-openai/target/demo-classpath.txt -Raw).Trim()
$demoFrontend = "$env:USERPROFILE/.m2/repository/com/kingsrook/qqq/qqq-frontend-next/1.0.0-RC.18/qqq-frontend-next-1.0.0-RC.18.jar"
$demoClasspath = "qbit-assistant-provider-openai/target/test-classes;qbit-assistant-provider-openai/target/classes;qbit-assistant/target/test-classes;qbit-assistant/target/classes;$demoFrontend;$demoDependencies"
java -cp $demoClasspath com.kingsrook.qbits.assistant.provider.openai.demo.OpenAiDemoServer 8099
```

The frontend artifact is `com.kingsrook.qqq:qqq-frontend-next:1.0.0-RC.18`,
already in the local Maven repository. Adjust `$demoFrontend` if your Maven
local repository is elsewhere. Omit the final port argument for the default
8099, or use another free port (the smoke check used 18099). Stop with Ctrl+C.

Startup prints these URLs (with the selected port):

- Assistant home: `http://localhost:8099/app/assistantAdmin`
- Local provider: `http://localhost:8099/app/assistant_provider/1`
- Priced and tested model: `http://localhost:8099/app/assistant_model/1`

Startup also prints `Image presentation conversation id: <id>`. Open that
conversation in the assistant panel history as `admin-demo` to capture the
answer to "How many images do I have by type?". The id is assigned on each run;
the conversation and its presentation are memory-backed and reset on restart.

Every browser session is `admin-demo`. Startup inserts an enabled, default,
keyless Local GPU (Ollama) provider, marked LOCAL and HIPAA eligible for the
synthetic demo, and a disabled CLOUD OpenRouter provider whose key is Not set.
If the Anthropic module is explicitly added to the classpath, its provider type
and a disabled Claude (production) row are also included; otherwise it is skipped.

The normal Discover models process fetches four model IDs from the localhost
mock: `qwen3:30b-a3b`, `gpt-oss:20b`, `qwen3-coder:30b`, and `bge-m3`.
A normal update sets qwen3's input/output compute prices to $0.05/$0.10 per
million tokens. The provider and model Test connection processes then each
make a real HTTP completion call, recording two TEST usage rows and the
last-test fields. The greeting turn records a third usage row with purpose
TURN. An image breakdown turn then makes three more model calls through the
real OpenAI adapter and engine: five MIME-filtered `count_records` calls,
`present_results` using the returned counts, and a short closing message.
The fixture seeds 81 JPEG and 20 PNG rows (no WebP, GIF or SVG). The presentation
includes a table with computed shares, filtered row links, a pie chart, JPEG
and PNG filter links, and a "Show the PNG files" prompt action.
Discovery timestamps/status also come from the actual process.

Replies and token counts are synthetic mock API responses (24 input / 8 output
tokens per completion), not live GPU measurements or billed cloud usage.
No usage/history rows are manually seeded. The mock remains available for
repeating discovery, connection tests and chat while taking screenshots.

Capture the P-a screens in light and dark at 390 and 1440 px; see
[`docs/screenshots/README.md`](../../../../../../../../../../../docs/screenshots/README.md)
for the planned screenshot prefixes.
