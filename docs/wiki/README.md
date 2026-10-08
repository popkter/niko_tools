# NikoTools Wiki

NikoTools 是 IntelliJ Platform IDE 的自定义脚本插件。保存常用命令，使用参数表单填写输入，在当前项目的 Run 控制台查看结果。

NikoTools is a custom script plugin for IntelliJ Platform IDEs. Save recurring commands, collect inputs through parameter forms, and inspect results in the current project's Run console.

- [中文使用指南](zh-CN.md)
- [English user guide](en-US.md)
- [项目 README / Project README](../../README.md)
- [MIT License](../../LICENSE)

本文档对应当前项目版本 **0.1.1**。界面当前使用中文；英文指南保留实际界面名称并给出英文解释。

These guides describe version **0.1.1**. The current plugin UI uses Chinese labels; the English guide includes their translations.

## Marketplace / IDE Overview

插件详情的 Overview 内容来自 [`plugin.xml` 的 `description`](../../src/main/resources/META-INF/plugin.xml)，包含中英文介绍。修改后重新构建并安装 ZIP，即可查看本地插件详情；Marketplace 上的内容需随新版插件上传并发布后更新。

The bilingual Overview comes from the `description` in [`plugin.xml`](../../src/main/resources/META-INF/plugin.xml). Rebuild and install the ZIP to view the local plugin description. Updating the Marketplace listing requires uploading and publishing the updated plugin.

描述使用 CDATA 包裹的基础 HTML，而不是 Markdown；详见 [JetBrains 官方配置文档](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__description)。Wiki 是仓库内的完整指南，Overview 是随安装包提供的使用概览；当前源码链接若迁移，应同步更新 `plugin.xml` 和项目 README。

The description uses basic HTML inside CDATA, as documented in the [JetBrains plugin configuration reference](https://plugins.jetbrains.com/docs/intellij/plugin-configuration-file.html#idea-plugin__description). The repository Wiki provides the full guide, while the packaged Overview provides a concise introduction. Update the source links in `plugin.xml` and the project README if the repository moves.
