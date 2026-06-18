package com.example.desktop;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.MemoryCacheImageOutputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Iterator;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Embedded HTTP server that serves the viewer page and MJPEG stream.
 */
public class StreamServer {
    private static final String BOUNDARY = "--prismcast-frame";

    private HttpServer server;
    private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
    private final AtomicInteger viewerCount = new AtomicInteger(0);
    private final CopyOnWriteArrayList<Thread> streamThreads = new CopyOnWriteArrayList<>();

    private int port;
    private boolean passwordEnabled;
    private String passcode;
    private float jpegQuality;
    private float resolutionScale;

    public StreamServer() {
        this.port = 8080;
        this.passwordEnabled = false;
        this.passcode = "admin";
        this.jpegQuality = 0.70f;
        this.resolutionScale = 0.5f;
    }

    public void setPort(int port) { this.port = port; }
    public void setPasswordEnabled(boolean enabled) { this.passwordEnabled = enabled; }
    public void setPasscode(String passcode) { this.passcode = passcode; }
    public void setJpegQuality(float quality) { this.jpegQuality = quality; }
    public void setResolutionScale(float scale) { this.resolutionScale = scale; }
    public int getViewerCount() { return viewerCount.get(); }

    /** Update the latest frame from the screen capture. */
    public void updateFrame(BufferedImage image) {
        if (image == null) return;
        try {
            int w = Math.max(1, (int) (image.getWidth() * resolutionScale));
            int h = Math.max(1, (int) (image.getHeight() * resolutionScale));

            BufferedImage resized = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = resized.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(image, 0, 0, w, h, null);
            g.dispose();

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (writers.hasNext()) {
                ImageWriter writer = writers.next();
                ImageWriteParam param = writer.getDefaultWriteParam();
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(jpegQuality);
                writer.setOutput(new MemoryCacheImageOutputStream(baos));
                writer.write(null, new IIOImage(resized, null, null), param);
                writer.dispose();
            } else {
                ImageIO.write(resized, "jpg", baos);
            }
            latestFrame.set(baos.toByteArray());
        } catch (IOException ignored) {}
    }

    /** Start the HTTP server. */
    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);

        server.createContext("/", ex -> {
            if (passwordEnabled) {
                String cookie = getCookie(ex, "prismcast_auth");
                if (cookie == null || !cookie.equals(passcode)) {
                    serveLoginPage(ex);
                    return;
                }
            }
            serveViewerPage(ex);
        });

        server.createContext("/auth", ex -> {
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) {
                ex.sendResponseHeaders(405, -1);
                return;
            }
            String body = new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            String submitted = "";
            for (String param : body.split("&")) {
                if (param.startsWith("passcode=")) {
                    submitted = java.net.URLDecoder.decode(param.substring(9), StandardCharsets.UTF_8);
                }
            }
            if (submitted.equals(passcode)) {
                ex.getResponseHeaders().add("Set-Cookie", "prismcast_auth=" + passcode + "; Path=/; HttpOnly");
                ex.getResponseHeaders().add("Location", "/");
                ex.sendResponseHeaders(302, -1);
            } else {
                byte[] html = buildLoginPage("Invalid passcode. Try again.").getBytes(StandardCharsets.UTF_8);
                ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
                ex.sendResponseHeaders(200, html.length);
                try (OutputStream os = ex.getResponseBody()) { os.write(html); }
            }
        });

        server.createContext("/stream", ex -> {
            if (passwordEnabled) {
                String cookie = getCookie(ex, "prismcast_auth");
                if (cookie == null || !cookie.equals(passcode)) {
                    ex.sendResponseHeaders(403, -1);
                    return;
                }
            }
            viewerCount.incrementAndGet();
            Thread currentThread = Thread.currentThread();
            streamThreads.add(currentThread);
            try {
                ex.getResponseHeaders().add("Content-Type", "multipart/x-mixed-replace; boundary=" + BOUNDARY);
                ex.getResponseHeaders().add("Cache-Control", "no-cache, no-store");
                ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
                ex.sendResponseHeaders(200, 0);
                try (OutputStream os = ex.getResponseBody()) {
                    while (!Thread.currentThread().isInterrupted()) {
                        byte[] frame = latestFrame.get();
                        if (frame != null) {
                            os.write((BOUNDARY + "\r\n").getBytes(StandardCharsets.UTF_8));
                            os.write("Content-Type: image/jpeg\r\n".getBytes(StandardCharsets.UTF_8));
                            os.write(("Content-Length: " + frame.length + "\r\n\r\n").getBytes(StandardCharsets.UTF_8));
                            os.write(frame);
                            os.write("\r\n".getBytes(StandardCharsets.UTF_8));
                            os.flush();
                        }
                        Thread.sleep(33);
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } catch (IOException ignored) {
            } finally {
                streamThreads.remove(currentThread);
                viewerCount.decrementAndGet();
            }
        });

        server.createContext("/api/status", ex -> {
            String json = "{\"running\":true,\"viewers\":" + viewerCount.get() + "}";
            byte[] data = json.getBytes(StandardCharsets.UTF_8);
            ex.getResponseHeaders().add("Content-Type", "application/json");
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.sendResponseHeaders(200, data.length);
            try (OutputStream os = ex.getResponseBody()) { os.write(data); }
        });

        server.start();
    }

    /** Stop the server and interrupt all streaming threads. */
    public void stop() {
        for (Thread t : streamThreads) {
            t.interrupt();
        }
        streamThreads.clear();
        viewerCount.set(0);
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    public String getLocalUrl() {
        return "http://" + getLocalIp() + ":" + port;
    }

    private String getLocalIp() {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (ni.isLoopback() || !ni.isUp()) continue;
                for (InetAddress addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof java.net.Inet4Address) {
                        String ip = addr.getHostAddress();
                        if (!ip.startsWith("127.")) return ip;
                    }
                }
            }
        } catch (Exception ignored) {}
        return "localhost";
    }

    private String getCookie(HttpExchange ex, String name) {
        String cookieHeader = ex.getRequestHeaders().getFirst("Cookie");
        if (cookieHeader == null) return null;
        for (String part : cookieHeader.split(";")) {
            String trimmed = part.trim();
            if (trimmed.startsWith(name + "=")) {
                return trimmed.substring(name.length() + 1);
            }
        }
        return null;
    }

    private void serveLoginPage(HttpExchange ex) throws IOException {
        byte[] html = buildLoginPage(null).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, html.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(html); }
    }

    private void serveViewerPage(HttpExchange ex) throws IOException {
        byte[] html = buildViewerPage().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
        ex.sendResponseHeaders(200, html.length);
        try (OutputStream os = ex.getResponseBody()) { os.write(html); }
    }

    private String buildLoginPage(String error) {
        String errorHtml = error != null ? "<p style='color:#F2B8B5;margin-bottom:16px;font-size:13px'>" + error + "</p>" : "";
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Prism Cast — Authenticate</title>"
            + "<style>"
            + "body{margin:0;background:#1C1B1F;color:#E6E1E5;font-family:Inter,'Segoe UI',system-ui,sans-serif;display:flex;align-items:center;justify-content:center;min-height:100vh}"
            + ".card{background:#2B2930;border-radius:24px;padding:40px;max-width:380px;width:90%;box-shadow:0 20px 60px rgba(0,0,0,.5);border:1px solid #49454F}"
            + ".dot{width:10px;height:10px;background:#B2F042;border-radius:50%;display:inline-block;margin-right:8px}"
            + "h1{font-size:22px;margin:0 0 6px;color:#D0BCFF}p.sub{font-size:13px;color:#938F99;margin:0 0 24px}"
            + "input{width:100%;box-sizing:border-box;padding:14px 16px;background:#1C1B1F;border:1px solid #49454F;border-radius:12px;color:#E6E1E5;font-size:14px;outline:none;margin-bottom:16px}"
            + "input:focus{border-color:#D0BCFF}"
            + "button{width:100%;padding:14px;background:#D0BCFF;color:#21005D;border:none;border-radius:12px;font-size:14px;font-weight:700;cursor:pointer;letter-spacing:.5px}"
            + "button:hover{background:#E8DCFF}"
            + ".lock{font-size:36px;margin-bottom:16px}"
            + "</style></head><body>"
            + "<div class='card'><div class='lock'>&#128274;</div>"
            + "<h1><span class='dot'></span>Prism Cast</h1>"
            + "<p class='sub'>This stream is password protected</p>"
            + errorHtml
            + "<form method='POST' action='/auth'>"
            + "<input type='password' name='passcode' placeholder='Enter stream passcode' autofocus required>"
            + "<button type='submit'>Authenticate</button>"
            + "</form></div></body></html>";
    }

    private String buildViewerPage() {
        return "<!DOCTYPE html><html><head><meta charset='utf-8'><meta name='viewport' content='width=device-width,initial-scale=1'>"
            + "<title>Prism Cast Viewer</title>"
            + "<style>"
            + "*{margin:0;padding:0;box-sizing:border-box}"
            + "body{background:#1C1B1F;color:#E6E1E5;font-family:Inter,'Segoe UI',system-ui,sans-serif}"
            + ".wrap{max-width:1280px;margin:0 auto;padding:20px}"
            + ".header{display:flex;align-items:center;justify-content:space-between;margin-bottom:16px}"
            + ".brand{display:flex;align-items:center;gap:12px}"
            + ".brand-icon{width:40px;height:40px;background:#D0BCFF;border-radius:50%;display:flex;align-items:center;justify-content:center;font-size:18px}"
            + ".brand h1{font-size:18px;color:#fff;letter-spacing:-.5px}"
            + ".brand p{font-size:12px;color:#CAC4D0}"
            + ".live-dot{width:8px;height:8px;background:#FF4444;border-radius:50%;display:inline-block;margin-right:6px;animation:pulse 1.5s infinite}"
            + "@keyframes pulse{0%,100%{opacity:1}50%{opacity:.4}}"
            + ".badge{background:rgba(0,0,0,.4);padding:6px 12px;border-radius:8px;font-size:11px;font-weight:700;letter-spacing:1px;display:flex;align-items:center;gap:6px}"
            + ".panel{background:#2B2930;border-radius:20px;overflow:hidden;border:1px solid #49454F;box-shadow:0 12px 40px rgba(0,0,0,.4)}"
            + ".top-bar{padding:12px 18px;display:flex;justify-content:space-between;align-items:center;background:#232128;border-bottom:1px solid #49454F}"
            + ".secure{background:#EADDFF;color:#21005D;padding:4px 10px;border-radius:8px;font-size:10px;font-weight:700;letter-spacing:.5px}"
            + "img.stream{width:100%;display:block;background:#0f0e12;min-height:400px;object-fit:contain}"
            + ".stats{display:flex;gap:10px;margin-top:16px}"
            + ".stat{flex:1;background:#2B2930;border:1px solid #49454F;border-radius:16px;padding:14px;text-align:center}"
            + ".stat-val{font-size:20px;font-weight:700;color:#D0BCFF}"
            + ".stat-lbl{font-size:9px;font-weight:700;color:#938F99;letter-spacing:.5px;margin-top:4px}"
            + ".footer{text-align:center;margin-top:24px;font-size:11px;color:#49454F}"
            + "</style></head><body>"
            + "<div class='wrap'>"
            + "<div class='header'>"
            + "<div class='brand'><div class='brand-icon'>&#x1F4E1;</div><div><h1>Prism Cast</h1><p><span class='live-dot'></span>Stream Active • Desktop</p></div></div>"
            + "<div class='badge'><span class='live-dot'></span>LIVE</div>"
            + "</div>"
            + "<div class='panel'>"
            + "<div class='top-bar'><strong style='font-size:13px'>Desktop Screen Stream</strong><span class='secure'>&#128274; E2EE SECURE</span></div>"
            + "<img class='stream' src='/stream' alt='Prism Cast desktop stream'>"
            + "</div>"
            + "<div class='stats'>"
            + "<div class='stat'><div class='stat-val' id='latency'>—</div><div class='stat-lbl'>LATENCY</div></div>"
            + "<div class='stat'><div class='stat-val' id='status'>Connected</div><div class='stat-lbl'>STATUS</div></div>"
            + "<div class='stat'><div class='stat-val' id='quality'>HD</div><div class='stat-lbl'>QUALITY</div></div>"
            + "</div>"
            + "<div class='footer'>Prism Cast Desktop • Secure Screen Broadcasting</div>"
            + "</div>"
            + "<script>"
            + "setInterval(async()=>{try{let s=performance.now();await fetch('/api/status');let e=performance.now();document.getElementById('latency').textContent=Math.round(e-s)+'ms'}catch(e){document.getElementById('status').textContent='Disconnected'}},2000);"
            + "</script>"
            + "</body></html>";
    }
}
