package com.example.data.model

enum class JarvisVisualType {
    NONE,
    ROUTE_MAP,
    CIRCUIT_DIAGRAM,
    METRIC_CARD,
    COMPARISON_CHART,
    STEP_GUIDE,
    INFO_GRAPHIC
}

data class RoutePoint(
    val name: String,
    val detail: String = "",
    val isOrigin: Boolean = false,
    val isDestination: Boolean = false
)

data class RouteMapData(
    val origin: String = "Nairobi CBD",
    val destination: String = "TRM (Thika Road Mall)",
    val distance: String = "13.8 km",
    val duration: String = "25-35 mins",
    val routeName: String = "Thika Superhighway (A2)",
    val trafficCondition: String = "Moderate Flow",
    val waypoints: List<RoutePoint> = emptyList(),
    val directions: List<String> = emptyList()
)

data class KeyMetric(
    val label: String,
    val value: String,
    val subtext: String = "",
    val badge: String = "OK"
)

data class DiagramNode(
    val id: String,
    val label: String,
    val type: String = "IC", // "RESISTOR", "CAPACITOR", "MCU", "SENSOR", "POWER", "GROUND"
    val xPercent: Float = 0.5f,
    val yPercent: Float = 0.5f
)

data class DiagramConnection(
    val fromId: String,
    val toId: String,
    val label: String = ""
)

data class DiagramData(
    val title: String = "Schematic Blueprint",
    val nodes: List<DiagramNode> = emptyList(),
    val connections: List<DiagramConnection> = emptyList()
)

data class JarvisState(
    val prompt: String = "",
    val replyText: String = "Jarvis Ambient HUD is online and listening.",
    val visualType: JarvisVisualType = JarvisVisualType.NONE,
    val title: String = "",
    val subtitle: String = "",
    val routeData: RouteMapData? = null,
    val metricsData: List<KeyMetric>? = null,
    val diagramData: DiagramData? = null,
    val stepsData: List<String>? = null,
    val isLoading: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)
