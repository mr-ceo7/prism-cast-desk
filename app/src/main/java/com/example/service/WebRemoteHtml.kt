package com.example.service

object WebRemoteHtml {

    fun getRemoteHtml(projectorIp: String, port: Int): String {
        return """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no">
    <title>Prism Cast • Wall HUD Remote</title>
    <style>
        :root {
            --bg-dark: #070b14;
            --card-bg: #0f172a;
            --border-color: #1e293b;
            --cyan-accent: #00f0ff;
            --emerald-accent: #10b981;
            --amber-accent: #f59e0b;
            --purple-accent: #a855f7;
            --pink-accent: #ec4899;
            --text-main: #f8fafc;
            --text-muted: #94a3b8;
        }

        * {
            box-sizing: border-box;
            margin: 0;
            padding: 0;
            font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
            -webkit-tap-highlight-color: transparent;
        }

        body {
            background-color: var(--bg-dark);
            color: var(--text-main);
            min-height: 100vh;
            padding-bottom: 70px;
        }

        header {
            background: linear-gradient(180deg, #0f172a 0%, #070b14 100%);
            border-bottom: 1px solid var(--border-color);
            padding: 16px;
            display: flex;
            align-items: center;
            justify-content: space-between;
            position: sticky;
            top: 0;
            z-index: 100;
        }

        .brand {
            display: flex;
            align-items: center;
            gap: 10px;
        }

        .brand-dot {
            width: 10px;
            height: 10px;
            border-radius: 50%;
            background: var(--cyan-accent);
            box-shadow: 0 0 10px var(--cyan-accent);
        }

        .brand h1 {
            font-size: 17px;
            font-weight: 700;
            letter-spacing: 0.5px;
        }

        .pill-badge {
            background: rgba(0, 240, 255, 0.1);
            border: 1px solid rgba(0, 240, 255, 0.3);
            color: var(--cyan-accent);
            font-size: 11px;
            font-weight: 600;
            padding: 4px 10px;
            border-radius: 20px;
        }

        /* Bottom Tab Navigation */
        .bottom-nav {
            position: fixed;
            bottom: 0;
            left: 0;
            right: 0;
            height: 60px;
            background: #0f172a;
            border-top: 1px solid var(--border-color);
            display: flex;
            justify-content: space-around;
            align-items: center;
            z-index: 100;
        }

        .nav-item {
            display: flex;
            flex-direction: column;
            align-items: center;
            gap: 4px;
            color: var(--text-muted);
            font-size: 11px;
            font-weight: 500;
            background: none;
            border: none;
            cursor: pointer;
            padding: 8px;
            flex: 1;
        }

        .nav-item.active {
            color: var(--cyan-accent);
        }

        .nav-item svg {
            width: 20px;
            height: 20px;
            fill: currentColor;
        }

        /* Tab Content */
        .tab-panel {
            display: none;
            padding: 16px;
        }

        .tab-panel.active {
            display: block;
        }

        /* Common Card Style */
        .card {
            background: var(--card-bg);
            border: 1px solid var(--border-color);
            border-radius: 14px;
            padding: 16px;
            margin-bottom: 16px;
        }

        .card-title {
            font-size: 14px;
            font-weight: 600;
            color: var(--text-muted);
            text-transform: uppercase;
            letter-spacing: 1px;
            margin-bottom: 12px;
            display: flex;
            align-items: center;
            justify-content: space-between;
        }

        /* Jarvis AI Terminal Tab */
        .jarvis-input-container {
            display: flex;
            gap: 10px;
            margin-bottom: 12px;
        }

        .chat-input {
            flex: 1;
            background: #070b14;
            border: 1px solid var(--border-color);
            color: #fff;
            padding: 12px 14px;
            border-radius: 10px;
            font-size: 15px;
            outline: none;
        }

        .chat-input:focus {
            border-color: var(--cyan-accent);
        }

        .btn {
            background: linear-gradient(135deg, #00f0ff 0%, #0284c7 100%);
            color: #000;
            font-weight: 700;
            border: none;
            border-radius: 10px;
            padding: 12px 16px;
            cursor: pointer;
            font-size: 14px;
            display: flex;
            align-items: center;
            justify-content: center;
            gap: 6px;
        }

        .btn-mic {
            background: #1e293b;
            color: var(--cyan-accent);
            border: 1px solid var(--border-color);
            border-radius: 10px;
            width: 48px;
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .btn-mic.recording {
            background: #ef4444;
            color: #fff;
            animation: pulse 1s infinite alternate;
        }

        @keyframes pulse {
            0% { transform: scale(1); }
            100% { transform: scale(1.08); }
        }

        .prompt-chips {
            display: flex;
            flex-wrap: wrap;
            gap: 8px;
            margin-top: 10px;
        }

        .chip {
            background: rgba(255, 255, 255, 0.05);
            border: 1px solid var(--border-color);
            color: var(--text-main);
            padding: 6px 12px;
            border-radius: 20px;
            font-size: 12px;
            cursor: pointer;
        }

        .chip:hover {
            border-color: var(--cyan-accent);
            color: var(--cyan-accent);
        }

        .status-box {
            background: #020617;
            border: 1px solid var(--border-color);
            border-radius: 10px;
            padding: 12px;
            font-size: 13px;
            color: var(--text-muted);
            min-height: 48px;
            display: flex;
            align-items: center;
        }

        /* To-Do List Tab */
        .todo-input-row {
            display: flex;
            gap: 8px;
            margin-bottom: 14px;
        }

        .todo-item {
            display: flex;
            align-items: center;
            justify-content: space-between;
            padding: 12px;
            background: #070b14;
            border: 1px solid var(--border-color);
            border-radius: 10px;
            margin-bottom: 8px;
        }

        .todo-left {
            display: flex;
            align-items: center;
            gap: 10px;
            flex: 1;
        }

        .todo-checkbox {
            width: 20px;
            height: 20px;
            border-radius: 6px;
            border: 2px solid var(--border-color);
            cursor: pointer;
            display: flex;
            align-items: center;
            justify-content: center;
        }

        .todo-checkbox.checked {
            background: var(--emerald-accent);
            border-color: var(--emerald-accent);
        }

        .todo-text {
            font-size: 14px;
            color: #fff;
        }

        .todo-text.completed {
            text-decoration: line-through;
            color: var(--text-muted);
        }

        .btn-delete {
            background: none;
            border: none;
            color: #ef4444;
            cursor: pointer;
            padding: 6px;
        }

        /* Notepad Tab */
        .notepad-textarea {
            width: 100%;
            height: 220px;
            background: #070b14;
            border: 1px solid var(--border-color);
            color: #fff;
            padding: 14px;
            border-radius: 10px;
            font-size: 14px;
            line-height: 1.5;
            resize: vertical;
            outline: none;
        }

        .notepad-textarea:focus {
            border-color: var(--purple-accent);
        }

        /* Telemetry Tab */
        .control-group {
            margin-bottom: 16px;
        }

        .control-label {
            font-size: 13px;
            color: var(--text-muted);
            margin-bottom: 6px;
            display: flex;
            justify-content: space-between;
        }

        .slider {
            width: 100%;
            accent-color: var(--amber-accent);
        }

        .btn-row {
            display: flex;
            gap: 8px;
            flex-wrap: wrap;
        }

        .mode-btn {
            flex: 1;
            background: #070b14;
            border: 1px solid var(--border-color);
            color: var(--text-main);
            padding: 10px;
            border-radius: 8px;
            font-size: 12px;
            font-weight: 600;
            cursor: pointer;
        }

        .mode-btn.active {
            border-color: var(--amber-accent);
            color: var(--amber-accent);
            background: rgba(245, 158, 11, 0.1);
        }

        .toast {
            position: fixed;
            bottom: 75px;
            left: 50%;
            transform: translateX(-50%);
            background: #0f172a;
            border: 1px solid var(--cyan-accent);
            color: #fff;
            padding: 10px 20px;
            border-radius: 20px;
            font-size: 13px;
            box-shadow: 0 4px 12px rgba(0,0,0,0.5);
            display: none;
            z-index: 200;
        }
    </style>
</head>
<body>

    <header>
        <div class="brand">
            <div class="brand-dot"></div>
            <h1>PRISM CAST HUD</h1>
        </div>
        <div class="pill-badge" id="hud-status">PROJECTOR CONNECTED</div>
    </header>

    <!-- Tab 1: Jarvis AI Assistant -->
    <div id="tab-jarvis" class="tab-panel active">
        <div class="card">
            <div class="card-title">
                <span>Jarvis Wall AI</span>
                <span style="color: var(--cyan-accent);">LIVE</span>
            </div>
            
            <div class="jarvis-input-container">
                <input type="text" id="jarvis-prompt" class="chat-input" placeholder="Ask Jarvis (e.g. how far is TRM from town)..." />
                <button id="mic-btn" class="btn-mic" onclick="toggleVoiceInput()" title="Voice Input">
                    <svg width="20" height="20" viewBox="0 0 24 24" fill="currentColor">
                        <path d="M12 14c1.66 0 3-1.34 3-3V5c0-1.66-1.34-3-3-3S9 3.34 9 5v6c0 1.66 1.34 3 3 3z"/><path d="M17 11c0 2.76-2.24 5-5 5s-5-2.24-5-5H5c0 3.53 2.61 6.43 6 6.92V21h2v-3.08c3.39-.49 6-3.39 6-6.92h-2z"/>
                    </svg>
                </button>
                <button class="btn" onclick="sendJarvisPrompt()">Send</button>
            </div>

            <div class="prompt-chips">
                <div class="chip" onclick="quickPrompt('How far is TRM from town')">🗺️ TRM from Town</div>
                <div class="chip" onclick="quickPrompt('Wireless Multimeter voltage probe test')">⚡ Multimeter Test</div>
                <div class="chip" onclick="quickPrompt('ESP32 sensor telemetry circuit schematic')">🔧 Circuit Diagram</div>
                <div class="chip" onclick="quickPrompt('Projector ambient performance KPIs')">📊 System Metrics</div>
            </div>
        </div>

        <div class="card">
            <div class="card-title">HUD Response Caption</div>
            <div id="jarvis-status" class="status-box">Jarvis is ready. Type or speak a prompt to project interactive visuals on the wall.</div>
        </div>
    </div>

    <!-- Tab 2: Synced To-Do List -->
    <div id="tab-todo" class="tab-panel">
        <div class="card">
            <div class="card-title">
                <span>Wall Synced Tasks</span>
                <span id="todo-count">0 items</span>
            </div>

            <div class="todo-input-row">
                <input type="text" id="todo-input" class="chat-input" placeholder="Add task to projector wall..." onkeydown="if(event.key==='Enter') addTodo()" />
                <button class="btn" onclick="addTodo()">Add</button>
            </div>

            <div id="todo-list">
                <!-- Loaded dynamically -->
            </div>
        </div>
    </div>

    <!-- Tab 3: Ambient Notepad -->
    <div id="tab-notes" class="tab-panel">
        <div class="card">
            <div class="card-title">
                <span>Wall Ambient Notepad</span>
                <button class="btn" style="padding: 6px 12px; font-size: 12px;" onclick="saveNote()">Sync to Wall</button>
            </div>

            <textarea id="notepad-content" class="notepad-textarea" placeholder="Type memo notes here to sync directly to the projector wall..."></textarea>
        </div>
    </div>

    <!-- Tab 4: Sensors & Telemetry (Multimeter / Oscilloscope) -->
    <div id="tab-sensors" class="tab-panel">
        <div class="card">
            <div class="card-title">Wireless Multimeter Feed</div>
            <div class="control-group">
                <div class="control-label">Measurement Mode</div>
                <div class="btn-row">
                    <button class="mode-btn active" onclick="setDmmMode('DC_VOLTS')">DC Volts</button>
                    <button class="mode-btn" onclick="setDmmMode('AC_VOLTS')">AC Volts</button>
                    <button class="mode-btn" onclick="setDmmMode('RESISTANCE')">Resistance</button>
                    <button class="mode-btn" onclick="setDmmMode('CURRENT_MA')">Current</button>
                </div>
            </div>

            <div class="control-group">
                <div class="control-label">
                    <span>Injected Value</span>
                    <span id="dmm-val-label" style="color: var(--amber-accent); font-weight: bold;">3.30 V</span>
                </div>
                <input type="range" min="0" max="30" step="0.1" value="3.3" class="slider" id="dmm-slider" oninput="updateDmmValue(this.value)" />
            </div>

            <button class="btn" style="width: 100%; background: var(--amber-accent);" onclick="pushDmmTelemetry()">Push to Projector</button>
        </div>

        <div class="card">
            <div class="card-title">Oscilloscope Waveform Feed</div>
            <div class="control-group">
                <div class="control-label">Wave Shape</div>
                <div class="btn-row">
                    <button class="mode-btn active" id="wave-sine" onclick="setWaveType('SINE')">Sine</button>
                    <button class="mode-btn" id="wave-square" onclick="setWaveType('SQUARE')">Square</button>
                    <button class="mode-btn" id="wave-triangle" onclick="setWaveType('TRIANGLE')">Triangle</button>
                </div>
            </div>

            <div class="control-group">
                <div class="control-label">
                    <span>Frequency</span>
                    <span id="scope-freq-label" style="color: var(--emerald-accent); font-weight: bold;">1000 Hz</span>
                </div>
                <input type="range" min="50" max="10000" step="50" value="1000" class="slider" id="scope-freq-slider" oninput="updateScopeFreq(this.value)" />
            </div>

            <button class="btn" style="width: 100%; background: var(--emerald-accent);" onclick="pushScopeTelemetry()">Push Waveform</button>
        </div>
    </div>

    <!-- Bottom Navigation Bar -->
    <nav class="bottom-nav">
        <button class="nav-item active" onclick="switchTab('jarvis')">
            <svg viewBox="0 0 24 24"><path d="M12 2C6.48 2 2 6.48 2 12s4.48 10 10 10 10-4.48 10-10S17.52 2 12 2zm1 17.93c-3.95-.49-7-3.85-7-7.93 0-.62.08-1.21.21-1.79L9 15v1c0 1.1.9 2 2 2v1.93zm6.9-2.54c-.26-.81-1-1.39-1.9-1.39h-1v-3c0-.55-.45-1-1-1H8v-2h2c.55 0 1-.45 1-1V7h2c1.1 0 2-.9 2-2v-.41c2.93 1.19 5 4.06 5 7.41 0 2.08-.8 3.97-2.1 5.39z"/></svg>
            <span>Jarvis</span>
        </button>
        <button class="nav-item" onclick="switchTab('todo')">
            <svg viewBox="0 0 24 24"><path d="M19 3H5c-1.1 0-2 .9-2 2v14c0 1.1.9 2 2 2h14c1.1 0 2-.9 2-2V5c0-1.1-.9-2-2-2zm-9 14l-5-5 1.41-1.41L10 14.17l7.59-7.59L19 8l-9 9z"/></svg>
            <span>Tasks</span>
        </button>
        <button class="nav-item" onclick="switchTab('notes')">
            <svg viewBox="0 0 24 24"><path d="M3 17.25V21h3.75L17.81 9.94l-3.75-3.75L3 17.25zM20.71 7.04c.39-.39.39-1.02 0-1.41l-2.34-2.34c-.39-.39-1.02-.39-1.41 0l-1.83 1.83 3.75 3.75 1.83-1.83z"/></svg>
            <span>Notes</span>
        </button>
        <button class="nav-item" onclick="switchTab('sensors')">
            <svg viewBox="0 0 24 24"><path d="M15 9H9v6h6V9zm-2 4h-2v-2h2v2zm8-2V9h-2V7c0-1.1-.9-2-2-2h-2V3h-2v2h-2V3H9v2H7c-1.1 0-2 .9-2 2v2H3v2h2v2H3v2h2v2c0 1.1.9 2 2 2h2v2h2v-2h2v2h2v-2h2c1.1 0 2-.9 2-2v-2h2v-2h-2v-2h2zm-4 6H7V7h10v10z"/></svg>
            <span>Sensors</span>
        </button>
    </nav>

    <div id="toast" class="toast">Action synced</div>

    <script>
        let currentTab = 'jarvis';
        let dmmMode = 'DC_VOLTS';
        let dmmValue = 3.3;
        let waveType = 'SINE';
        let waveFreq = 1000;
        let speechRecognition = null;
        let isRecordingVoice = false;

        function showToast(msg) {
            const toast = document.getElementById('toast');
            toast.innerText = msg;
            toast.style.display = 'block';
            setTimeout(() => { toast.style.display = 'none'; }, 2000);
        }

        function switchTab(tabId) {
            currentTab = tabId;
            document.querySelectorAll('.tab-panel').forEach(p => p.classList.remove('active'));
            document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));

            document.getElementById('tab-' + tabId).classList.add('active');
            event.currentTarget.classList.add('active');

            if (tabId === 'todo') loadTodos();
            if (tabId === 'notes') loadNotes();
        }

        // Voice Input using Web Speech API
        function initVoiceRecognition() {
            const SpeechRec = window.SpeechRecognition || window.webkitSpeechRecognition;
            if (SpeechRec) {
                speechRecognition = new SpeechRec();
                speechRecognition.continuous = false;
                speechRecognition.interimResults = false;
                speechRecognition.lang = 'en-US';

                speechRecognition.onresult = function(event) {
                    const transcript = event.results[0][0].transcript;
                    document.getElementById('jarvis-prompt').value = transcript;
                    toggleVoiceInput(false);
                    sendJarvisPrompt();
                };

                speechRecognition.onerror = function(event) {
                    console.error('Speech recognition error', event);
                    toggleVoiceInput(false);
                };

                speechRecognition.onend = function() {
                    toggleVoiceInput(false);
                };
            }
        }

        function toggleVoiceInput(forceState) {
            if (!speechRecognition) initVoiceRecognition();
            if (!speechRecognition) {
                alert('Speech recognition is not supported in this browser. Please type your query.');
                return;
            }

            const micBtn = document.getElementById('mic-btn');
            if (forceState !== undefined) {
                isRecordingVoice = forceState;
            } else {
                isRecordingVoice = !isRecordingVoice;
            }

            if (isRecordingVoice) {
                micBtn.classList.add('recording');
                try { speechRecognition.start(); } catch(e){}
            } else {
                micBtn.classList.remove('recording');
                try { speechRecognition.stop(); } catch(e){}
            }
        }

        function quickPrompt(text) {
            document.getElementById('jarvis-prompt').value = text;
            sendJarvisPrompt();
        }

        function sendJarvisPrompt() {
            const input = document.getElementById('jarvis-prompt');
            const prompt = input.value.trim();
            if (!prompt) return;

            document.getElementById('jarvis-status').innerText = 'Jarvis is processing and drawing on wall HUD...';
            showToast('Prompt sent to wall HUD');

            fetch('/api/jarvis/query', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ prompt: prompt })
            })
            .then(res => res.json())
            .then(data => {
                document.getElementById('jarvis-status').innerText = data.replyText || 'Visual directive rendered on wall.';
                input.value = '';
            })
            .catch(err => {
                document.getElementById('jarvis-status').innerText = 'Error sending prompt to projector.';
            });
        }

        // To-Do Logic
        function loadTodos() {
            fetch('/api/todos')
                .then(res => res.json())
                .then(todos => {
                    const list = document.getElementById('todo-list');
                    list.innerHTML = '';
                    document.getElementById('todo-count').innerText = todos.length + ' items';

                    todos.forEach(t => {
                        const item = document.createElement('div');
                        item.className = 'todo-item';
                        const checkSvg = t.isCompleted ? '<svg width="12" height="12" viewBox="0 0 24 24" fill="#000"><path d="M9 16.2L4.8 12l-1.4 1.4L9 19 21 7l-1.4-1.4L9 16.2z"/></svg>' : '';
                        const checkedClass = t.isCompleted ? 'checked' : '';
                        const textClass = t.isCompleted ? 'completed' : '';
                        item.innerHTML = '<div class="todo-left" onclick="toggleTodo(' + t.id + ', ' + (!t.isCompleted) + ')">' +
                            '<div class="todo-checkbox ' + checkedClass + '">' + checkSvg + '</div>' +
                            '<span class="todo-text ' + textClass + '">' + t.text + '</span>' +
                            '</div>' +
                            '<button class="btn-delete" onclick="deleteTodo(' + t.id + ')">' +
                            '<svg width="18" height="18" viewBox="0 0 24 24" fill="currentColor"><path d="M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM19 4h-3.5l-1-1h-5l-1 1H5v2h14V4z"/></svg>' +
                            '</button>';
                        list.appendChild(item);
                    });
                });
        }

        function addTodo() {
            const input = document.getElementById('todo-input');
            const text = input.value.trim();
            if (!text) return;

            fetch('/api/todos', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ text: text, priority: 'NORMAL' })
            })
            .then(() => {
                input.value = '';
                loadTodos();
                showToast('Task added to wall HUD');
            });
        }

        function toggleTodo(id, isCompleted) {
            fetch('/api/todos/' + id, {
                method: 'PUT',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ isCompleted: isCompleted })
            }).then(() => loadTodos());
        }

        function deleteTodo(id) {
            fetch('/api/todos/' + id, { method: 'DELETE' }).then(() => loadTodos());
        }

        // Notepad Logic
        function loadNotes() {
            fetch('/api/note')
                .then(res => res.json())
                .then(data => {
                    document.getElementById('notepad-content').value = data.content || '';
                });
        }

        function saveNote() {
            const content = document.getElementById('notepad-content').value;
            fetch('/api/note', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ content: content })
            })
            .then(() => showToast('Notepad synced to projector'));
        }

        // Sensors Logic
        function setDmmMode(mode) {
            dmmMode = mode;
            document.querySelectorAll('#tab-sensors .mode-btn').forEach(b => b.classList.remove('active'));
            event.currentTarget.classList.add('active');
        }

        function updateDmmValue(val) {
            dmmValue = parseFloat(val);
            document.getElementById('dmm-val-label').innerText = dmmValue.toFixed(2) + ' V';
        }

        function pushDmmTelemetry() {
            fetch('/api/telemetry/multimeter', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    mode: dmmMode,
                    value: dmmValue,
                    unit: dmmMode.includes('RES') ? 'kΩ' : (dmmMode.includes('CURR') ? 'mA' : 'V'),
                    barPercent: Math.min(1.0, dmmValue / 30.0)
                })
            })
            .then(() => showToast('Multimeter reading updated on wall'));
        }

        function setWaveType(type) {
            waveType = type;
            ['sine', 'square', 'triangle'].forEach(t => {
                document.getElementById('wave-' + t).classList.toggle('active', t.toUpperCase() === type);
            });
        }

        function updateScopeFreq(freq) {
            waveFreq = parseInt(freq);
            document.getElementById('scope-freq-label').innerText = waveFreq + ' Hz';
        }

        function pushScopeTelemetry() {
            fetch('/api/telemetry/oscilloscope', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({
                    waveType: waveType,
                    frequency: waveFreq,
                    vpp: 3.3
                })
            })
            .then(() => showToast('Oscilloscope wave updated on wall'));
        }

        window.onload = function() {
            initVoiceRecognition();
        };
    </script>
</body>
</html>
"""
    }
}
