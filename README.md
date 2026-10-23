# DLNA Media Server for Android

A modern, high-performance Android DLNA / UPnP Media Server written in Kotlin using **Jetpack Compose**, **Hilt**, **Room**, and **FFmpeg**. This application enables you to stream local videos, music, and photos from your Android device to smart TVs, game consoles, media players, and other DLNA-compliant client devices across your local Wi-Fi network with built-in background media transcoding support.

---

## 🌟 Key Features

- **DLNA / UPnP Media Server (SSDP & HTTP)**
  - Fully compliant Simple Service Discovery Protocol (SSDP) engine supporting UPnP device discovery on port 1900.
  - Built-in HTTP ContentDirectory SOAP service generating DIDL-Lite XML metadata for seamless media browsing.
  - Supports HTTP Range requests for fast seeking and high-bitrate media streaming.

- **Hardware-Accelerated Background Transcoding**
  - Integrated with **FFmpeg Kit** for background batch transcoding of video, audio, and subtitle streams into compatible formats.
  - Auto-detection of device hardware video and audio encoders (`MediaCodec` / OMX hardware acceleration).
  - **Video Encoders**: x264 (`libx264` / `h264_mediacodec`), x265 (`libx265` / `hevc_mediacodec`), VP9 (`libvpx-vp9` / `vp9_mediacodec`), AV1 (`libsvtav1` / `av1_mediacodec`), or Keep original.
  - **Video Presets**: `ultrafast`, `medium`, and `slow` (with automatic SVT-AV1 preset mapping).
  - **Target Resolutions**: 4K (2160p), 1080p, 720p, or Keep original.
  - **Audio Codecs**: AAC (`aac` / `aac_mediacodec`), AC3 (`ac3`), FLAC (`flac`), or Keep original.
  - **Subtitle Encoders**: ASS (`ass`), SRT (`subrip`), or Keep original.
  - Configurable bitrate (Mbps), Constant Rate Factor (CRF), and maximum parallel job concurrency.
  - Asynchronous queue manager (`TranscodeQueueManager`) with progress tracking, wake lock, and background service execution.

- **Persistent Background Services**
  - Android Foreground Services (`DlnaService` and `TranscodeService`) targeting `FOREGROUND_SERVICE_DATA_SYNC` and `FOREGROUND_SERVICE_MEDIA_PROCESSING`.
  - Stays active in the background without being killed by system battery optimization during media streaming or background transcoding jobs.

- **Jetpack Compose + Material 3 UI**
  - **Dashboard Screen**: One-tap server control, live server IP/port indicator, active client monitoring, directory manager with native folder picker, and system permission cards.
  - **Settings Screen**: Port assignment, SSDP broadcast tuning, transcode profile setup (video codec, audio codec, subtitle format, resolution, bitrate, CRF, max parallel jobs), and logging preferences.
  - **Log Viewer**: Live streaming logs with disk log buffering, filtering, and daily log inspection.

- **Modern Architecture & Testing**
  - Clean Layered Architecture (UDF) with strict decoupling between `core/` modules and `feature/` presentation views.
  - Comprehensive unit test suite with JUnit 4, MockK and Coroutines Test.

---

## 🏗️ Architecture & Tech Stack

| Layer | Library / Tool                           | Description                                                                                   |
| :--- |:-----------------------------------------|:----------------------------------------------------------------------------------------------|
| **Language & SDK** | Kotlin 2.4+, Android SDK 37 (Min API 26) | Idiomatic Kotlin with KSP symbol processing targeting Java 25 compatibility                   |
| **UI Framework** | Jetpack Compose + Material 3             | Single-Activity architecture (`MainActivity`), Navigation Compose                             |
| **Dependency Injection** | Hilt (`dagger.hilt`)                     | Scoped dependency injection across Application, ViewModels, and Services                      |
| **Database & Storage** | Room 2.8.5 + DataStore Preferences       | SQLite database for directories & transcode jobs with schema tracking; DataStore for settings |
| **Media & Transcoding** | FFmpeg Kit (GPL min)                     | Low-level media parsing, codec query, and hardware-accelerated video transcoding              |
| **Networking** | Custom HTTP & Multicast Socket           | Asynchronous HTTP server and UDP multicast SSDP discovery engine                              |
| **Logging** | Timber + Disk Log Buffer                 | Custom `AppLogTree` streaming logs to Logcat and daily JSON files                             |

---

## 🚀 Getting Started

### Prerequisites

- Android Studio Ladybug (or newer)
- Java 11+
- Android SDK 37

### Building from Source

1. **Clone the Repository**:
   ```bash
   git clone https://github.com/felixbrucker/dlna-server-app.git
   cd dlna-server-app
   ```

2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
   The generated APK will be located at `app/build/outputs/apk/debug/app-debug.apk`.

3. **Run Unit Tests**:
   ```bash
   ./gradlew test
   ```

---

## ⚙️ Required Permissions

To function as a DLNA media server, the app requests the following system permissions:

- **`MANAGE_EXTERNAL_STORAGE` / `READ_EXTERNAL_STORAGE`**: Access local video, audio, and image files to build media catalogs.
- **`INTERNET` & `ACCESS_NETWORK_STATE` / `ACCESS_WIFI_STATE`**: Bind to local Wi-Fi interface and serve HTTP/UPnP requests.
- **`CHANGE_WIFI_MULTICAST_STATE`**: Receive UDP SSDP multicast discovery packets from DLNA clients.
- **`FOREGROUND_SERVICE` (`FOREGROUND_SERVICE_DATA_SYNC`, `FOREGROUND_SERVICE_MEDIA_PROCESSING`)**: Maintain active server and transcode operations in the background.
- **`WAKE_LOCK`**: Keep CPU active during active media transcoding jobs.

---

## 📄 License

This project is licensed under the terms specified in the [LICENSE](LICENSE) file.
