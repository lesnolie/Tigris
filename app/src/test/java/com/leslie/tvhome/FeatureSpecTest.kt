package com.leslie.tvhome

import com.leslie.tvhome.features.Features
import org.junit.Assert.*
import org.junit.Test

class FeatureSpecTest {
    @Test fun weatherRemainsOptInWithoutBrandRequirements() {
        assertFalse(Features.weather.defaultEnabled)
        assertTrue(Features.weather.supported(28, emptySet()))
        assertFalse(Features.weather.supported(27, emptySet()))
    }
    @Test fun hdmiRequiresActualAdapterCapability() {
        assertFalse(Features.hdmi.supported(36, emptySet()))
        assertTrue(Features.hdmi.supported(28, setOf("tv.inputs")))
    }
    @Test fun builtinsHaveUniqueIdsAndDocumentedFallbacks() {
        assertEquals(Features.all.size, Features.all.map { it.id }.toSet().size)
        assertTrue(Features.all.all { it.fallback.isNotBlank() && it.lifecycle.isNotBlank() })
    }
}
