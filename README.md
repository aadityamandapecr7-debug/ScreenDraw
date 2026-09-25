# ScreenDraw

A tiny, free, floating draw-over-any-app tool for Android tablets.

What it does:
- Installs as a normal app.
- On first open, asks for "display over other apps" permission.
- Once granted, a small floating circle (bubble) appears on screen, on top of
  everything — YouTube, browser, PDF viewer, anything.
- Tap the bubble to open the toolbar. Tools, left to right:
  - **✏ Pen** — freehand drawing with stylus or finger.
  - **⬠ Shapes** — opens a sub-row: line, rectangle, triangle, circle,
    octagon, and a 3D wireframe cube. Drag on screen to size/place the shape.
  - **⌫ Eraser** — tap once to toggle between two modes (icon changes to
    show which is active): "⌫·" erases pixels wherever you drag, like a
    real eraser; "⌫◆" removes a whole object in one tap.
  - **🔴 Laser pointer** — draws a glowing dot that follows your finger/stylus
    and fades away on its own after about half a second. Leaves nothing
    behind — for pointing things out while explaining, not permanent ink.
  - **⬚ Select** — tap any stroke or shape to select it (dashed outline
    appears), drag to move it, or tap a color/size to change just that
    object.
  - **↶ / ↷** Undo / redo, **🗑** clear everything.
  - **⭘ Size** — opens a slider controlling pen/shape/eraser thickness.
  - **🎨 Colors** — opens 7 quick presets plus a full spectrum picker
    (drag the square for shade, the strip below it for hue) — any color,
    not just presets.
  - **✕** collapses back to just the bubble.
- Draw with your stylus (or finger) directly on top of whatever app is open.
- Drag the bubble anywhere on screen.

No account, no ads, no in-app purchase, no internet permission at all —
it's a few hundred KB installed.

## Option 1: Build it entirely on your tablet (no PC, no big download)

This uses GitHub's free servers to compile the app for you. Your tablet
only ever downloads the finished APK at the end — a few MB, not gigabytes.

1. On your tablet's browser, go to **github.com** and create a free account
   (if you don't have one).
2. Tap **+** (top right) → **New repository**. Name it `ScreenDraw` → Create.
3. On the new repo page, tap **Add file → Upload files**.
4. From your tablet's file manager, select every file and folder from this
   unzipped `ScreenDraw` folder (including the hidden `.github` folder —
   your file manager may need "show hidden files" turned on) and upload them,
   keeping the same folder structure. If your browser's file picker won't
   let you select folders, upload file-by-file into matching folder paths
   using GitHub's "Add file" option, or ask a friend with a PC to do this
   one-time upload for you (2 minutes) — after that, everything else here
   happens on your tablet.
5. Commit the upload ("Commit changes" button).
6. Tap the **Actions** tab at the top of the repo. You'll see a build
   running automatically (it takes 3–5 minutes) — it's compiling the app
   on GitHub's computer, not yours.
7. Once it shows a green checkmark, tap into that finished run, scroll to
   **Artifacts**, and tap **ScreenDraw-apk** to download it. That's the
   real installable app file — only a few MB.
8. Open your tablet's Downloads, tap the downloaded file (rename it to end
   in `.apk` if it doesn't already), allow "install unknown apps" for
   whichever app opened it, then tap **Install**.

From then on, ScreenDraw is a normal app icon on your tablet.

## Option 2: Build it with Android Studio on a PC

You need a computer (Windows/Mac/Linux) with **Android Studio** — free,
one-time download: https://developer.android.com/studio

1. Install Android Studio, open it.
2. Choose "Open" and select this `ScreenDraw` folder (the one containing
   `settings.gradle`).
3. Let it sync (first time takes a few minutes — it downloads Gradle and
   the Android SDK bits automatically).
4. Plug your tablet into the computer via USB, and on the tablet enable
   **Developer options → USB debugging** (Settings → About tablet → tap
   "Build number" 7 times to unlock Developer options).
5. In Android Studio, pick your tablet from the device dropdown at the top,
   then click the green ▶ Run button.
6. The app installs and opens on your tablet automatically. From then on
   it's a normal app icon you can tap any time — no cable needed.

Alternative to USB: Build → Build Bundle(s) / APK(s) → Build APK(s), then
copy the resulting `.apk` file from `app/build/outputs/apk/debug/` onto
your tablet (Google Drive, USB drive, email to yourself) and tap it to
install (you'll need to allow "install unknown apps" for whichever app you
use to open it).

## If you want to change the colors or tools

- Colors: edit the `colors` list near the top of `OverlayService.kt`.
- Tools: `toolButton(...)` calls in `addToolbar()` in the same file —
  add/remove lines there.
- Pen thickness: `drawView.setStrokeWidth(...)`, currently defaulted in
  `DrawView.kt`.
