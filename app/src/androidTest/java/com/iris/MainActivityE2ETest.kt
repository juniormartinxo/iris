package com.iris

import android.Manifest
import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.hasContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@OptIn(ExperimentalTestApi::class)
@RunWith(AndroidJUnit4::class)
class MainActivityE2ETest {

    @get:Rule(order = 0)
    val permissionRule: GrantPermissionRule =
        GrantPermissionRule.grant(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

    @get:Rule(order = 1)
    val composeTestRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun appOpensAndShowsLoadingOrModelMissing() {
        composeTestRule.waitForIdle()
        // Expect either:
        // - LoadingScreen (model present in test device's external files)
        // - ModelMissingScreen (no model sideloaded — most likely on emulator)
        val anyExpected = listOf(
            "Carregando modelo Iris. Aguarde de quinze a trinta segundos.",
            "Toque duas vezes para descrever",
        )
        val matches = anyExpected.any { desc ->
            composeTestRule.onAllNodes(hasContentDescription(desc, substring = true))
                .fetchSemanticsNodes().isNotEmpty()
        }
        assert(matches) {
            "Expected Loading, ModelMissing, or MainScreen entry; found neither"
        }
    }
}
