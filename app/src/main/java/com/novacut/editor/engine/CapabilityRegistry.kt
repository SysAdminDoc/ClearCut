package com.novacut.editor.engine

/** Public capability state and dependency metadata generated from the checked-in registry. */
data class CapabilityRecord(
    val id: String,
    val name: String,
    val engine: String,
    val onDevice: String,
    val status: String,
    val reachability: String,
)

data class PublicDependencyRecord(
    val id: String,
    val label: String,
    val version: String,
    val coordinate: String,
    val catalogKeys: List<String>,
    val purpose: String,
    val publicStatus: String,
)

object CapabilityRegistry {
    val capabilities: List<CapabilityRecord> = CapabilityRegistryGenerated.capabilities
    val dependencies: List<PublicDependencyRecord> = CapabilityRegistryGenerated.dependencies
    val notices: List<OpenSourceLicenseNotice> = CapabilityRegistryGenerated.notices

    fun capabilityFor(id: String): CapabilityRecord? = capabilities.firstOrNull { it.id == id }

    fun dependencyFor(id: String): PublicDependencyRecord? = dependencies.firstOrNull { it.id == id }
}
