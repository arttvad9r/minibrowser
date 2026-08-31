package com.artt.minibrowser

import android.app.Application
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.artt.minibrowser.ui.ChoiceRow
import com.artt.minibrowser.ui.EmptyState
import com.artt.minibrowser.ui.MinibrowserTheme
import com.artt.minibrowser.ui.SettingsGroup
import com.artt.minibrowser.ui.SettingsRow
import com.artt.minibrowser.ui.ToggleRow
import com.github.takahirom.roborazzi.ExperimentalRoborazziApi
import com.github.takahirom.roborazzi.RoborazziComposeOptions
import com.github.takahirom.roborazzi.captureRoboImage
import com.github.takahirom.roborazzi.fontScale
import com.github.takahirom.roborazzi.roborazziSystemPropertyOutputDirectory
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], application = Application::class)
class ComposeScreenshotTest {
    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun designFoundationLight() {
        captureRoboImage(
            "${roborazziSystemPropertyOutputDirectory()}/design_foundation_light.png",
        ) {
            MinibrowserTheme(darkTheme = false) {
                DesignFoundationFixture()
            }
        }
    }

    @OptIn(ExperimentalRoborazziApi::class)
    @Test
    fun designFoundationDarkLargeText() {
        captureRoboImage(
            filePath = "${roborazziSystemPropertyOutputDirectory()}/design_foundation_dark_large_text.png",
            roborazziComposeOptions = RoborazziComposeOptions {
                fontScale(2f)
            },
        ) {
            MinibrowserTheme(darkTheme = true) {
                DesignFoundationFixture()
            }
        }
    }
}

@Composable
private fun DesignFoundationFixture() {
    Box(
        Modifier
            .width(360.dp)
            .height(800.dp)
            .background(MaterialTheme.colorScheme.background)
            .padding(20.dp),
    ) {
        Column(
            Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text("Settings", style = MaterialTheme.typography.titleLarge)
            SettingsGroup {
                SettingsRow(
                    title = "Search engine",
                    subtitle = "Used for address-bar searches",
                    value = "Google",
                )
                ToggleRow(
                    icon = Icons.Filled.Search,
                    label = "Content filtering",
                    checked = true,
                    onChecked = {},
                    subtitle = "Blocks known unwanted content",
                )
            }
            ChoiceRow(
                title = "Use system appearance",
                selected = true,
                onClick = {},
            )
            Spacer(Modifier.height(4.dp))
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                EmptyState(
                    icon = Icons.Filled.Search,
                    title = "No downloads",
                    subtitle = "Downloaded files will appear here",
                )
            }
        }
    }
}
