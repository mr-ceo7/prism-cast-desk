# Prism-Cast Performance Audit Report

This report provides a comprehensive analysis of the performance of the **Prism-Cast** application suite, including both the Android app and the desktop application. The audit identifies key bottlenecks and provides actionable recommendations to improve streaming quality, reduce latency, and minimize resource consumption.

---

### Executive Summary

The Prism-Cast suite consists of two independent streaming applications:
1.  **An Android App (`StreamService.kt`):** Captures the device screen and audio, streaming it via an embedded HTTP server.
2.  **A Desktop App (`PrismCastApp.java`):** Captures the desktop screen and streams it using an embedded HTTP server.

Both applications use an MJPEG-over-HTTP streaming method, which is universally compatible but has performance limitations. The primary bottlenecks across both platforms are **CPU-intensive frame processing** (capture, resize, and JPEG compression) and **inefficient, polling-based network streaming loops**. The Android app also streams **uncompressed audio**, leading to very high bandwidth usage.

By addressing these core issues, the application's performance can be significantly improved, leading to a smoother user experience with lower latency and resource usage.

---

### Detailed Analysis & Recommendations

#### I. Cross-Platform (Android & Desktop) Core Issues

The following issues are common to both the Android and desktop applications and should be prioritized.

**1. Inefficient MJPEG Streaming Loop**

*   **Issue:** Both `StreamService.kt` and `StreamServer.java` use a `while` loop with a fixed `Thread.sleep()` delay (20ms on Android, 33ms on desktop) to send frames to clients. This polling mechanism is a major source of inefficiency and latency. It wastes CPU cycles and results in a "jerky" stream because frames are sent at a fixed interval, not when they are actually captured.
*   **Recommendation (High Priority):**
    *   **Implement a Producer-Consumer Model:** Refactor the streaming logic to be event-driven.
        *   **Producer:** The thread that captures and compresses a frame.
        *   **Consumer(s):** The client connection threads.
    *   **On Desktop (`StreamServer.java`):** Use `Object.wait()` and `Object.notifyAll()`. The client threads should `wait()` on a shared lock object. After the `updateFrame()` method processes a new frame, it should call `notifyAll()` on that lock to wake up all client threads simultaneously to send the new frame.
    *   **On Android (`StreamService.kt`):** Use Kotlin Coroutines constructs. A `kotlinx.coroutines.flow.SharedFlow` is ideal. The image processing code would `emit()` new frames to the `SharedFlow`. Each client's streaming coroutine would `collect()` from this flow, receiving frames instantly without polling.

**2. CPU-Intensive Frame Processing on the Main Capture Thread**

*   **Issue:** In both apps, screen capture, image resizing (on desktop), and JPEG compression are performed sequentially for every frame. These are CPU-heavy operations that can easily bottleneck the entire pipeline, leading to dropped frames and high CPU usage.
*   **Recommendation (Medium Priority):**
    *   **Isolate JPEG Compression:** Move the JPEG compression into a separate thread or coroutine. This can be done using a thread pool. The capture thread would place the raw `Bitmap` or `BufferedImage` into a queue, and a worker thread would pull from the queue, compress it, and then make the resulting `byte[]` available to the streaming threads. This creates a multi-stage pipeline.
    *   **Hardware-Accelerated Encoding (Advanced):** For a significant performance leap, consider moving away from MJPEG to a modern video codec like **H.264**.
        *   On Android, `MediaCodec` provides access to hardware encoders.
        *   On Desktop, libraries like Xuggler or FFmpeg bindings could be used, though this adds complexity and native dependencies.
    *   This would reduce bandwidth dramatically and offload the encoding work to dedicated hardware, freeing up the CPU. The client-side would then need a corresponding H.264 decoder (which is standard in modern browsers).

---

#### II. Android-Specific Issues (`StreamService.kt`)

**1. Uncompressed Audio Streaming**

*   **Issue:** The app streams raw 16-bit PCM audio at 44.1kHz. This consumes **~705 kbps** of bandwidth, which is excessively high and will cause major issues on all but the fastest networks.
*   **Recommendation (High Priority):**
    *   **Implement Audio Compression:** Compress the audio stream before sending it. The **Opus** codec is the best choice for real-time streaming due to its low latency and excellent compression. AAC is another good option. This will reduce audio bandwidth by over 90%, making audio streaming viable over most connections. The web client can decode this using the Web Audio API.

**2. Custom HTTP Server**

*   **Issue:** The app uses a custom-built `SimpleHttpServer`. While functional, it's not as robust or efficient as battle-tested libraries and creates a new thread for every client.
*   **Recommendation (Medium Priority):**
    *   **Use a Production-Ready Library:** Replace the custom server with **Ktor** or **NanoHTTPD**. These libraries offer better performance, efficient thread management, and a more robust feature set, simplifying the network code significantly.

---

#### III. Desktop-Specific Issues (`PrismCastApp.java` & `StreamServer.java`)

**1. `Swing.Timer` for Screen Capture**

*   **Issue:** A `javax.swing.Timer` with a 33ms delay (~30 FPS) is used to trigger screen captures. `Swing.Timer` runs on the Event Dispatch Thread (EDT). `robot.createScreenCapture()` can be a slow operation, and performing it on the EDT can make the application's UI unresponsive.
*   **Recommendation (High Priority):**
    *   **Move Capture to a Separate Thread:** Do not use a `Swing.Timer`. Instead, create a dedicated `Thread` that runs a `while(streaming)` loop. Inside the loop, perform the `robot.createScreenCapture()`, `server.updateFrame()`, and then `Thread.sleep()` for the desired frame interval. This will completely decouple the capture logic from the UI thread, ensuring the UI remains responsive.

**2. Unbounded Thread Pool in `StreamServer`**

*   **Issue:** The server uses `Executors.newCachedThreadPool()`, which can create an unlimited number of threads. This poses a risk of resource exhaustion if many clients connect.
*   **Recommendation (Medium Priority):**
    *   **Use a Fixed-Size Thread Pool:** Change the executor to `Executors.newFixedThreadPool(N)`, where `N` is a reasonable number (e.g., 10 or 20). This provides back-pressure and prevents the server from being overwhelmed by too many viewers.

### Final Summary

By implementing the high-priority recommendations—specifically, **fixing the inefficient streaming loops**, **compressing audio on Android**, and **moving desktop screen capture off the UI thread**—the Prism-Cast application will see a dramatic improvement in performance, latency, and stability. The other recommendations will provide further polish and robustness.
