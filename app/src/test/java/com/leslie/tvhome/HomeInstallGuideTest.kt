package com.leslie.tvhome

import org.junit.Assert.assertEquals
import org.junit.Test

class HomeInstallGuideTest {
    @Test fun actualHomeTakesPriorityOverVendorGuess() {
        assertEquals(R.string.install_home_next_native,
            HomeInstallGuide.nextStep("TCL", "FFalcon", true, false, false))
    }
    @Test fun boundGuardRequiresPhysicalVerification() {
        assertEquals(R.string.install_home_next_guard,
            HomeInstallGuide.nextStep("Xiaomi", "Redmi", false, true, true))
    }
    @Test fun firmwareGuidanceDoesNotAssumeHomeIsWorking() {
        assertEquals(R.string.install_home_next_xiaomi,
            HomeInstallGuide.nextStep("Xiaomi", "Redmi", false, false, false))
        assertEquals(R.string.install_home_next_tcl,
            HomeInstallGuide.nextStep("TCL", "FFalcon", false, false, false))
        assertEquals(R.string.install_home_next_permission,
            HomeInstallGuide.nextStep("Sony", "BRAVIA", false, false, false))
        assertEquals(R.string.install_home_next_generic,
            HomeInstallGuide.nextStep("Sony", "BRAVIA", false, false, true))
    }
}
