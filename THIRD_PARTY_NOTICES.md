# NikoTools dependency notices

NikoTools source code, icons, and its bundled template reference resource are
distributed under the MIT license in LICENSE. This declaration applies to the
NikoTools plugin directory, not to the rest of PopToolProject.

## IDE-provided APIs and libraries

NikoTools uses IntelliJ Platform, the Terminal plugin, the Android plugin,
Android ddmlib, JediTerm, and Gson APIs supplied by the installed IDE.
Android and Terminal integrations are optional; the core script features need
only IntelliJ Platform and its Gson library. Their
implementations are not copied into the NikoTools distribution. They retain
their respective upstream licenses and are supplied under the IDE's terms.

- IntelliJ Platform: https://github.com/JetBrains/intellij-community
- Android tools: https://android.googlesource.com/platform/tools/
- JediTerm: https://github.com/JetBrains/jediterm
- Gson: https://github.com/google/gson

## External tools

Python, PowerShell, Bash, cmd, adb, and scrcpy are not bundled or installed by
NikoTools. Users supply these tools separately; each remains subject to its
own license. NikoTools' MIT license does not relicense these tools.

## Template reference resource

The bundled poptool-guide.json is a copy of the PopToolProject help reference
used to preserve the original parameter examples. The copy distributed within
NikoTools is included in the plugin's MIT license scope. Updating this copy
does not modify the original PopToolProject resource.
