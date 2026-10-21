# AutoModpack + NeoForge installer
This is a NeoForge installer based on the bundled NeoForge Legacy Installer source. After a successful client or server installation it also downloads the matching AutoModpack mod from Modrinth.

Use it as a normal NeoForge installer for the official Minecraft launcher or a Minecraft server.

The installer writes diagnostic output to `installer.log` when run from classes or the IDE, and to `<installer-jar>.log` when run from the bundled JAR. The log includes the selected versions, installation paths, download and launcher-processing steps, and full exception stack traces.

It supports all versions that [AutoModpack mod](https://modrinth.com/mod/automodpack) supports.

Based on the NeoForge Legacy Installer.
