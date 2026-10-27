package com.leslie.tvhome

import com.leslie.tvhome.tv.Input
import com.leslie.tvhome.tv.SonyInputFallback
import org.junit.Assert.*
import org.junit.Test

class SonyInputFallbackTest {
    private fun input(port: Int, label: String = "") = Input("content://android.media.tv/passthrough/vendor%2F$port", "HDMI $port", label, true)
    @Test fun mapsExactPhysicalPortRatherThanListOrder() {
        val inputs = listOf(input(10), input(2), input(1))
        assertEquals(inputs.last(), SonyInputFallback.find("extInput:hdmi?port=1", inputs))
    }
    @Test fun supportsCecParametersAndPreservesExistingPinnedUris() {
        assertEquals(input(2), SonyInputFallback.find("extInput:hdmi?logicalAddr=4&port=2", listOf(input(2))))
    }
    @Test fun editedUserLabelDoesNotChooseTheWrongPort() {
        val wrong = input(2, "HDMI 1")
        assertNull(SonyInputFallback.find("extInput:hdmi?port=1", listOf(wrong)))
    }
    @Test fun ambiguousMissingOrNonAndroidInputsRequireSystemPicker() {
        assertNull(SonyInputFallback.find("extInput:hdmi?port=1", listOf(input(1), input(1))))
        assertNull(SonyInputFallback.find("extInput:hdmi?port=1", listOf(input(2))))
        assertNull(SonyInputFallback.find("extInput:hdmi?port=1", listOf(Input("extInput:hdmi?port=1", "HDMI 1", "", true))))
    }
    @Test fun malformedPortDoesNotSwitchAnySource() {
        for (uri in listOf("extInput:hdmi?port=0", "extInput:hdmi?port=1&port=2", "extInput:hdmi?port=one", "extInput:cec?port=1"))
            assertNull(SonyInputFallback.find(uri, listOf(input(1))))
    }
}
