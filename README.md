# NikoTools — JetBrains IDE 自定义脚本插件

## 使用指南 / User guides

- [项目 Wiki / Wiki index](docs/wiki/README.md)
- [中文完整使用指南](docs/wiki/zh-CN.md)
- [English user guide](docs/wiki/en-US.md)

插件市场及 IDE 插件详情的双语 Overview 内容在 [`plugin.xml`](src/main/resources/META-INF/plugin.xml) 的 `description` 中维护，随构建安装包提供。完整指南覆盖安装、执行类型、参数模板、高级配置、Android 设备、分享迁移与排错。

## 开源许可

NikoTools 插件源码、图标和随插件提供的模板参考资源采用 [MIT License](LICENSE)。允许使用、修改、分发和商业使用，须保留完整的版权及许可声明；软件按原样提供，不提供担保。

Copyright (c) 2026 popkter。公开源码：[popkter/PopToolProject — plugins/jetbrains](https://github.com/popkter/PopToolProject/tree/develop/plugins/jetbrains)。

许可范围为此插件目录，不改变 PopToolProject 其他目录的授权条款。IDE、Android 插件及外部运行工具保留各自的许可，详见 [依赖说明](THIRD_PARTY_NOTICES.md)。插件 JAR 的 `META-INF` 中包含许可文本。

支持 IntelliJ Platform **242 及以上（IDEA 2024.2+）**的 Android Studio、IntelliJ IDEA、PyCharm 等 IDE，使用 IDE 自带的 Java 运行插件，字节码要求 Java 21 或更高。构建基线为 IntelliJ IDEA Community `2024.2.6`。插件不打包脚本解释器、adb 或 scrcpy。

脚本管理、参数、导入导出和运行控制台仅依赖 IntelliJ Platform。Android 与 Terminal 均为可选依赖：安装并启用 Terminal 插件时启用终端选区入口；安装并启用提供兼容设备选择 API 的 Android 插件时启用 IDE 所选 Android 设备读取；接口不兼容时明确提示，不影响普通脚本。缺少这两个插件不会阻止 NikoTools 加载和执行普通脚本。

## 开发和本地构建

此目录是基于 [IntelliJ Platform Plugin Generator](https://plugins.jetbrains.com/generator) 生成的独立 Gradle 项目。保留 Gradle Wrapper、版本目录及 `.run` 配置，插件实现使用 Kotlin，位于 `src/main/kotlin`，不包含生成器的示例工具窗口。

全部 19 个插件实现类使用 Kotlin，继续生成 Java 21 字节码。Kotlin 语言与标准库 API 设为 1.9，以兼容 242 平台自带的 Kotlin 1.9.24；插件使用 IDE 提供的标准库，不额外打包 Kotlin 运行库，也不要求用户启用 Kotlin 语言支持插件。插件 ID、扩展类名、`poptool-scripts.xml` 及 JSON 数据字段保持兼容。

界面使用官方 Kotlin UI DSL 声明式布局：设置页使用 `BoundConfigurable` 与状态绑定，编辑器、参数表单和模板预览使用 DSL 行、分组与校验；工具窗口及运行控制台组合 IDE 原生控件，不再手写 Swing 布局。UI DSL 和原生控件底层由 IntelliJ Platform 的 Swing UI 系统承载。

JUnit 测试入口及迁移测试位于 `src/test/kotlin`。原有 `CoreTests.java` 保留为迁移行为的回归对照，`IntegrationSmoke.java` 保留为 Windows 独立 IDE 验收工具；两者均不进入发布安装包。

要求 JDK 21；Gradle 由 Wrapper 自动下载，无需另行安装。默认编译 SDK 为 IntelliJ IDEA Community `2024.2.6`。核心代码按最低支持版本编译；较新的 Reworked Terminal 和可选 Android API 通过兼容适配访问，避免旧 IDE 加载时链接到不存在的类。首次构建会下载 Gradle、插件构建工具和 IDE SDK。运行时依赖仍为可选，不要求其他 IDE 安装 Android 插件。

macOS/Linux：

```bash
./gradlew build
```

使用本机 IDEA 2024.2 作为编译 SDK，避免重复下载 IDE SDK（运行 Gradle 的 JDK 仍需 Java 21）：

```bash
export JAVA_HOME='/Applications/Android Studio.app/Contents/jbr/Contents/Home'
./gradlew build -PlocalIdePath='/Applications/IntelliJ IDEA.app'
```

Windows：

```powershell
.\gradlew.bat build
# 或使用本机 IDEA 2024.2 作为编译 SDK
.\gradlew.bat build -PlocalIdePath='你的 IDEA 2024.2 安装目录'
```

构建产物为 `build/distributions/NikoTools-0.1.1.zip`，插件版本来自 `gradle.properties`。在目标 IDE 的 **Settings → Plugins → 齿轮 → Install Plugin from Disk** 选择 ZIP，按提示重启，然后打开 **Tools → NikoTools**。

构建逻辑统一由 Gradle 管理，无需 Bash 或 PowerShell 包装脚本。`build` 执行检查并生成插件 ZIP；`assemble` 只打包，`check` 只检查，`buildPlugin` 也可直接用于生成 ZIP。Gradle Wrapper 的 `gradlew` / `gradlew.bat` 是各系统的标准启动器；Windows 启动器可在 cmd 中运行，无需安装 PowerShell。

在 IntelliJ IDEA 中打开本目录即可导入 Gradle 项目，将 Gradle JVM 设为 Java 21，然后直接运行共享配置，无需使用命令行脚本：

- **Build Plugin** → `build`，运行检查并生成安装 ZIP。
- **Run IDE with Plugin** → `runIde`，启动独立开发沙箱。
- **Run Tests** → `check`，包含原有核心回归测试。
- **Run Verifications** → `verifyPlugin`，检查 Android Studio、IntelliJ IDEA、PyCharm 的二进制兼容性。

```bash
./gradlew runIde
./gradlew verifyPlugin
# 用两套已安装的 IDE 验证同一份按旧平台编译的插件
./gradlew verifyPlugin -PlocalIdePath='/Applications/IntelliJ IDEA.app' -PverificationIdePath='/Applications/Android Studio.app'
```

插件 ID `com.poptools.scripts` 和已有配置文件名称保持不变，可继续读取既有脚本库。最低平台版本为 `242`（2024.2），不设置 `until-build` 上限。2024.1 及更早版本使用 Java 17，当前 Java 21 插件不支持。未来平台版本仍可能改变 API，不设置上限不等于保证永久兼容；IDE 内交互仍需在各目标产品上验收。

本次 0.1.1 已在本机 IDEA 2024.2.6（IU-242.26775.15）和 Android Studio（AI-253.32098.37.2534.15336583）运行核心测试，并用官方 Plugin Verifier 验证同一个按 242 编译的 ZIP，两者均判定 Compatible。新版终端选区适配测试在 253 上通过，在 242 上验证缺失新版 API 时的回退。验证器仍报告弃用 API（253 上包括计划移除的 API），后续平台升级需要继续回归；二进制兼容性检查不替代真实 IDE UI 和设备交互验收。

## GitHub 构建与发布

将本目录提交到独立 GitHub 仓库后，`.github/workflows/build.yml` 在 push、PR 和手动运行时执行 `build`，上传插件 ZIP 与 SHA-256 文件。workflow 的手动 `verify_plugin` 选项用于执行兼容性验证。此外，独立任务在 Android Studio 平台 253 上执行核心与新版终端适配测试，发布 ZIP 仍由 242 基线任务生成。此项目不再使用 PopToolProject 的桌面应用 Release workflow。

## Marketplace 发布

首次发布通过 [JetBrains Marketplace](https://plugins.jetbrains.com/) 手动上传 `build/distributions/NikoTools-0.1.1.zip`，创建 Vendor 并补齐联系邮箱、网站、MIT 许可证和公开源码链接。迁移到独立仓库后，需要更新 `plugin.xml` 中的源码及许可证链接。

后续递增 `gradle.properties` 的 `version` 并构建。Gradle 已提供 `signPlugin`、`publishPlugin` 任务；签名使用 `CERTIFICATE_CHAIN`、`PRIVATE_KEY`、`PRIVATE_KEY_PASSWORD`，上传使用 `PUBLISH_TOKEN` 环境变量。密钥不写入仓库。GitHub workflow 只构建，不自动上传 Marketplace。

## 使用

- **终端选区**：在本地终端选中文本，右键选择“新建 NikoTools 自定义脚本”。选区进入脚本正文，保存前不执行。支持经典终端和 Reworked 终端。
- **全局操作**：顶部原生图标工具栏提供新建脚本、批量操作、导入、排序和环境路径五项，悬停显示操作说明。
- **脚本管理**：列表按名称、类型、说明展示，使用 IDE 原生表格及主题选中效果。右键脚本行可执行、编辑、分享或删除该脚本；菜单采用原生图标和分隔线。双击脚本直接进入执行流程，有参数时仍先填写参数。右键或双击列表空白处不会操作脚本；选中后可用 Shift+F10 打开菜单，按 Delete 弹出删除确认（macOS 也支持 Backspace）。顶部“排序”可按使用频率（高到低）、添加顺序（先添加在前）或名称（忽略大小写升序）排列，排序方式跨项目持久化。使用频率按实际启动次数累计，再次运行也计入，取消或启动失败不计入；同频率或同名时保留添加顺序。
- **脚本模板**：在新建或编辑弹窗中打开“脚本模板与参数语法”，复用原项目帮助页的示例代码，可预览参数替换结果并复制代码；不新增独立模板库或“保存为模板”功能。
- **参数**：点击“执行”后保留参数弹窗，可修改参数并再次执行；点击“关闭”或窗口关闭按钮结束填写。弹窗不阻塞 IDE 操作，同一项目中重复打开同一脚本会聚焦已有弹窗，脚本运行期间仍阻止重复启动。保存时根据正文、参数数组、环境变量和工作目录生成输入表单。支持 `${名称}`、`${名称:默认值}`、`${名称=旧格式默认值}`、`${模式:开启=1|关闭=0}`、`${路径@file}`、`${路径@dir}`，以及 `Var/pVal` 声明。声明行在执行前移除，替换保留 PopTool 的字面语义。
- **执行配置**：可设置工作目录、解释器、依赖工具、输出编码、超时、参数数组及环境变量。高级参数元数据保留 PopTool 的必填、文本、多行、数字、布尔、下拉、文件、目录、密码和 Android 设备类型。
- **输出**：当前项目中每个脚本复用一个 IDE Run 标签，显示打印、错误和退出码，可停止或再次运行。完成后重新执行会清空旧输出；脚本改名后仍按脚本 ID 复用标签。启动中或运行中重复点击会切回已有控制台，不额外启动进程。不同脚本可并发运行且各自保留标签，关闭标签后下次执行会重新创建。参数在运行前填写；运行中的标准输入关闭，不支持交互菜单。

示例：

```text
Var message = ${消息:你好}
Var mode = ${模式:开启=1|关闭=0}
Write-Output '${message}'
Write-Output '${mode}'
```

参数值按原语法直接替换；脚本作者负责按照目标语言正确处理引号。

## 环境路径

打开 **Settings → NikoTools**，或点击脚本管理界面的“环境路径”，可指定 Python、PowerShell、Bash、cmd、adb 和 scrcpy 可执行文件的绝对路径。

优先级：**脚本解释器路径 → 插件设置路径 → IDE 进程继承的系统 PATH**。显式路径无效时报错，不自动回退。系统 PATH 中 PowerShell 优先查找 `pwsh`，再查找 `powershell`；Python 查找 `python`，再查找 `python3`。

修改系统 PATH 后通常需要重新启动 IDE。插件不继承终端中临时激活的虚拟环境，不自动搜索项目 Android SDK，不安装环境或管理 adb 服务。声明依赖时填工具名称，例如 `adb, scrcpy`。只有当前脚本所需依赖会阻止运行。

配置工具目录会加入本次运行的 PATH；同时提供 `POPTOOLS_ADB`、`POPTOOLS_SCRCPY` 等完整路径环境变量。工具文件名与调用名不同的脚本，应使用对应完整路径变量。

## Android 设备

运行时自动尝试读取当前项目运行工具栏中唯一、在线且可用的 Android 设备，并将其序列号设置为本次子进程的 `ANDROID_SERIAL`。编辑器不再提供 `auto / use / none` 选择，也不通过脚本正文判断是否使用 adb。

没有启用 Android 插件、API 不兼容、未选择设备、设备离线或选择多个设备时，普通脚本仍可启动，不自动注入设备序列号。仅有设备选择不会让普通脚本额外依赖 adb；工具依赖仍由脚本的「依赖工具」配置决定。

已有脚本 JSON 中的 `android_device_mode` 字段保留以兼容导入与存储，但不再控制执行。显式声明 `android_device` 依赖或必填 `android_device` 参数的脚本仍要求有效设备；非必填设备参数在没有设备时为空。

执行中切换 IDE 设备不改变本次默认目标。脚本显式设置的 `ANDROID_SERIAL` 或 `adb -s` 等设备参数可覆盖自动目标。没有 IDE 所选设备时，外部工具按自身规则选择目标或报告错误；插件不会扫描、连接设备或重启 adb 服务。

使用所选设备的 scrcpy 脚本可以显式传递序列号，例如 PowerShell 使用 `scrcpy -s $env:ANDROID_SERIAL`。

## 数据导入与存储

“导入”读取剪贴板中的脚本定义；“分享”将当前脚本复制到剪贴板，并像原 PopTool 一样移除参数默认值、保留下拉选项定义。支持 PopTool `poptools.custom-script` v1 分享 JSON、原始 v1 工具 JSON、工具 JSON 数组，以及包含 `scripts` 数组的对象。

冲突时选择覆盖、另存或跳过；批量导入逐条报告失败，不修改原文件。未知字段、未知参数类型或不支持的执行类型会明确报错；`internal`、`url` 运行方式不属于本次脚本迁移范围。PopTool 内部资源路径、托管环境和专用环境变量不能直接在 IDE 中复用，需要在导入后改为系统路径。

脚本和工具路径使用 IDE 用户级配置 `options/poptool-scripts.xml` 持久化，跨项目共享。默认工作目录在每次运行时解析为当前项目根目录；相对目录基于项目根目录。文件与目录选择器优先打开输入框中的有效路径（相对路径基于当前项目根目录）；路径为空、无效或不存在时从当前 IDE 窗口的项目根目录打开。仍可浏览并选择项目外的路径；无项目根目录时使用 IDE 的默认选择行为。

“批量操作”提供批量目录导入和导出，保留默认值。导入前将当前库备份到 IDE 配置目录的 `poptool-backups`，读取所选目录（优先使用其中的 `tools` 子目录）的脚本 JSON，不修改来源文件。直接引用导出目录内 `.py/.ps1/.sh/.bat/.cmd` 文件的脚本会复制到 IDE 配置目录的 `poptool-assets`，保留同目录依赖并调整入口路径。导出在所选目录内新建带时间戳的目录，定义存入 `tools`；直接引用绝对路径脚本文件时，复制其所在目录的文件到 `scripts` 并改为相对入口，以便重新导入。动态引用、工作目录之外的资源及外部解释器路径仍需用户自行迁移或配置。

## 验证

`./gradlew check`（或 Windows 的 `./gradlew.bat check`）运行核心语法、数据格式、环境解析和终端兼容性检查，JUnit 报告位于 `build/reports/tests/test`。在 242 上验证不存在新版终端 API 时的回退；在 253 上额外验证真实新版终端接口的选区读取与空选区行为。

可选 PopTool 对照数据需要原项目 Python 环境；生成器现在显式接受其路径，独立插件构建不依赖 PopToolProject：

```bash
/路径/PopToolProject/.venv/bin/python ./verify_poptool_compatibility.py ./build/poptool-compatibility.json --poptool-root /路径/PopToolProject
./gradlew check -PcompatibilityFixture=./build/poptool-compatibility.json
```

Windows Android Studio 253+ 集成 smoke（单独编译其 IDE 专用测试类，不随普通测试或发布包打包）：

```powershell
./smoke.ps1 -IdePath '你的 Android Studio 安装目录'
```

此 PowerShell 命令仅用于可选的 Windows 集成验收，普通构建不依赖它。它直接调用 Gradle 构建后启动独立 Android Studio 开发实例，配置、缓存、插件和测试项目都位于 `build/smoke-时间戳`，不安装到日常 IDE 配置中。测试包额外包含集成检查类，发布 ZIP 不包含该类。检查结果写入该目录的 `report.txt`，日志位于 `log/idea.log`；测试结束自动退出开发实例。

人工验收应覆盖真实设备的在线、离线、授权和多设备选择，以及已安装的 Python/Bash/scrcpy 实际脚本。使用停止操作时只终止本次运行的可追踪进程树，不停止共享 adb 服务；脚本主动脱离父进程的程序不保证可以追踪。
