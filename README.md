# NetBeans Kotlin Plugin (Modernized)

[![Status](https://img.shields.io/badge/status-work%20in%20progress-orange.svg)]()
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](http://www.apache.org/licenses/LICENSE-2.0)

> [!WARNING]
> **Work in Progress (Active Development):**  
> This project is currently a **work in progress**. While core features (compilation bridge, code completion, indentation, NetBeans 31 integration) are functional, ongoing stabilization, testing, and feature enhancements are actively under development.

> [!IMPORTANT]
> **Project Origin & Disclaimer:**  
> This project originates from the NetBeans `contrib` repository, based on the original Kotlin NetBeans plugin donated to the community by **JetBrains s.r.o.** under the [Apache License 2.0](http://www.apache.org/licenses/LICENSE-2.0).  
> While the original JetBrains repository was archived in 2017, this modernized edition updates and revives the plugin to support modern Apache NetBeans releases, recent Kotlin compiler versions, and modern Java runtimes.

## Current Specifications & Modernization

| Specification | Details |
| :--- | :--- |
| **Status** | **Work in Progress (Active Development)** |
| **Target IDE** | **Apache NetBeans 31** (`RELEASE310`) |
| **Kotlin Language & Compiler** | **Kotlin 2.4.21** (Language & API level 2.x) |
| **Java Platform** | **JDK 21** target (Compatible with JDK 21 to 25+) |
| **License** | **Apache License 2.0** |

### Key Improvements in This Edition
* **Modernized Toolchain:** Built with `nbm-maven-plugin 14.5` targeting Java 21 bytecode, removing deprecated JDK internals access.
* **Kotlin 2.x Integration:** Uses `kotlin-compiler 2.4.21` directly, eliminating conflicting legacy IntelliJ core jars and upgrading compiler bridge subsystems.
* **Modern Indentation & Formatter:** Replaced the legacy broken formatter with an integrated indentation and formatting engine compatible with NetBeans 31 APIs.
* **Enhanced Autocomplete & Diagnostics:** Upgraded completion, semantic analysis, diagnostics, and light-classes generation for Kotlin 2.x constructs.
* **Maven & Gradle Project Templates:** Built-in project creation wizards and templates for Kotlin Maven and Gradle projects.
* **Optimized Execution & Headless Tests:** Improved automated test suite execution and seamless Maven runner integration.

## Building and Installing from Source

### Prerequisites
* JDK 21 or newer installed and configured in your environment.
* Apache Maven 3.8+ (or use the included Maven wrapper).

### Build NBM Package
Run the Maven build command:
```bash
mvn clean package
```
This produces the NetBeans Module package (`.nbm`) at:
`target/nbm/kotlin.netbeans.plugin-0.2.0-SNAPSHOT.nbm`

### Installation in Apache NetBeans
1. Launch **Apache NetBeans IDE** (version 31+ recommended).
2. Navigate to **Tools** > **Plugins** in the top menu.
3. Open the **Downloaded** tab and click **Add Plugins...**.
4. Browse and select `target/nbm/kotlin.netbeans.plugin-0.2.0-SNAPSHOT.nbm`.
5. Click **Install**, follow the wizard prompts, and restart NetBeans if prompted.

## Acknowledgments & Tooling

* **Original Author:** JetBrains s.r.o. for creating and open-sourcing the original NetBeans Kotlin plugin under the Apache License 2.0.
* **Apache NetBeans Community:** For maintaining the Apache NetBeans platform, APIs, and tooling.
* **Modernization & AI Assistance:** The architectural modernization, deep-reflection patches, and reverse engineering across 9+ years of API evolution were developed with the assistance of **Google Antigravity** (Advanced Agentic Coding).

---

## Original Project Documentation (Historical Reference)

[![JetBrains team project](https://jb.gg/badges/obsolete.svg)](https://confluence.jetbrains.com/display/ALL/JetBrains+on+GitHub)
[![TeamCity (simple build status)](https://img.shields.io/teamcity/http/teamcity.jetbrains.com/s/Kotlin_NetBeansPlugin.svg)](https://teamcity.jetbrains.com/viewType.html?buildTypeId=Kotlin_NetBeansPlugin&branch_Kotlin=%3Cdefault%3E&tab=buildTypeStatusDiv)
[![GitHub license](https://img.shields.io/badge/license-Apache%20License%202.0-blue.svg?style=flat)](http://www.apache.org/licenses/LICENSE-2.0)

# Kotlin plugin for NetBeans IDE

**NOTE**: This plugin is no longer actively developed. See https://github.com/JetBrains/kotlin-netbeans/issues/122 for more information.

## Installing Kotlin plugin

### NetBeans Update Center

The plugin could be installed via NetBeans Update Center.

### Manual installation

1. Download the latest release: [0.2.0.1](https://github.com/JetBrains/kotlin-netbeans/releases/tag/v0.2.0.1)
2. Launch NetBeans IDE
3. Choose **Tools** and then **Plugins** from the main menu
4. Switch to **Downloaded** tab
5. On the **Downloaded** tab click **Add Plugins...** button
6. In the file chooser, navigate to the folder with downloaded plugin. Select the NBM file and click OK. The plugin will show up in the list of plugins to be installed.
7. Click **Install** button in the Plugins dialog
8. Complete the installation wizard by clicking **Next**, agreeing to the license terms and clicking **Install** button.


## Plugin feature set

1. Syntax highlighting
2. Semantics highlighting
3. Diagnostics
4. Code completion
5. Navigation in Source Code
6. Quick fixes
7. Intentions and Inspections
8. Occurrences finder
9. Code folding 
10. Unit testing support
11. Ant, Maven and Gradle support
12. Navigation by Kotlin class name
13. Debugging support
