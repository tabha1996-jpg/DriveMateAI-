package com.drivemate.ai

/**
 * Contract for future authorised charge-point integrations. No bundled credentials,
 * scraping, payment access or fictitious live data.
 */
enum class ChargingNetwork(val label: String) {
    HPCL("HP eCharge / HPCL"),
    IOCL("IndianOil e-Charge"),
    BPCL("BPCL eDrive"),
    TATA_POWER("Tata Power EZ Charge"),
    JIO_BP("Jio-bp pulse")
}

enum class ConnectorState {
    REPORTED_AVAILABLE, REPORTED_BUSY, REPORTED_OUT_OF_SERVICE, UNVERIFIED
}

enum class EvidenceKind {
    OPERATOR_CONNECTOR, AGGREGATED_CONNECTORS, STATIC_LISTING
}

data class ConnectorEvidence(
    val connectorId: String?, // null when source has only aggregated counts
    val format: String, // e.g. CCS2
    val powerKw: Double?,
    val state: ConnectorState,
    val observedAtEpochMillis: Long?,
    val evidenceKind: EvidenceKind,
    val sourceName: String
)

data class StationEvidence(
    val provider: ChargingNetwork,
    val stationId: String,
    val stationName: String,
    val latitude: Double,
    val longitude: Double,
    val connectors: List<ConnectorEvidence>,
    val sourceName: String,
    val fetchedAtEpochMillis: Long,
    val costPerKwhInr: Double? = null
)

/**
 * A compatible Google Places result can provide a COUNT of available CCS2 connectors
 * by type and power. That does not identify Gun 1 / Gun 2 or prove hardware works.
 */
data class ConnectorGroupEvidence(
    val type: String,
    val powerKw: Double?,
    val total: Int,
    val available: Int?,
    val outOfService: Int?,
    val lastUpdatedEpochMillis: Long?
)

data class VerifiedConnectorView(
    val connectorId: String,
    val description: String,
    val state: ConnectorState,
    val lastUpdatedEpochMillis: Long?,
    val reason: String
)

object ChargerEvidenceRules {
    const val MAX_STATUS_AGE_MILLIS: Long = 5 * 60 * 1000L

    /**
     * Keep uncertain and outdated observations out of the "reported available" bucket.
     * The result still describes reported state, not guaranteed physical operability.
     */
    fun safeConnectorStatus(
        evidence: ConnectorEvidence,
        nowEpochMillis: Long
    ): VerifiedConnectorView {
        val timestamp = evidence.observedAtEpochMillis
        val fresh = timestamp != null &&
            timestamp <= nowEpochMillis &&
            nowEpochMillis - timestamp <= MAX_STATUS_AGE_MILLIS
        val individual = evidence.evidenceKind == EvidenceKind.OPERATOR_CONNECTOR &&
            !evidence.connectorId.isNullOrBlank()
        val status = if (fresh && individual) evidence.state else ConnectorState.UNVERIFIED
        val explanation = when {
            !individual -> "Source did not identify this individual gun"
            !fresh -> "Status missing, stale or timestamp invalid"
            status == ConnectorState.REPORTED_AVAILABLE -> "Provider recently reported available; physical success not guaranteed"
            status == ConnectorState.REPORTED_BUSY -> "Provider recently reported busy"
            status == ConnectorState.REPORTED_OUT_OF_SERVICE -> "Provider recently reported out of service"
            else -> "Provider could not verify availability"
        }
        return VerifiedConnectorView(
            connectorId = evidence.connectorId ?: "Unidentified connector",
            description = "${evidence.format} • ${evidence.powerKw?.let { "${it} kW" } ?: "power unknown"}",
            state = status,
            lastUpdatedEpochMillis = timestamp,
            reason = explanation
        )
    }

    /** Aggregate availability is NOT an individually identified gun. */
    fun aggregateStatusDescription(group: ConnectorGroupEvidence, nowEpochMillis: Long): String {
        val whenUpdated = group.lastUpdatedEpochMillis
        val fresh = whenUpdated != null &&
            whenUpdated <= nowEpochMillis &&
            nowEpochMillis - whenUpdated <= MAX_STATUS_AGE_MILLIS
        if (!fresh) return "Unverified (missing/stale availability update)"
        val count = group.available ?: return "Unverified (available count missing)"
        return "Source reports $count of ${group.total} ${group.type} connectors available; individual gun identity unknown"
    }
}

/**
 * Source boundary. Providers can be added only after authorised API access exists.
 * Implementations must never turn failed/missing responses into 'available'.
 */
interface ChargingNetworkSource {
    val network: ChargingNetwork
    suspend fun fetchStations(latitude: Double, longitude: Double): List<StationEvidence>
}

class ChargingProviderRegistry(private val sources: List<ChargingNetworkSource> = emptyList()) {
    val connected: Set<ChargingNetwork> get() = sources.map { it.network }.toSet()
    val disconnected: Set<ChargingNetwork> get() = ChargingNetwork.entries.toSet() - connected

    fun connectionSummary(): String =
        ChargingNetwork.entries.joinToString(separator = "\n") {
            val label = if (it in connected) "Connector integration configured (not independently tested)" else
                "Live connector feed not connected"
            "${it.label}: $label"
        }
}
