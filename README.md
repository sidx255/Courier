# Courier - Reliable rclone transfers for Android

Courier is maintained by [sidx255](https://github.com/sidx255). It focuses on resilient, high-performance, integrity-verified phone-to-NAS and cloud data migrations.

Courier is a fork of [Round Sync](https://github.com/newhinton/Round-Sync), which itself builds on RCX and rclone. Upstream authors, maintainers, contributors, and license notices remain credited in this repository and in the app.

[![license: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE) [![GitHub release](https://img.shields.io/github/v/release/sidx255/Courier?include_prereleases)](https://github.com/sidx255/Courier/releases/latest) [![supportive flags](https://img.shields.io/badge/support-🇺🇦_🏳️‍⚧_🏳️‍🌈-4aad4e)](https://github.com/sidx255/Courier)

A resilient cloud/NAS file manager and migration tool, powered by rclone.


## Screenshots
<table>
  <tr style="border:none">
    <td style="border:none">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/1.jpg" width="360vh" />
    </td>
    <td style="border:none">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/2.jpg" width="360vh" />
    </td>
    <td style="border:none">
      <img src="fastlane/metadata/android/en-US/images/phoneScreenshots/3.jpg" width="360vh" />
    </td>
  </tr>
</table>


## Features
|                                                            Cloud Access                                                             |                                    256 Bit Encryption<sup>[1](https://rclone.org/crypt/#file-encryption)</sup>                                     |                                                         Integrated Experience                                                         |
|:-----------------------------------------------------------------------------------------------------------------------------------:|:--------------------------------------------------------------------------------------------------------------------------------------------------:|:-------------------------------------------------------------------------------------------------------------------------------------:|
| <img src="https://github.com/newhinton/Round-Sync/blob/master/docs/cloud-computing.png?raw=true" alt="Cloud Access" width="144" />  | <img src="https://github.com/newhinton/Round-Sync/blob/master/docs/locked-padlock.png?raw=true" alt="256 Bit End-to-End Encryption" width="108" /> | <img src="https://github.com/newhinton/Round-Sync/blob/master/docs/smartphone.png?raw=true" alt="Integrated Experience" width="132"/> |
|                                             Use your cloud storage like a local folder.                                             |                                         Keep your files private on any cloud provider with crypt remotes.                                          |                                  Don't give up features or comfort just because it runs on a phone.                                   |

- **File Management** (list, view, download, upload, move, rename, delete files and folders)
- **Streaming** (Stream media files, serve files and directories over FTP, HTTP, WebDAV or DLNA)
- **Integration** (Access local storage devices and share files with the application to store them on a remote)
- **Many cloud storage providers** (all via rclone config import, some without ui-setup)
- **Material 3 Design** (Dark theme)
- **All architectures** (runs on ARM, ARM64, x86 and x64 devices, Android 7+)
- **Storage Access Framework (SAF)** ([see docs](https://roundsync.com/usage/saf.html)) for SD card and USB device access.
- **Intentservice** to start tasks via third party apps!
- **Task Management** to allow regular runs of your important tasks!


## Installation

Grab the [latest Courier release](https://github.com/sidx255/Courier/releases/latest) and install the signed APK.
| CPU architecture | Where to find | APK identifier |
|:---|:--|:---:|
|ARM 32 Bit | older devices | ```armeabi-v7a``` |
|**ARM 64 Bit** | **most devices** | ```arm64-v8a``` |
|Intel/AMD 32 Bit | some TV boxes and tablets | ```x86``` |
|Intel/AMD 64 Bit | some emulators | ```x86_64``` |

If you don't know which version to pick, use `courier_<version>-oss-universal-release.apk`. Most phones, including the Samsung Z Flip 6, use `arm64-v8a`.

### Migrating from an older Courier package

The permanent Android package is `com.sidx255.courier`. Android cannot move private app data across package IDs automatically.

1. In the older Courier installation, use Settings -> Export and save the configuration ZIP.
2. Install the signed `com.sidx255.courier` APK.
3. Import that ZIP. It restores `rclone.conf`, tasks, triggers, filters, and supported preferences.
4. Verify the task and trigger lists before uninstalling the older Courier package.

After this one-time migration, normal APK updates preserve all app data because every release uses the same package ID and signing key.

### Release signing

Future releases must use the same signing key. Local credentials belong in ignored `local.properties`; CI uses the `COURIER_KEYSTORE_BASE64`, `COURIER_STORE_PASSWORD`, `COURIER_KEY_ALIAS`, and `COURIER_KEY_PASSWORD` secrets. Release builds fail when signing is not configured rather than producing an unusable unsigned update.

[<img src="https://img.shields.io/badge/Download-GitHub%20Releases-4aad4e?logo=github"
    alt="Get it on GitHub Releases"
    height="80">](https://github.com/sidx255/Courier/releases/latest)

## Usage
[See the documentation](https://roundsync.com/).


## Intents
This app includes the ability to launch an intent! Create a task to sync to a remote, and copy it's id (via the treedot-menu)
The intent needs the following:

| Intent          |                   Content                   |                 |
|:----------------|:-------------------------------------------:|----------------:|
| packageName     |              com.sidx255.courier             |                 |
| className       | ca.pkay.rcloneexplorer.Services.SyncService |                 |
| Action          |                 START_TASK                  |                 |
| Integer Extra   |                    task                     |        idOfTask |
| Boolean Extra   |                notification                 |   true or false |


## Libraries
- [rclone](https://github.com/rclone/rclone) - Calling this a library is an understatement. Without rclone, there would not be Round Sync. See https://rclone.org/donate/ to support rclone.
- [Jetpack AndroidX](https://developer.android.com/license)
- [Floating Action Button SpeedDial](https://github.com/leinardi/FloatingActionButtonSpeedDial) - A Floating Action Button Speed Dial implementation for Android that follows the Material Design specification.
- [Glide](https://github.com/bumptech/glide) - An image loading and caching library for Android focused on smooth scrolling.
- [MarkdownJ](https://github.com/myabc/markdownj) - converts markdown into HTML.
- [Material Design Icons](https://github.com/Templarian/MaterialDesign) - 2200+ Material Design Icons from the Community.
- [Recyclerview Animators](https://github.com/wasabeef/recyclerview-animators) - An Android Animation library which easily add itemanimator to RecyclerView items.
- [Toasty](https://github.com/GrenderG/Toasty) - The usual Toast, but with steroids.
- Icons from [Flaticon](https://www.flaticon.com) courtesy of [Smashicons](https://www.flaticon.com/authors/smashicons) and [Freepik](https://www.flaticon.com/authors/freepik)


## Contributing
See [CONTRIBUTING](./CONTRIBUTING.md)

Anyone is welcome to contribute and help out. However, hate, discrimination and racism are decidedly unwelcome here. If you feel offended by this, you might belong to the group of people who are not welcome. I will not tolerate hate in any way.

If you want to add more translations, see our [weblate-project](https://hosted.weblate.org/projects/round-sync/round-sync/)!

## Developing

You should first make sure you have:

- Go 1.20+ installed and in your PATH
- Java installed and in your PATH
- Android SDK command-line tools installed OR the NDK version specified in `gradle.properties`
  installed

You can then build the app normally from Android Studio or from CLI by running:

```sh
# Debug build
./gradlew assembleOssDebug

# or release build
./gradlew assembleOssRelease
```


## License
This app is released under the terms of the [GPLv3 license](https://github.com/newhinton/extract/blob/master/LICENSE). Community contributions are licensed under the MIT license, and [CLA Assistant](https://cla-assistant.io/) will ask you to confirm [a CLA stating that](https://gist.githubusercontent.com/x0b/889f037d76706fc9e3ab8ee1c047841b/raw/67c028b19e33111428904558cfda0c01039d1574/rcloneExplorer-cla-202001) if create a PR.


## About this app
This is a fork of [**RCX**](https://github.com/x0b/rcx) by **x0b**<sup>[x0b](https://github.com/x0b)</sup> which is itself a fork of [**rcloneExplorer**](https://github.com/patrykcoding/rcloneExplorer) by **Patryk Kaczmarkiewicz**<sup>[patrykcoding](https://github.com/patrykcoding)</sup> .

If you want to convey a modified version (fork), we ask you to use a different name, app icon and package id as well as proper attribution to avoid user confusion.
