Minecraft 26.3 includes MoulConfig built from the official source revision
4eaeb73cdaebb083d1d56d310fe1851ded1b7828:
https://github.com/NotEnoughUpdates/MoulConfig/tree/4eaeb73cdaebb083d1d56d310fe1851ded1b7828

MoulConfig is licensed under LGPL-3.0-or-later. Its source headers and notices
remain intact. The complete corresponding source is available at the link
above; gradle/moulconfig263.gradle records the source archive checksum and all
build adaptations (focused targets and a stable archive version) and small compatibility fixes (one Fabric metadata entry and distinct SDL mouse key codes). The library
is embedded as a separate JAR and may be replaced with a compatible build.
The 26.1.2 and 26.2 targets continue using the published MoulConfig 4.7.2 artifacts.

Building 26.3 requires JDK 8 for MoulConfig's common sources and JDK 25 for the
Minecraft adapter. Set JAVA_HOME_8_X64 to a JDK 8 directory and JAVA_HOME to JDK 25.
GitHub Actions installs both. No upstream binary is relabeled as a26.3 port.
