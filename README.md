# AutoModpack + NeoForge installer
This is a thin NeoForge installer wrapper. It lists available NeoForge versions, downloads the selected official NeoForge installer from the NeoForge Maven repository, and delegates the installation to that official installer.

Choose a client installation, server installation, or fat installer from the graphical interface. The wrapper does not build or embed a NeoForge installer, so the selected official release performs the actual installation.

The installer writes diagnostic output to `installer.log` when run from classes or the IDE, and to `<installer-jar>.log` when run from the bundled JAR. The log includes the selected versions, installation paths, download and launcher-processing steps, and full exception stack traces.

The command-line form is `java -jar legacyinstaller-1.0.0-fatjar.jar <action> <version> <directory>`, where action is `--installClient`, `--installServer`, or `--fat-installer`. For `--fat-installer`, the directory argument receives `neoforge-<version>-fat.jar`.

NeoForge 26.3.x requires Java 25 or newer to run. Server installations add a Java version check to `run.bat`; install Java 25 or newer and place it first on `PATH` if the check reports an older runtime.
