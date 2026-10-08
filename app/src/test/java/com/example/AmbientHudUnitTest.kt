package com.example

import com.example.ai.JarvisAgent
import com.example.data.model.*
import com.example.service.WebRemoteHtml
import org.junit.Assert.*
import org.junit.Test

class AmbientHudUnitTest {

    private val jarvisAgent = JarvisAgent()

    @Test
    fun testJarvisTrmRouteQueryGeneratesRouteMap() {
        val prompt = "how far is TRM from town"
        val state = jarvisAgent.generateLocalResponse(prompt)

        assertEquals(JarvisVisualType.ROUTE_MAP, state.visualType)
        assertTrue(state.replyText.contains("13.8", ignoreCase = true) || state.replyText.contains("TRM", ignoreCase = true))
        assertNotNull(state.routeData)
        val route = state.routeData!!
        assertEquals("Nairobi CBD", route.origin)
        assertEquals("TRM (Thika Road Mall)", route.destination)
        assertEquals("13.8 km", route.distance)
        assertTrue(route.waypoints.isNotEmpty())
        assertTrue(route.waypoints.first().isOrigin)
        assertTrue(route.waypoints.last().isDestination)
    }

    @Test
    fun testJarvisMultimeterQueryGeneratesCircuitDiagram() {
        val prompt = "wireless multimeter probe test"
        val state = jarvisAgent.generateLocalResponse(prompt)

        assertEquals(JarvisVisualType.CIRCUIT_DIAGRAM, state.visualType)
        assertNotNull(state.diagramData)
        val diag = state.diagramData!!
        assertTrue(diag.nodes.isNotEmpty())
        assertTrue(diag.connections.isNotEmpty())
        assertNotNull(state.metricsData)
    }

    @Test
    fun testJarvisKpiQueryGeneratesMetricCards() {
        val prompt = "show me system performance KPIs"
        val state = jarvisAgent.generateLocalResponse(prompt)

        assertEquals(JarvisVisualType.METRIC_CARD, state.visualType)
        assertNotNull(state.metricsData)
        assertTrue(state.metricsData!!.isNotEmpty())
    }

    @Test
    fun testJarvisDefaultQueryGeneratesInfographic() {
        val prompt = "tell me a random fact"
        val state = jarvisAgent.generateLocalResponse(prompt)

        assertEquals(JarvisVisualType.INFO_GRAPHIC, state.visualType)
        assertNotNull(state.stepsData)
    }

    @Test
    fun testMultimeterTelemetryDefaults() {
        val dmm = MultimeterTelemetry(
            mode = "DC_VOLTS",
            value = 5.0,
            unit = "V",
            barPercent = 0.5f
        )
        assertEquals("DC_VOLTS", dmm.mode)
        assertEquals(5.0, dmm.value, 0.001)
        assertEquals("V", dmm.unit)
        assertEquals(0.5f, dmm.barPercent, 0.001f)
    }

    @Test
    fun testOscilloscopeTelemetryDefaults() {
        val scope = OscilloscopeTelemetry(
            channel = "CH1",
            waveType = "SINE",
            frequency = 1000.0,
            vpp = 3.3
        )
        assertEquals("CH1", scope.channel)
        assertEquals("SINE", scope.waveType)
        assertEquals(1000.0, scope.frequency, 0.001)
        assertEquals(3.3, scope.vpp, 0.001)
    }

    @Test
    fun testWebRemoteHtmlContainsEssentialTabsAndControls() {
        val html = WebRemoteHtml.getRemoteHtml("192.168.1.100", 8080)
        assertNotNull(html)
        assertTrue(html.contains("PRISM CAST HUD"))
        assertTrue(html.contains("Jarvis Wall AI"))
        assertTrue(html.contains("Wall Synced Tasks"))
        assertTrue(html.contains("Wall Ambient Notepad"))
        assertTrue(html.contains("Wireless Multimeter Feed"))
        assertTrue(html.contains("Oscilloscope Waveform Feed"))
        assertTrue(html.contains("toggleVoiceInput"))
        assertTrue(html.contains("api/jarvis/query"))
        assertTrue(html.contains("api/todos"))
        assertTrue(html.contains("api/note"))
        assertTrue(html.contains("api/telemetry/multimeter"))
        assertTrue(html.contains("api/telemetry/oscilloscope"))
    }
}
