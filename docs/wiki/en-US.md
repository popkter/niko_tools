# NikoTools English User Guide

[Languages](README.md) · [中文](zh-CN.md) · [Project README](../../README.md)

This guide describes **0.1.1** and reflects the current implementation. NikoTools saves recurring scripts, generates parameter forms before execution, and displays results in the IDE's native Run console. Scripts and tool paths are shared across projects in the same IDE configuration. Working directories, file browsing, and Android device selection use the current project window.

The current UI uses Chinese labels. This guide quotes those labels alongside their English meaning so you can find the relevant controls.

## Contents

1. [Installation and prerequisites](#installation-and-prerequisites)
2. [Your first run](#your-first-run)
3. [Managing scripts and templates](#managing-scripts-and-templates)
4. [Execution types](#execution-types)
5. [Parameter template syntax](#parameter-template-syntax)
6. [Execution configuration](#execution-configuration)
7. [Parameter metadata](#parameter-metadata)
8. [Android devices](#android-devices)
9. [Output and stopping scripts](#output-and-stopping-scripts)
10. [Sharing, importing, and migration](#sharing-importing-and-migration)
11. [Practical examples](#practical-examples)
12. [Troubleshooting and FAQ](#troubleshooting-and-faq)
13. [Data locations and development](#data-locations-and-development)

## Installation and prerequisites

### IDE and installation package

The minimum IntelliJ Platform build is **242 (IntelliJ IDEA 2024.2 series)**. The plugin requires a Java 21 runtime; a compatible IDE's bundled runtime is sufficient for use. You do not need to configure a development JDK just to run the plugin. The build baseline is IDEA 2024.2.6, targeting Android Studio, IntelliJ IDEA, PyCharm, and other IntelliJ Platform IDEs. Product version numbers differ, so check platform build numbers and actual compatibility.

1. Obtain the built `NikoTools-0.1.1.zip`. For local builds, see the [project README](../../README.md).
2. Open **Settings / Preferences → Plugins → gear icon → Install Plugin from Disk** and select the ZIP.
3. Restart if prompted. Open a project and use **Tools → NikoTools** or its sidebar icon.
4. Once the plugin is published on Marketplace, it can also be installed through its listing. The publishing configuration in this project does not confirm that a listing is already available.

### External tools

The plugin does not bundle Python, PowerShell, Bash, cmd, adb, or scrcpy. Install only the tools your scripts use. Ordinary scripts do not require Android devices or adb.

Open **Settings → NikoTools**, or use **环境路径 (Environment paths)** in the tool window. You can specify absolute paths to these executable files:

| Setting | Purpose |
| --- | --- |
| `python` | Python interpreter; choose a virtual environment's Python executable when needed |
| `powershell` | PowerShell, usually `pwsh` or Windows `powershell.exe` |
| `bash` | Bash; Windows users must provide a usable Bash installation |
| `cmd` | Windows batch interpreter `cmd.exe` |
| `adb` | Android debugging tool |
| `scrcpy` | Android screen mirroring tool |

Leave a path blank to use the **PATH inherited by the IDE process**. Resolution priority is the script's interpreter override, then the global plugin setting, then PATH. An invalid explicit path causes an error without fallback. Restart the IDE after changing system PATH when necessary. A virtual environment activated temporarily in the terminal is not automatically inherited by the plugin.

## Your first run

Prepare Python first, either on the IDE's PATH or in the environment settings.

1. Open NikoTools and click **新建脚本 (New script)**.
2. Set the name to `Greeting` and select execution type `python`.
3. Enter this body:

   ```python
   import sys
   print("Hello, " + sys.argv[1])
   ```

4. Expand **执行配置 (Execution configuration)** and enter this **参数数组 JSON (Arguments JSON)**:

   ```json
   ["${name:World}"]
   ```

5. Keep the remaining defaults and click **保存 (Save)**.
6. Double-click the saved script, enter a name in **运行参数 (Run parameters)**, and click **执行 (Run)**.
7. Find the greeting and exit code in the IDE's Run window.

The input is passed as a separate process argument without embedding it in Python source. Scripts without placeholders start without a parameter form.

## Managing scripts and templates

The toolbar has five actions. Hover over an icon to see its label.

| UI label | Action |
| --- | --- |
| 新建脚本 — New script | Create a script with a name, description, type, and body |
| 批量操作 — Batch operations | Import a directory or export the script library |
| 导入 — Import | Read script JSON from the clipboard |
| 排序 — Sort | Sort by usage frequency, addition order, or name |
| 环境路径 — Environment paths | Configure global interpreters and tools |

For scripts with parameters, clicking 执行 (Run) keeps the parameter dialog open so you can edit inputs and run again. Click 关闭 (Close) to dismiss it. The dialog allows other IDE operations; reopening the same script in the same project focuses its existing dialog, and an active run still prevents duplicate launches.

The table shows name, type, and description. Double-click a script to run it. Its context menu provides **执行脚本 (Run)**, **编辑脚本 (Edit)**, **分享脚本 (Share)**, and **删除脚本 (Delete)**. Use **Shift+F10** or the context-menu key for the selected row. Press **Delete** on a selected script to request deletion (Backspace also works on macOS). Deletion asks for confirmation. Sorting is persisted across projects: usage frequency is highest first, addition order is oldest first, and names sort ascending ignoring case. Usage counts actual process launches, including reruns; cancellation and launch failures do not count. Ties retain addition order. Clicking empty table space does not operate on a script.

In the editor, **脚本模板与参数语法 (Script templates and parameter syntax)** opens reference examples, syntax help, parameter substitution previews, and code copying. A preview does not execute a command. Paste copied code into the body, choose the appropriate type and configuration, and save. There is no separate template library or “Save as template” action.

With the optional IDE Terminal plugin enabled, select text in a supported local terminal and use **新建 NikoTools 自定义脚本 (New NikoTools custom script)** from its context menu. The selection opens in the script editor and is not executed before saving. New scripts default to `powershell`; choose the appropriate type for Bash, Python, or other content. The Terminal plugin is not required for the script list or execution.

## Execution types

| Type | Meaning of the body | Typical use |
| --- | --- | --- |
| `powershell` | Commands or script source evaluated by PowerShell | PowerShell automation and Windows administration |
| `python` | Python source, or a recognized existing script file path | Data processing, file tools, and multiline logic |
| `bash` | Commands evaluated by `bash -c` | macOS/Linux commands, pipelines, and redirection |
| `batch` | Windows batch source or an existing batch file path | `.bat` / `.cmd` scripts |
| `process` | One executable name or path; arguments go in the JSON array | Launching tools such as `git`, `adb`, or `scrcpy` directly |

The table displays `process` as **外部进程 (External process)**. For example, put `git` in the body and `["status", "--short"]` in Arguments JSON. Do not put the entire `git status --short` command in the body. This type launches a program directly, without a shell, and does not interpret pipes, redirection, or shell variables. It is useful for a single executable; select a shell type for multiple commands or shell syntax.

For a one-line Python body, if the first parsed item identifies an existing file relative to the working directory, that file is run. Otherwise, the body becomes a temporary Python source file. Quote paths containing spaces and prefer Arguments JSON for arguments. Bash and PowerShell file invocations follow their language syntax, such as `bash "./scripts/task.sh"` or `& './scripts/task.ps1'`. For a batch file, put its arguments in Arguments JSON rather than after the path in the body.

Execution types do not convert scripts between platforms. `batch` requires Windows cmd; the other interpreters must be installed on the host.

## Parameter template syntax

The plugin discovers placeholders in the **body, argument array, environment values, and working directory**. Saving creates the corresponding form. Interpreter paths are not parsed as templates.

| Syntax | Meaning |
| --- | --- |
| `${name}` | Text input without an inline default |
| `${name:default}` | Text input with a default |
| `${name=default}` | Legacy default syntax |
| `${mode:On=1\|Off=0}` | Choice showing labels but substituting `1` or `0`; the first option is the default |
| `${input@file}` | File browser input |
| `${output@dir}` | Directory browser input; its metadata kind is `directory` |
| `${input@file:./data.txt}` | File input with an inline default path |

Names accept letters, numbers, underscores, and Chinese characters, but not spaces. Repeated references share the same input. Conflicting repeated defaults, types, or choices cause errors. The `\|` in this Markdown table represents a literal `|` in actual scripts.

### Var / pVal declarations

Declarations separate an internal identifier from the displayed label:

```text
Var file = ${InputFile@file}
Var mode = ${Mode:On=1|Off=0}
pVal output = ${OutputDirectory@dir}
```

Use `${file}`, `${mode}`, and `${output}` elsewhere in the body or configuration. The identifier after the declaration keyword becomes the metadata `id`; the name inside braces becomes the form label. `Var` and `pVal` are case sensitive and must occupy their own lines. The plugin removes declaration lines before execution; they are not native shell or Python variable declarations.

### Browsing and substitution

File and directory browse buttons prefer an existing input path (relative paths resolve from the current project root). Empty, invalid, or nonexistent input falls back to the **project root in the current IDE window**, and you can navigate outside the project. Without a project root, the IDE's default browsing behavior applies. Use `@file` for files and `@dir` for directories. Entering folders while browsing for a file is normal navigation. Manually entered paths are currently checked for required input only, not for existence or file/directory type.

Substitution is literal: quotes, backslashes, newlines, and shell metacharacters are not escaped automatically. Handle the target language's syntax when embedding input in source. Prefer Arguments JSON for paths or arbitrary text; each array item is a separate argument.

### Defaults and conditional arguments

Some inputs offer **设为默认值 (Set as default)** to persist their value. Boolean, choice, and device controls do not have this button. Saving changed templates synchronizes defaults, file/directory kinds, and choice options from the template. When templates are unchanged, saved custom defaults are preserved.

An argument such as `"?verbose:--verbose"` adds `--verbose` only when `verbose` is truthy. Booleans use case-insensitive `true` or `1`, numbers use nonzero values, and other kinds use nonempty values. The controlling parameter must also be declared through a template. See the conditional argument example below.

## Execution configuration

For everyday scripts, name, type, and body are enough. Expand the configuration only when needed.

| Field | Default | When to change it |
| --- | --- | --- |
| 工作目录 — Working directory | Current project root | Run in a subdirectory; relative paths resolve from the project root and the directory must exist |
| 解释器路径 — Interpreter path | Global setting, then IDE PATH | Choose a virtual environment or another interpreter; for `process`, override the actual executable |
| 依赖工具 — Required tools | Empty | Check additional tools before launch, such as `adb, scrcpy`; the selected interpreter is resolved automatically |
| 输出编码 — Output encoding | `utf-8` | Decode another output encoding, such as `GBK` |
| 超时秒数 — Timeout seconds | Blank, unlimited | Positive integer; stop the process and traceable children after the limit |
| 参数数组 JSON — Arguments JSON | `[]` | Separate arguments with templates or conditional items |
| 环境变量 JSON — Environment JSON | `{}` | Override environment values for this child process, including templates |

Arguments example:

```json
["--input", "${input@file}", "--output", "${output@dir}"]
```

Environment example:

```json
{"APP_MODE": "${mode:dev}", "LOG_LEVEL": "info"}
```

Environment JSON is optional; ordinary scripts can keep `{}`. The child inherits the IDE environment, with tool paths, an Android serial when available, and an output directory added by the runner. Required tool directories are added to the run's PATH, and full executable paths are exposed as variables such as `POPTOOLS_ADB` and `POPTOOLS_SCRCPY`, following `POPTOOLS_<UPPERCASE_TOOL>`.

The runner sets `PYTHONUTF8=1`, `PYTHONIOENCODING=utf-8`, and `PYTHONUNBUFFERED=1` after custom environment values. It also generates `POPTOOLS_OUTPUT_DIR`. The `POPTOOLS` prefix is retained for existing script compatibility.

Bash arguments are available as `$1`, `$2`, etc., with `$0` fixed to `poptool-script`. Python uses `sys.argv`; batch uses `%1`, `%2`, etc. PowerShell arguments are appended to its `-Command` invocation, so write the body according to PowerShell's invocation semantics.

## Parameter metadata

Metadata configures advanced form behavior. It is generated from templates and **is not required for ordinary scripts**. Edit the JSON array when you need numeric validation, multiline or password inputs, booleans, optional fields, or custom labels.

| `kind` | Control and behavior |
| --- | --- |
| `text` | Single-line text |
| `multiline` | Multiline text |
| `integer` | Integer, validated on submission |
| `number` | Decimal number, validated on submission |
| `boolean` | Checkbox, substituting `True` / `False` |
| `choice` | Choice using its `value`; define options in the template |
| `file` | File browser input |
| `directory` | Directory browser input |
| `secret` | Masked password input; substitution uses the actual text |
| `android_device` | Read-only serial of the IDE-selected device |

`id` matches the internal template name. `label` sets the display name, `required` controls nonblank validation, `default` supplies the initial value, and `options` provides choice labels and values. The compatibility field `placeholder` can be stored in JSON, but the current form does not display it as a hint.

For a `${count:3}` placeholder:

```json
[
  {
    "id": "count",
    "label": "Repeat count",
    "kind": "integer",
    "required": true,
    "default": "3",
    "options": []
  }
]
```

Save the script with its placeholders first, then edit the generated metadata to keep IDs consistent. Saving in the editor retains only parameters referenced by templates. File/directory/choice kinds and choice options follow the template definitions. Advanced kinds such as `integer`, `boolean`, and `secret` work with ordinary text placeholders.

`secret` masks input; it does not encrypt stored values. Using Set as default persists the actual content. Printing the value or embedding it in source can also expose it.

## Android devices

You do not need to classify scripts as adb commands. The editor has no `auto / use / none` selector. Before execution, the plugin attempts to read the **single online, usable Android device selected in the current project's IDE run toolbar**. When available, its serial becomes the child process's `ANDROID_SERIAL`.

Ordinary scripts do not require Android devices. If the Android plugin is missing or disabled, its selection API is incompatible, no device is selected, a device is offline, or multiple devices are selected, no automatic serial is injected and ordinary scripts can still launch. Selecting a device alone does not add an adb dependency.

1. Connect and authorize the device in Android Studio or an IDE with a compatible Android plugin.
2. Select one device in the current project's run toolbar.
3. Install and configure adb or scrcpy when your script uses them.
4. Run the script. The console reports the default device serial when one was obtained.

To require an IDE-selected device, declare `adb, android_device` in Required tools, or set a template-referenced parameter to `kind: "android_device"` with `required: true`. These explicit requirements block execution without a valid selected device. An optional device parameter is empty when none is available.

Selection is read at the start of the run. Changing it during execution does not update the running process. A script's explicit `ANDROID_SERIAL` overrides the automatic value; `adb -s SERIAL` follows adb's own targeting rules. Without an automatic serial, external tools use inherited environment values and their own device rules, which may succeed or report missing/multiple devices. The plugin does not scan/connect devices or restart adb services.

The legacy JSON field `android_device_mode` remains compatible with storage/import but no longer controls execution.

## Output and stopping scripts

- The current project's **Run** tab displays stdout, stderr, an exit code, and the output directory.
- A script reuses its tab by ID, including after a rename. A new run clears the previous output.
- Clicking a script again while it is starting or running focuses its existing console without launching a duplicate. Different scripts can run concurrently.
- **再次运行 (Run again)** reads the latest saved definition and initializes the form with the previous inputs.
- **停止 (Stop)** and timeouts terminate the run and traceable child processes, without stopping shared adb services. Detached programs are not guaranteed to remain traceable.
- Process stdin is closed. Interactive menus and runtime password prompts are unsupported; use pre-run parameters or noninteractive tool options.

Each run exposes `POPTOOLS_OUTPUT_DIR` for generated files. Temporary Python/batch source files are deleted after exit; nonempty output directories are retained. These directories are in system temporary storage and may still be cleaned by the OS. Copy results to your project or another chosen location for long-term storage.

## Sharing, importing, and migration

### Clipboard sharing and import

Use **分享脚本 (Share script)** to copy JSON. The recipient copies the complete JSON and clicks **导入 (Import)**. Accepted clipboard formats are:

- A v1 `poptools.custom-script` share envelope;
- A raw v1 custom tool object;
- An array of tool objects;
- An object containing a `scripts` array.

Sharing removes the interpreter override, parameter defaults, and inline template defaults, while preserving choice definitions. Literal content in commands/environment values and paths outside defaults remain present; inspect the actual content before sharing. External script files are not copied by clipboard sharing.

When an ID or title conflicts, choose **覆盖 (Overwrite)** to retain the existing ID, **另存 (Save separately)** for a new ID and unique title, or **跳过 (Skip)**. Imports report success, skips, and failures per item; one failure does not roll back successful items. Unsupported fields, kinds, and versions are rejected. `internal` and `url` executors are unsupported. PopTool-managed environments and internal resource paths need adjustment for the IDE.

Minimal importable example:

```json
{
  "schema_version": 1,
  "id": "custom.hello",
  "title": "Hello Python",
  "description": "Print a greeting",
  "executor": {
    "kind": "python",
    "command": "print('Hello from NikoTools')"
  }
}
```

### Directory export and import

Use **批量操作 → 导出目录 (Batch operations → Export directory)** to choose a destination. The plugin creates a new timestamped directory:

```text
poptool-scripts-<timestamp>/
├── tools/       # Script JSON, including defaults
└── scripts/     # Transferable external script files, if present
```

For **导入目录 (Import directory)**, select the export root. The importer prefers its `tools` subdirectory and searches JSON files recursively. Directory files should contain a single tool/share object or an array of tools; use clipboard import for a `scripts` wrapper. Before import, the current library is backed up under `poptool-backups/<timestamp>` in the IDE configuration directory. Source files are not modified.

For recognized direct references to absolute `.py/.ps1/.sh/.bat/.cmd` files, export copies the containing directory's files and rewrites the entry to a relative path. Import of transferable relative entries copies resources into `poptool-assets` in the IDE configuration directory and updates the entry path. Symbolic links are skipped. Dynamic references, resources elsewhere in the project, extra tools, and interpreter paths require separate migration; directory export is not a complete project archive.

Directory exports and automatic backups preserve defaults and suit backup/machine migration. Clipboard sharing removes defaults and suits sharing definitions. Reconfigure tool paths on the destination machine.

## Practical examples

### Python: select a file and pass it as an argument

Type: `python`. Body:

```python
import pathlib
import sys
path = pathlib.Path(sys.argv[1])
print(path)
print(path.stat().st_size)
```

Arguments JSON:

```json
["${input@file}"]
```

### Bash: select a directory

Type: `bash`. Body:

```bash
printf 'Directory: %s\n' "$1"
ls -la -- "$1"
```

Arguments JSON: `["${directory@dir}"]`. A path with spaces remains one argument.

### PowerShell: text and choices

Type: `powershell`. Body:

```powershell
Var message = ${Message:Hello}
Var mode = ${Mode:On=1|Off=0}
Write-Output '${message}'
Write-Output '${mode}'
```

This demonstrates literal substitution. Adjust string handling if the message contains single quotes.

### process: list Android devices

Type: `process`. Body: `adb`. Arguments JSON: `["devices", "-l"]`. Listing devices does not require an IDE-selected device. To read a property from the selected device, use `["shell", "getprop", "ro.product.model"]`; the injected `ANDROID_SERIAL` supplies adb's default target. Add the `android_device` requirement if an IDE-selected device is mandatory.

### Bash: conditional arguments

Type: `bash`. Body:

```bash
Var verbose = ${Verbose:False}
printf 'Argument: %s\n' "$@"
```

Arguments JSON: `["?verbose:--verbose"]`. Save, then edit `verbose` metadata to set `kind` to `boolean`. Checking the box adds `--verbose`; leaving it unchecked omits the argument. This demonstration only prints its arguments. For a real tool, replace the printing command with an invocation such as `mytool "$@"`.

## Troubleshooting and FAQ

| Symptom | What to check |
| --- | --- |
| Interpreter/tool not found | Installation, IDE PATH, and global paths; correct or clear an invalid explicit path |
| Works in terminal but not in plugin | IDE startup environment, virtual environment, working directory, and execution type |
| `process` cannot find `git status` | Put only `git` in the body and move arguments to the JSON array |
| Working directory does not exist | Default is the current project root; relative directories resolve from that root |
| Android device missing | Ordinary scripts can continue; required-device scripts need one online authorized selection and a compatible Android plugin |
| File browser enters folders | Normal navigation; use `@dir` for directory input and `@file` for file input |
| Garbled output | Match Output encoding to the external tool; Python is configured to request UTF-8 |
| Parameter/JSON errors | Use valid JSON with double quotes and correct arrays/objects; check conflicting declarations and numeric input |
| Quotes in input break source | Substitution does not escape input; use Arguments JSON or handle target-language quoting |
| Script waits for input | Runtime stdin is unavailable; use pre-run forms or noninteractive options |
| Terminal context action missing | Enable Terminal and select text; the terminal implementation must expose a compatible selection API |
| Another project shows the same scripts | The IDE library is shared; execution context still belongs to the current window |

## Data locations and development

Scripts and paths are persisted in **`options/poptool-scripts.xml` under the IDE configuration directory**, not in the project. Backups use `poptool-backups`; imported resources use `poptool-assets`. Separate IDE configuration directories do not automatically share libraries; use directory migration.

The implementation uses Kotlin and the official Kotlin UI DSL; the current UI labels are Chinese. See the [project README](../../README.md) for builds, tests, compatibility verification, and Marketplace publishing. The bilingual Overview is maintained in [`plugin.xml`](../../src/main/resources/META-INF/plugin.xml), in `description`. Rebuild and install to view the local description; the Marketplace listing updates when the new package is published.

Plugin source and template resources use the [MIT License](../../LICENSE). IDEs and external tools retain their own licenses.
