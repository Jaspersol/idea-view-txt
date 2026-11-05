<div align="center">

# View Txt

### Read a reference file in a popup beside your caret — without leaving the editor

**The triple-tap of `/` opens the text file you registered in the tool window as a floating page at the caret. The wheel turns pages; the position is remembered per project.**

[![Platform](https://img.shields.io/badge/platform-IntelliJ%20IDEA%202025.2%2B-lightgrey.svg)](#requirements)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.1-7F52FF.svg)](https://kotlinlang.org/)
[![Gradle](https://img.shields.io/badge/build-Gradle%20IntelliJ%20Platform%202.10-02303A.svg)](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html)
[![License](https://img.shields.io/badge/license-not%20set-lightgrey.svg)](#license)

English | [中文](README_ZH.md)

**[Quick Start](#quick-start) · [Usage](#usage) · [Features](#features) · [FAQ](#faq)**

</div>

## Why View Txt?

Notes, cheat sheets and logs you consult while coding live outside the editor, so checking one costs a window switch, and you lose your place both in that file and in your code. This plugin shows the file in a lightweight popup that follows the caret: the editor keeps focus, `Esc` puts it away, and the next triple-tap resumes at the page you stopped on.

- **No window switch** — a `JBPopup` rendered at the caret, offset so it never covers the character you are typing
- **Focus stays in the editor** — the popup is created with `setRequestFocus(false)`, so you can keep typing with it open
- **Resumes where you stopped** — the current start index is written back to the project's `viewTxt.xml` after every page turn
- **Configurable without restarting** — file path, trigger key, start index and popup size are all in the tool window and take effect immediately

## Requirements

- **IntelliJ IDEA 2025.2 or later** — `sinceBuild = 252.25557`, developed against `2025.2.4`
- **JDK 21** — both source and target bytecode
- **Gradle 9** — use the bundled wrapper (`gradle-9.0.0`), no local install needed

## Quick Start

```bash
./gradlew runIde        # start a sandbox IDE with the plugin installed
./gradlew buildPlugin   # build the distributable ZIP into build/distributions/
./gradlew ktlintCheck   # style check (runs automatically inside build)
```

Or use the bundled run configuration: `.run/Run IDE with Plugin.run.xml` runs `runIde` and collects `idea.log`.

## Usage

1. **Open the tool window** — the `view-txt setting` stripe on the right edge of the window (View → Tool Windows → view-txt setting, depending on your keymap).
2. **Pick a file** — 浏览 selects a file; 保存路径 stores it. Saving a new path resets the index to 0, because an index from another file would land in the middle of unrelated text.
3. **Choose the trigger key** — the dropdown lists `/`, `.`, `,`, `;`, `'`, `` ` ``, `-`, `=`, `[`, `]` and `\`; 设置快捷键 applies it.
4. **Read it** — focus the editor and press that key **three times within 2 seconds**. The popup appears at the caret; the wheel scrolls down for the next page and up for the previous one. `Esc` or a click outside closes it.
5. **Optional** — the Tools menu → *Show File Content Popup* does the same as the triple-tap, using the same path and index.

## Features

### Popup rendering

- **Caret-following placement** — below-right of the caret with a fixed offset; near the screen edge it flips to the other side and is clamped on screen, and it follows the editor when you scroll
- **Random page lengths** — each page is ten lines of 10–24 characters, so pages read like prose rather than a fixed grid; the step list is remembered so going back reaches exactly the previous boundary
- **`...` placeholder** — a greyed line marks that more content follows
- **Editor font** — the popup uses the IDE's editor scheme font and size, and each line carries a file-type icon
- **Remembered page** — the start index survives closing the popup, switching files in the project, and restarting the IDE

### Settings

- **File path** — shown in a text field, settable by hand or via the file chooser
- **Trigger key** — one of twelve punctuation keys, stored as a `KeyEvent` key code
- **Start index** — editable as a number, with 刷新 to re-read the value the popup has advanced to
- **Popup size** — width and height are editable, and the popup is resizable and movable with the mouse

### Project state

Everything is persisted through `FileStateService` into `viewTxt.xml` in the project's `.idea` directory:

| Field | Meaning |
| --- | --- |
| `lastSelectedFilePath` | The file the popup should read |
| `lastContentPosition` | Start index inside that file |
| `popupShortcut` | Trigger key code (default `/`, i.e. `VK_SLASH`) |
| `popupWidth` / `popupHeight` | Popup size |

Renaming any of these fields invalidates existing configurations, so they are treated as a stable interface.

## FAQ

<details>
<summary><strong>Why three presses instead of a single shortcut?</strong></summary>

A single letter or punctuation key can't be bound as a plain editor keystroke without stealing it from typing, and a modifier combination would need its own conflict handling with IDE and OS shortcuts. A rapid triple-tap of a key you rarely type repeatedly is unambiguous and needs no keymap entry — the counter resets after two seconds of inactivity so ordinary typing never triggers it.

</details>

<details>
<summary><strong>The popup opens at the beginning of the file every time.</strong></summary>

The start index is saved per project. If it keeps resetting, check whether the tool window's 保存路径 or the file chooser is being used — both intentionally reset the index to 0. Editing the index field and pressing 更新索引 overrides it.

</details>

<details>
<summary><strong>Can I read a file outside the project?</strong></summary>

Yes. The file chooser has no project scope restriction, and the path is stored as an absolute string, so any readable file works. Only the *state* is project-scoped.

</details>

<details>
<summary><strong>Where do the counters live, and do they leak memory?</strong></summary>

Press counts and last-press times are kept in `WeakHashMap<Editor, …>` keyed by the editor instance, so they are discarded when an editor is collected — a long-running IDE session doesn't accumulate entries for closed editors.

</details>

<details>
<summary><strong>Does the popup work in split editors and multiple projects?</strong></summary>

Each editor gets its own counter, but `FileStateService` is project-level, so the file path and index are shared across all editors of the same project. Two projects can read different files.

</details>

## Contributing

```bash
./gradlew ktlintCheck build        # style check + assemble
./gradlew buildPlugin              # distributable ZIP
./gradlew verifyPlugin             # compatibility check against the target IDE
```

Kotlin style comes from the `intellij_idea` code style configured in `.editorconfig` and enforced by ktlint.

**Tech stack**: Kotlin 2.1 · IntelliJ Platform Gradle Plugin 2.10 · IntelliJ IDEA 2025.2 API · Swing/JB components · ktlint

## License

No license file is included in this repository yet — copyright of the respective authors, all rights reserved. Open an issue if you need explicit permission to reuse it.
