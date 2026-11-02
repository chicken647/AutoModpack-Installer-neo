# AutoModpack + NeoForge installer
This is a NeoForge installer wrapper. It lists available NeoForge versions, downloads the selected official NeoForge installer from the NeoForge Maven repository, and delegates the installation to that official installer.

## About credits

To add a project credit, add an `AboutLink` with its display label and URL to the `ABOUT_LINKS` list in `src/neoforge/main/java/net/minecraftforge/installer/WrapperInstaller.java`. The list order determines the display order. Rebuild the installer to include the new credit.

## GitHub releases

No manual version tag is required. Pushing a commit to `main` runs the installer release workflow, which uses the same `neoforged/action-tag-versioning@v2` calculation as the existing NeoForge publishing workflow. It derives the next version from repository tags and the commit distance from the latest version tag, then builds and publishes the installer as `legacyinstaller-<version>-fatjar.jar` under the matching `v<version>` release tag.

The workflow runs tests and verifies the fat JAR and its embedded version before publishing. If the calculated tag already exists for the same commit, rerunning the workflow updates that release; if it points to another commit, the workflow fails instead of changing it. A calculated version with a prerelease suffix is published as a prerelease.

The installer release workflow sends start and completion notifications to the same Discord webhook used by the NeoForge publishing workflow. Configure the repository’s `DISCORD_WEBHOOK` secret; webhook delivery failures do not block a release.

To build and verify a versioned JAR locally, run `.\gradlew.bat clean test verifyFatJarContents -PreleaseVersion=1.0.54` in PowerShell. Builds without `releaseVersion` continue to use the default version `1.0.0`.