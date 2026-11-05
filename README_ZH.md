<div align="center">

<img src="docs/logo.png" width="120" alt="View Txt logo">

# View Txt

### 在光标旁的弹窗里看参考文件 —— 不必离开编辑器

**连按三次 `/`，就会在光标处把工具窗口里登记的那个文本文件显示成一页浮层。滚轮翻页，进度按项目记忆。**

[![Platform](https://img.shields.io/badge/platform-IntelliJ%20IDEA%202024.1%2B-lightgrey.svg)](#环境要求)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/build-Gradle%20IntelliJ%20Platform%202.10-02303A.svg)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
[![License](https://img.shields.io/badge/license-not%20set-lightgrey.svg)](#许可)

[English](README.md) | 中文

**[快速开始](#快速开始) · [使用](#使用) · [功能](#功能) · [常见问题](#常见问题)**

</div>

## 为什么做这个？

写代码时要随手翻的笔记、速查表、日志都在编辑器之外，看一次就得切一次窗口，而且两边的位置都会丢。这个插件把文件显示成一个跟随光标的轻量弹窗：焦点仍留在编辑器里，`Esc` 收起，下次连按三次就接着上次那一页。

- **不切窗口** —— 用 `JBPopup` 渲染在光标旁，带固定偏移，绝不压住你正在输入的字符
- **焦点留在编辑器** —— 弹窗以 `setRequestFocus(false)` 创建，开着也能继续打字
- **接着上次读** —— 每次翻页都把当前起始索引写回项目的 `viewTxt.xml`
- **改完即生效** —— 文件路径、触发键、起始索引、弹窗尺寸都在工具窗口里设置，无需重启

## 环境要求

- **IntelliJ IDEA 2024.1 及以上** —— `sinceBuild = 241`，开发基线 `2024.1.7`
- **JDK 17** —— 源码与目标字节码都是
- **Gradle 9** —— 用仓库自带的 wrapper（`gradle-9.0.0`），无需本地安装

## 快速开始

```bash
./gradlew runIde        # 启动装了插件的沙箱 IDE
./gradlew buildPlugin   # 构建可分发的 ZIP 到 build/distributions/
./gradlew ktlintCheck   # 风格检查（build 已包含）
```

也可以直接用仓库里的运行配置 `.run/Run IDE with Plugin.run.xml`，它执行 `runIde` 并收集 `idea.log`。

## 使用

1. **打开工具窗口** —— 窗口右侧的 `view-txt setting` 标签（或用 View → Tool Windows 打开，取决于你的键位）。
2. **选文件** —— 「浏览」选择文件，「保存路径」存下路径。保存新路径会把索引归零 —— 旧索引落到另一个文件里会从毫无关系的位置开始。
3. **选触发键** —— 下拉框列出 `/`、`.`、`,`、`;`、`'`、`` ` ``、`-`、`=`、`[`、`]`、`\`，「设置快捷键」即生效。
4. **读** —— 焦点回到编辑器，2 秒内连按该键 **三次**。弹窗出现在光标旁，滚轮向下翻下一页、向上翻上一页；`Esc` 或点击外部关闭。
5. **可选** —— Tools 菜单 →「Show File Content Popup」与连按三次等效，读取同一份路径与索引。

## 功能

### 弹窗

- **跟随光标** —— 固定偏移渲染在光标右下；靠近屏幕边缘时翻到另一侧并夹在屏幕内，编辑器滚动时同步跟随
- **每页长度随机** —— 每页十行、每行 10–24 个字符，读起来像正文而不是固定网格；步长会被记住，往回翻才能精确回到上一次的切分点
- **`...` 占位行** —— 灰色的一行提示后面还有内容
- **编辑器字体** —— 弹窗字体与字号取自 IDE 编辑器配色方案，每行还带文件类型图标
- **记住读到哪** —— 起始索引跨关闭弹窗、切换文件、重启 IDE 都能保留

### 设置

- **文件路径** —— 文本框中显示，可手填或用文件选择器
- **触发键** —— 十二个标点键之一，存成 `KeyEvent` 键码
- **起始索引** —— 可直接输入数字，「刷新」用于读回弹窗翻到的位置
- **弹窗尺寸** —— 宽高可设置，弹窗本身也支持鼠标缩放和拖动

### 项目级状态

一切都通过 `FileStateService` 持久化到项目 `.idea` 目录下的 `viewTxt.xml`：

| 字段 | 含义 |
| --- | --- |
| `lastSelectedFilePath` | 弹窗要读的文件 |
| `lastContentPosition` | 该文件内的起始索引 |
| `popupShortcut` | 触发键码（默认 `/`，即 `VK_SLASH`） |
| `popupWidth` / `popupHeight` | 弹窗尺寸 |

改动这些字段名会让已有配置失效，因此它们被视为稳定接口。

## 常见问题

<details>
<summary><strong>为什么是连按三次，而不是一个快捷键？</strong></summary>

普通字母或标点无法作为编辑器按键绑定 —— 那会把该键从输入里抢走；而修饰键组合又要处理与 IDE、系统快捷键的冲突。对一个平时不会连续敲的键做快速三连击，语义明确且不需要任何键位配置 —— 计数器在 2 秒无输入后重置，正常打字永远触发不了。

</details>

<details>
<summary><strong>每次弹窗都从文件开头开始。</strong></summary>

起始索引按项目保存。若它反复归零，检查是否用了工具窗口的「保存路径」或文件选择器 —— 这两处都会刻意把索引置 0。直接编辑索引框并点「更新索引」可覆盖。

</details>

<details>
<summary><strong>能读项目之外的文件吗？</strong></summary>

能。文件选择器不限制项目范围，路径按绝对字符串保存，任何可读文件都行 —— 只有**状态**是按项目隔离的。

</details>

<details>
<summary><strong>连击计数存在哪，会泄漏内存吗？</strong></summary>

计数与最后按键时间存在以编辑器实例为键的 `WeakHashMap` 里，编辑器被回收时条目随之消失 —— 长时间开着的 IDE 不会为已关闭的编辑器累积记录。

</details>

<details>
<summary><strong>分屏编辑和多项目下能用吗？</strong></summary>

每个编辑器各有自己的计数，但 `FileStateService` 是项目级的，所以同一项目的所有编辑器共享文件路径与索引；两个项目可以各读各的文件。

</details>

## 参与开发

```bash
./gradlew ktlintCheck build        # 风格检查 + 构建
./gradlew buildPlugin              # 可分发 ZIP
./gradlew verifyPlugin             # 对目标 IDE 的兼容性检查
```

Kotlin 风格取自 `.editorconfig` 里的 `intellij_idea` 代码风格，由 ktlint 强制执行。

**技术栈**：Kotlin 2.1 · IntelliJ Platform Gradle Plugin 2.10 · IntelliJ IDEA 2024.1 API · Swing / JB 组件 · ktlint

## 许可

本仓库尚未包含许可文件 —— 版权归各自作者所有，保留所有权利。如需明确授权，请开 issue 说明。
