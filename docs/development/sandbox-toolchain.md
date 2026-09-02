# Sandbox toolchain restore (workspace `.cache` and `.git/config` are wiped between sessions)

After a sandbox reset:
1. Reinstall Temurin 17 + Gradle 8.14.2 into `~/.cache/toolchain`
   (adoptium API + services.gradle.org), Android cmdline-tools 11076708 +
   `platforms;android-35` + `build-tools;35.0.0` into `~/.cache/android-sdk`
   (pre-write the two standard license hashes).
2. `git config user.name/user.email` + `git remote add origin git@github.com:99ggprooo00-code/DHUN.git`
3. SSH: `~/.ssh/dhun_sandbox` (sandbox-born deploy key) + ~/.ssh/config Host github.com entry.
