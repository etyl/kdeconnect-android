# TV Remote

A remote control screen for a computer plugged to a TV, with its own **TV Remote** launcher icon
next to the regular KDE Connect one. It has two tabs:

- **Multimedia**
  - media controls (play/pause, ±10 s, previous/next, seek bar, player choice),
  - computer volume, also on the phone's volume keys while the remote is open,
  - back / home / full screen ("f", the video players key) / close window (Alt+F4) keys,
  - custom buttons: open a web page, or run a command defined on the computer.
- **Touchpad**
  - a touchpad (tap = click, two fingers = scroll, two-finger tap = right click) and click buttons,
  - Copy / Paste buttons (Ctrl+C / Ctrl+V, so in a terminal Copy interrupts the running program),
  - a typing field that types live on the computer, with backspace and enter.

It only uses the existing plugins (Remote input, Multimedia controls, System volume, Share, Run
Command), so those must be enabled for the computer in KDE Connect.

## Keeping it separate from upstream

Everything lives in `src/tvremote/`. The only upstream files touched are:

- `build.gradle.kts`: 3 lines adding `src/tvremote/java` and `src/tvremote/res` to the main source set,
- `src/main/AndroidManifest.xml`: the `TvRemoteActivity` declaration, at the end of `<application>`.

To update from upstream:

```sh
git remote add upstream https://invent.kde.org/network/kdeconnect-android.git  # once
git fetch upstream
git merge upstream/master
```

## Custom buttons

Long press a button (or use **Edit buttons**) to change it. Two kinds of action:

- **Web page**: opens the address in a *new tab* of the computer's default browser.
- **PC command**: runs a command added on the computer in KDE Connect → this phone → *Run commands*,
  matched by name, ignoring case and spaces ("Shut down" finds "Shutdown"). If the computer has no command with that name, the optional
  fallback address is opened instead, so the default Netflix and Jellyfin buttons work out of the box
  and switch to the "reuse the open window" behaviour once you add the commands below.

### Reuse an already open Netflix / Jellyfin window

Browsers can't be asked to switch to an existing tab from the command line, but the window title is
the title of its active tab. So the script below looks for a window whose title contains "Netflix"
(or "Jellyfin"), focuses it if found, and otherwise opens the page in a **new window**, so that the
service stays the active tab of its own window and is found again next time.

#### Linux

Save as `~/.local/bin/tv-open` and `chmod +x` it:

```sh
#!/bin/sh
# Usage: tv-open <window title part> <url>
title="$1"
url="$2"
if command -v kdotool >/dev/null 2>&1; then
    # KDE Plasma, works on Wayland too
    id=$(kdotool search --name "$title" | head -n 1)
    [ -n "$id" ] && exec kdotool windowactivate "$id"
elif command -v wmctrl >/dev/null 2>&1; then
    # X11 desktops
    wmctrl -a "$title" && exit 0
fi
if command -v firefox >/dev/null 2>&1; then
    exec firefox --new-window "$url"
fi
# No Firefox: default browser, in a new tab
exec xdg-open "$url"
```

Focusing an existing window needs `kdotool` (KDE Plasma, X11 or Wayland) or `wmctrl` (X11 only).
On GNOME Wayland neither works, so the script always opens a new window there.

Commands to add in KDE Connect on the computer:

| Name      | Command                                                        |
|-----------|----------------------------------------------------------------|
| Netflix   | `~/.local/bin/tv-open Netflix https://www.netflix.com/browse`  |
| Jellyfin  | `~/.local/bin/tv-open Jellyfin http://localhost:8096`          |
| Shut down | `systemctl poweroff` (or `shutdown now`, same thing on systemd) |

#### Windows

Save as `C:\tv\tv-open.ps1`:

```powershell
param([string]$Title, [string]$Url)
# AppActivate matches a window title starting or ending with $Title, e.g. "Netflix — Mozilla Firefox"
$shell = New-Object -ComObject WScript.Shell
if ($shell.AppActivate($Title)) { exit }
try {
    Start-Process firefox "-new-window $Url" -ErrorAction Stop
} catch {
    # Firefox not found: default browser, in a new tab
    Start-Process $Url
}
```

Windows' focus-stealing protection sometimes only flashes the window in the taskbar instead of
bringing it to the front, and a minimized window can stay minimized.

| Name      | Command                                                                                                          |
|-----------|------------------------------------------------------------------------------------------------------------------|
| Netflix   | `powershell -NoProfile -ExecutionPolicy Bypass -File C:\tv\tv-open.ps1 Netflix https://www.netflix.com/browse`   |
| Jellyfin  | `powershell -NoProfile -ExecutionPolicy Bypass -File C:\tv\tv-open.ps1 Jellyfin http://localhost:8096`           |
| Shut down | `shutdown /s /t 0`                                                                                               |

Replace `http://localhost:8096` with your Jellyfin server's address if it runs on another machine.

## Testing without a computer

Debug builds enable the loopback backend (see `BackgroundService.registerLinkProviders`): the phone
lists itself as a device, pair with it and the remote shows all its sections. Keys and media
commands are sent back to the phone itself, so they don't do anything visible.
