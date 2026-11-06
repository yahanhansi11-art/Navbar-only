# Liquid Glass Navbar

A native Kotlin / Android Views sample and reusable floating navigation component. This repository originally contained no Android application, so the small `app` module provides a runnable test screen without replacing an existing app. It has no web content or third-party UI dependencies.

## Run in Android Studio

1. In GitHub Desktop, fetch and pull the merged changes into your local repository folder.
2. Open that folder in Android Studio (a recent version supporting Android Gradle Plugin 8.7). Allow Gradle sync to finish. Install Android SDK Platform 35 and JDK 17 via Android Studio if prompted; the Gradle wrapper downloads Gradle 8.9.
3. Select the `app` run configuration and a device or emulator running Android 6.0 (API 23) or newer, then press **Run**.
4. Tap Home, Search, Notifications, and Profile to switch the native sample content. The selected destination survives activity recreation.

## Integrate the component

Copy `LiquidGlassNavBar.kt` into your Android Views project and update its package name. Place `LiquidGlassNavBar` above your content in a `FrameLayout` (the sample uses a 78 dp height and 20 dp horizontal margins). Keep it clear of the system navigation inset. Set `onDestinationSelected` to navigate in your own application and call `select(destination, animate)` when navigation changes externally. The sample screens are placeholders, not search, notifications, or account backends.

For actual backdrop sampling, call `setBackdropView(contentView)` with a **separate sibling view behind the bar**, never a parent that contains the bar. Call `refreshBackdrop()` after the underlying content changes, scrolls, or moves. The component captures only the bar-sized region at quarter resolution; Android 12+ uses `RenderEffect` and API 23–30 uses a small CPU blur at capture time. On a continuously scrolling or animated backdrop, refreshes incur capture cost; throttle or omit them on low-end devices as appropriate. Without a backdrop view, the layered glass tint remains visible but there is no background blur. System surfaces, `SurfaceView`, video and hardware-only drawing may not appear in a software view snapshot. This is an approximation of refractive glass, not an OS-level live background blur.

The selection uses a finite damped spring animation; touch targets are separate accessible native views. There is no continuous animation. Test on real devices for appearance and performance, especially when sampling complex content.
