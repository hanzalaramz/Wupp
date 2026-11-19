package com.wa.beastclient.domain.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ScreenshotTogglePolicyTest {

    @Test
    fun screenshotsAreAllowedByDefaultEvenWithAppLockAndHideInRecentsEnabled() {
        val cfg = AppLockConfig(
            mode = AppLockMode.DEVICE,
            hideInRecents = true
        )
        assertTrue(cfg.allowScreenshots)

        val api34Policy = AppLockManager.resolveWindowPrivacyPolicy(cfg, sdkInt = 34)
        assertFalse(api34Policy.useFlagSecure)
        assertFalse(api34Policy.allowRecentsScreenshot)

        val api31Policy = AppLockManager.resolveWindowPrivacyPolicy(cfg, sdkInt = 31)
        assertFalse(api31Policy.useFlagSecure)
    }

    @Test
    fun disablingAllowScreenshotsEnforcesFlagSecureEvenWhenAppLockIsOff() {
        val cfg = AppLockConfig(
            mode = AppLockMode.OFF,
            hideInRecents = false,
            allowScreenshots = false
        )

        val api34Policy = AppLockManager.resolveWindowPrivacyPolicy(cfg, sdkInt = 34)
        assertTrue(api34Policy.useFlagSecure)
        assertFalse(api34Policy.allowRecentsScreenshot)

        val api29Policy = AppLockManager.resolveWindowPrivacyPolicy(cfg, sdkInt = 29)
        assertTrue(api29Policy.useFlagSecure)
        assertFalse(api29Policy.allowRecentsScreenshot)
    }

    @Test
    fun allowingScreenshotsWithHideRecentsOffPermitsBothCapturesAndRecentsPreview() {
        val cfg = AppLockConfig(
            mode = AppLockMode.CUSTOM_PIN,
            hideInRecents = false,
            allowScreenshots = true
        )
        val policy = AppLockManager.resolveWindowPrivacyPolicy(cfg, sdkInt = 34)
        assertFalse(policy.useFlagSecure)
        assertTrue(policy.allowRecentsScreenshot)
    }
}
