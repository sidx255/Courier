# Courier - Reliable rclone transfers for Android

Courier is maintained by [sidx255](https://github.com/sidx255). It focuses on resilient, high-performance, integrity-verified phone-to-NAS and cloud data migrations.

Courier is a fork of [Round Sync](https://github.com/newhinton/Round-Sync), which itself builds on RCX and rclone. Upstream authors, maintainers, contributors, and license notices remain credited in this repository and in the app.
[![license: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](https://github.com/newhinton/Round-Sync/blob/master/LICENSE) [![Latest Downloads](https://img.shields.io/github/downloads/newhinton/round-sync/latest/total
)](https://github.com/newhinton/Round-Sync/releases) [![GitHub release](https://img.shields.io/github/v/release/newhinton/Round-Sync?include_prereleases)](https://github.com/newhinton/Round-Sync/releases/latest) [![F-Droid](https://img.shields.io/f-droid/v/de.felixnuesse.extract?logo=fdroid&logoColor=white)](https://f-droid.org/packages/de.felixnuesse.extract/) [![IzzyOnDroid](https://img.shields.io/endpoint?url=https://apt.izzysoft.de/fdroid/api/v1/shield/de.felixnuesse.extract&logo=data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAADAAAAAwCAMAAABg3Am1AAAA4VBMVEXn9cuv7wDB9iGp4x2k5gKh3B6k3SyAxAGd4ASo6gCv5SCW2gHA7UTB6V+EwiOw3lK36zC+422d1yO78SWs3kfR7JhQiw2751G7+QCz8gCKzgGq3zay5DSm2jrF9jZLfwmNyiC77zXO7oaYzjW37CLj9Lze8LLA43uz3mK19ACR1QBcnRO78R6ExBek1kbE8FLI6nSPu0jH5YJxtQ2b1RiAmz53uwF7pitZkAeX1w7I72TY8KTO8HXD7La+0pKizWBzhExqjytpmR+UzSTA5Ctzy3uv1nOv3gyF3UuCsDRHcEx7M2pHAAAAS3RSTlP//////////////////////////////////////////////////////////////////////////////////////////////////wDLGfCsAAAB9ElEQVRIx72W53biMBCFhY0L7g0bTAktQEwgdMhuerbO+z/Q2sBiY0uKcvacnX8a3Y/R8YyuQPDJQP8KoExcro6ZC6C4TQXQx/oLABV3cfozgBgL/AWY9ScAsR7oBCD2AmSAoD8A+J3cWYECdBEaVm2z+U1hAuDx4fr6a08PGuuf6cmys5QvMEz0c12zhPWaAYBq9emp9/DlTrMUXsBOaw5Yjl5elrG+u9tYAxbAtjeL+Z3Wdl83Ovfr3BQyYAZBoLXbHDfQ2hykTSEAAIu+2LRcl4tD6UCm67jPCvD4/ON5YRhGpzOdrlar74fT5IcvOxDD0Xg0nvU7hjGVttv+0vYyAgyQdNgeey3Hce5DSZqN9GZmvzh8UO0F3thsiY4gqGoUtuL2AeaKpom5brVMryEKvCyXZVX0urd0wOxy4qwh8jxfLlcqZafpYoH0MzQGnNI/6CulOASFc/NWlZ17ADEG3oWjvn5TEvjbfJuyrnFaSfdyrK/f1Gp1tTAHF750aqgUJUCsr5UizFUv3EeQwmOFekmVmABDCiNVlqNwOwEqcM75vp+s/asrKpAmdxM/Gbnfuz0j8OYnPw2v9AqZ5Nt+f7hikwkw2T3Fc2l2jzdcst3DpwGCnvQ+EPUEu8c/STSAqMfZPeX5IQK0J+a//zn5MP4Am7ISN/4mSV8AAAAASUVORK5CYII=)](https://apt.izzysoft.de/packages/de.felixnuesse.extract)
[![Documentation](https://img.shields.io/badge/Documentation-roundsync.com-4aad4e)](https://roundsync.com) [![supportive flags](https://img.shields.io/badge/support-🇺🇦_🏳️‍⚧_🏳️‍🌈-4aad4e)](https://roundsync.com)
[![Android Lint](https://github.com/newhinton/Round-Sync/actions/workflows/lint.yml/badge.svg)](https://github.com/newhinton/Round-Sync/actions/workflows/lint.yml)

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

[<img src="https://f-droid.org/badge/get-it-on.png"
    alt="Get it on F-Droid"
    height="80">](https://f-droid.org/packages/de.felixnuesse.extract)
[<img src="https://gitlab.com/IzzyOnDroid/repo/-/raw/master/assets/IzzyOnDroid.png"
    alt="Get it on IzzyOnDroid"
    height="80">](https://apt.izzysoft.de/packages/de.felixnuesse.extract)

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
