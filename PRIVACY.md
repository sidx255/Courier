# Courier Privacy Policy

_Last updated: 2026-07-18_

Courier is an open-source file transfer and backup client for Android, built on
[rclone](https://rclone.org). It moves **your** files between your device and the
storage **you** configure (for example, your own NAS over SMB, or any cloud remote
supported by rclone). This policy explains exactly what data Courier handles.

## The short version

- Courier has **no backend server**. The developer does not operate any service that
  receives your data.
- Courier does **not collect, transmit, sell, or share** any personal data with the
  developer or any third party.
- There is **no analytics, advertising, tracking, or telemetry** of any kind.
- Everything Courier stores stays **on your device** or on the **remote storage you
  choose**. Your files travel only between your device and the destinations you set up.

## What Courier stores on your device

All of the following is kept in Courier's private application storage on your device:

- **Remote configuration** (`rclone.conf`): the definitions of the storage remotes you
  add, including **credentials** you provide — for example SMB usernames and passwords,
  or OAuth tokens for cloud providers.
- **Backup setup**: your tasks, triggers, filters, and guided-backup configuration
  (such as which folders back up to which destination and on what schedule).
- **App preferences**: your settings.
- **Local logs and optional bug reports**: diagnostic information kept on-device to help
  you troubleshoot. Bug reports are generated locally and are only ever shared if **you**
  choose to export and send them.

Courier does not upload any of this to the developer.

## Network connections

Courier connects **only** to the remote endpoints you configure (your NAS, your cloud
accounts, or servers you enable such as HTTP/FTP/WebDAV/DLNA). It does not contact any
server operated by the developer. File transfers occur directly between your device and
those destinations, using the protocols and credentials you set up.

## Permissions Courier requests, and why

- **Storage / All files access** — to read the files you choose to back up or serve, and
  to write files you restore or download.
- **Photos and media** — to back up or transfer media you select.
- **Network / Wi‑Fi state** — to reach the remotes you configure and to detect your home
  network for scheduled backups.
- **Notifications** — to show backup and transfer progress and results.
- **Foreground service** — to keep transfers running reliably while in progress.
- **Run at boot, exact alarms, and battery-optimization exemption** — to run your
  scheduled backups reliably at the times you set.

Permissions are used only for the purposes above.

## Data sharing and selling

Courier does **not** share or sell any data. No data is disclosed to the developer or to
third parties. The only parties that receive your files are the remote storage providers
or servers **you** explicitly configure, governed by their own terms and privacy policies.

## Children

Courier is a general-purpose utility and is not directed at children.

## Data retention and deletion

- Data stored on your device is removed when you uninstall Courier or clear its app data.
- Data on your remote storage is under your control; manage or delete it through your
  storage provider or by using Courier.

## Open source

Courier is free software licensed under the GNU General Public License v3. The complete
source code is available at https://github.com/sidx255/Courier. Courier is a fork of
[Round Sync](https://github.com/newhinton/Round-Sync), which builds on RCX and rclone;
upstream authors and license notices remain credited in the project.

## Contact

For privacy questions or to report a concern, open an issue at
https://github.com/sidx255/Courier/issues, or for security-sensitive reports use
https://github.com/sidx255/Courier/security/advisories/new.
