package com.example.service

import com.example.data.model.*
import com.example.data.repository.StreamRepository
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket
import java.util.*

class AmbientHttpServer(
    private val port: Int,
    private val localIpProvider: () -> String,
    private val repositoryProvider: () -> StreamRepository?,
    private val onJarvisQuery: (String) -> Unit,
    private val onUpdateMultimeter: (MultimeterTelemetry) -> Unit,
    private val onUpdateOscilloscope: (OscilloscopeTelemetry) -> Unit
) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false

    fun start() {
        if (isRunning) return
        isRunning = true
        Thread {
            try {
                serverSocket = ServerSocket(port)
                while (isRunning) {
                    val client = serverSocket?.accept() ?: break
                    Thread { handleClient(client) }.start()
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    fun stop() {
        isRunning = false
        try {
            serverSocket?.close()
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun handleClient(socket: Socket) {
        try {
            val reader = socket.getInputStream().bufferedReader()
            val out = socket.getOutputStream()

            val requestLine = reader.readLine() ?: return
            val tokenizer = StringTokenizer(requestLine)
            if (!tokenizer.hasMoreTokens()) return
            val method = tokenizer.nextToken()
            if (!tokenizer.hasMoreTokens()) return
            val pathAndQuery = tokenizer.nextToken()

            val headers = mutableMapOf<String, String>()
            var line: String? = reader.readLine()
            while (!line.isNullOrEmpty()) {
                val idx = line.indexOf(":")
                if (idx != -1) {
                    headers[line.substring(0, idx).trim().lowercase()] = line.substring(idx + 1).trim()
                }
                line = reader.readLine()
            }

            val path = pathAndQuery.split("?")[0]
            val contentLength = headers["content-length"]?.toIntOrNull() ?: 0
            val body = if (contentLength > 0) {
                val chars = CharArray(contentLength)
                var readTotal = 0
                while (readTotal < contentLength) {
                    val count = reader.read(chars, readTotal, contentLength - readTotal)
                    if (count == -1) break
                    readTotal += count
                }
                String(chars, 0, readTotal)
            } else ""

            if (method == "OPTIONS") {
                writeCorsOptionsResponse(out)
                return
            }

            // Web Remote Companion UI
            if (method == "GET" && (path == "/remote" || path == "/" || path == "/index.html")) {
                val html = WebRemoteHtml.getRemoteHtml(localIpProvider(), port)
                writeHtmlResponse(out, html)
                return
            }

            // Status API
            if (method == "GET" && path == "/api/status") {
                val freeMem = (Runtime.getRuntime().freeMemory() / (1024 * 1024))
                val isStreaming = StreamService.isStreaming.value
                val json = """{"status":"ONLINE","freeMemoryMb":$freeMem,"isStreaming":$isStreaming,"port":$port}"""
                writeJsonResponse(out, json)
                return
            }

            // --- To-Do Endpoints ---
            if (path == "/api/todos") {
                val repo = repositoryProvider()
                if (method == "GET") {
                    val todos = repo?.let { runBlocking { it.getAllTodos() } } ?: emptyList()
                    val array = JSONArray()
                    todos.forEach { t ->
                        array.put(JSONObject().apply {
                            put("id", t.id)
                            put("text", t.text)
                            put("isCompleted", t.isCompleted)
                            put("priority", t.priority)
                            put("createdAt", t.createdAt)
                        })
                    }
                    writeJsonResponse(out, array.toString())
                    return
                } else if (method == "POST") {
                    val obj = JSONObject(body)
                    val text = obj.optString("text", "")
                    val priority = obj.optString("priority", "NORMAL")
                    if (text.isNotEmpty() && repo != null) {
                        runBlocking { repo.addTodo(text, priority) }
                    }
                    writeJsonResponse(out, """{"success":true}""")
                    return
                }
            }

            if (path.startsWith("/api/todos/")) {
                val idStr = path.substringAfterLast("/")
                val todoId = idStr.toIntOrNull()
                val repo = repositoryProvider()
                if (todoId != null && repo != null) {
                    if (method == "PUT") {
                        val obj = JSONObject(body)
                        val isCompleted = obj.optBoolean("isCompleted", false)
                        runBlocking { repo.toggleTodo(todoId, isCompleted) }
                        writeJsonResponse(out, """{"success":true}""")
                        return
                    } else if (method == "DELETE") {
                        runBlocking { repo.deleteTodo(todoId) }
                        writeJsonResponse(out, """{"success":true}""")
                        return
                    }
                }
            }

            // --- Notepad Endpoints ---
            if (path == "/api/note") {
                val repo = repositoryProvider()
                if (method == "GET") {
                    val note = repo?.let { runBlocking { it.getNote() } } ?: NotepadNote()
                    val obj = JSONObject().apply {
                        put("id", note.id)
                        put("title", note.title)
                        put("content", note.content)
                        put("updatedAt", note.updatedAt)
                    }
                    writeJsonResponse(out, obj.toString())
                    return
                } else if (method == "POST") {
                    val obj = JSONObject(body)
                    val content = obj.optString("content", "")
                    val title = obj.optString("title", "Ambient Quick Notes")
                    if (repo != null) {
                        runBlocking { repo.saveNote(content, title) }
                    }
                    writeJsonResponse(out, """{"success":true}""")
                    return
                }
            }

            // --- KPI Cards Endpoints ---
            if (path == "/api/kpis" && method == "GET") {
                val repo = repositoryProvider()
                val kpis = repo?.let { runBlocking { it.getAllKpis() } } ?: emptyList()
                val array = JSONArray()
                kpis.forEach { k ->
                    array.put(JSONObject().apply {
                        put("id", k.id)
                        put("title", k.title)
                        put("value", k.value)
                        put("unit", k.unit)
                        put("change", k.change ?: "")
                        put("status", k.status)
                    })
                }
                writeJsonResponse(out, array.toString())
                return
            }

            if (path == "/api/kpi" && method == "POST") {
                val repo = repositoryProvider()
                val obj = JSONObject(body)
                val card = KpiCard(
                    id = obj.optString("id", "kpi_${System.currentTimeMillis()}"),
                    title = obj.optString("title", "Metric"),
                    value = obj.optString("value", "0"),
                    unit = obj.optString("unit", ""),
                    change = obj.optString("change", ""),
                    status = obj.optString("status", "NORMAL")
                )
                if (repo != null) {
                    runBlocking { repo.upsertKpi(card) }
                }
                writeJsonResponse(out, """{"success":true}""")
                return
            }

            // --- Telemetry Endpoints (Multimeter & Oscilloscope) ---
            if (method == "POST" && path == "/api/telemetry/multimeter") {
                val obj = JSONObject(body)
                val dmm = MultimeterTelemetry(
                    mode = obj.optString("mode", "DC_VOLTS"),
                    value = obj.optDouble("value", 3.30),
                    unit = obj.optString("unit", "V"),
                    barPercent = obj.optDouble("barPercent", 0.55).toFloat(),
                    isHold = obj.optBoolean("isHold", false),
                    rangeText = obj.optString("rangeText", "AUTO")
                )
                onUpdateMultimeter(dmm)
                writeJsonResponse(out, """{"success":true}""")
                return
            }

            if (method == "POST" && path == "/api/telemetry/oscilloscope") {
                val obj = JSONObject(body)
                val waveType = obj.optString("waveType", "SINE")
                val freq = obj.optDouble("frequency", 1000.0)
                val vpp = obj.optDouble("vpp", 3.3)
                val scope = OscilloscopeTelemetry(
                    waveType = waveType,
                    frequency = freq,
                    vpp = vpp
                )
                onUpdateOscilloscope(scope)
                writeJsonResponse(out, """{"success":true}""")
                return
            }

            // --- Jarvis AI Query Endpoint ---
            if (method == "POST" && path == "/api/jarvis/query") {
                val obj = JSONObject(body)
                val prompt = obj.optString("prompt", "")
                if (prompt.isNotEmpty()) {
                    onJarvisQuery(prompt)
                }
                writeJsonResponse(out, """{"status":"processing","prompt":"${JSONObject.quote(prompt)}"}""")
                return
            }

            // --- Screen Streaming (MJPEG fallback if active) ---
            if (method == "GET" && path == "/stream" && StreamService.isStreaming.value) {
                out.write("HTTP/1.1 200 OK\r\n".toByteArray())
                out.write("Content-Type: multipart/x-mixed-replace; boundary=--frame\r\n".toByteArray())
                out.write("Access-Control-Allow-Origin: *\r\n\r\n".toByteArray())
                out.flush()

                try {
                    while (StreamService.isStreaming.value) {
                        val frame = StreamService.latestFrameBytes
                        if (frame != null) {
                            out.write("--frame\r\n".toByteArray())
                            out.write("Content-Type: image/jpeg\r\n".toByteArray())
                            out.write("Content-Length: ${frame.size}\r\n\r\n".toByteArray())
                            out.write(frame)
                            out.write("\r\n".toByteArray())
                            out.flush()
                        }
                        Thread.sleep(33)
                    }
                } catch (e: Exception) {}
                return
            }

            // 404 Not Found
            write404(out)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                socket.close()
            } catch (e: Exception) {}
        }
    }

    private fun writeHtmlResponse(out: OutputStream, html: String) {
        val bytes = html.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: text/html; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun writeJsonResponse(out: OutputStream, json: String) {
        val bytes = json.toByteArray(Charsets.UTF_8)
        val header = "HTTP/1.1 200 OK\r\n" +
                "Content-Type: application/json; charset=UTF-8\r\n" +
                "Content-Length: ${bytes.size}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.write(bytes)
        out.flush()
    }

    private fun writeCorsOptionsResponse(out: OutputStream) {
        val header = "HTTP/1.1 204 No Content\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Access-Control-Allow-Methods: GET, POST, PUT, DELETE, OPTIONS\r\n" +
                "Access-Control-Allow-Headers: Content-Type, Authorization\r\n" +
                "Connection: close\r\n\r\n"
        out.write(header.toByteArray())
        out.flush()
    }

    private fun write404(out: OutputStream) {
        val body = "404 Not Found"
        val header = "HTTP/1.1 404 Not Found\r\n" +
                "Content-Type: text/plain\r\n" +
                "Content-Length: ${body.length}\r\n" +
                "Access-Control-Allow-Origin: *\r\n" +
                "Connection: close\r\n\r\n$body"
        out.write(header.toByteArray())
        out.flush()
    }
}
