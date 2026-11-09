package com.arcnova.app.engine

/**
 * Central registry for codec implementations.
 *
 * Kept empty until an engine passes license review and conformance tests.
 * Archive container readers and codecs will be registered separately.
 */
object EngineRegistry {
    val availableCodecs: List<CodecEngine> = emptyList()
    val availableArchiveEngines: List<ArchiveEngine> = emptyList()
}
