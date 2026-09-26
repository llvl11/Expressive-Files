package com.baiel.expressivefiles

import android.app.Application
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertWidthIsAtLeast
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import com.baiel.expressivefiles.model.SortMode
import com.baiel.expressivefiles.model.SortOrder
import com.baiel.expressivefiles.ui.components.CategorySortBar
import com.baiel.expressivefiles.ui.components.ChunkyIconButton
import com.baiel.expressivefiles.ui.components.SquigglyProgressIndicator
import com.baiel.expressivefiles.ui.theme.ExpressiveFilesTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], application = Application::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExpressiveComponentsTest {
    @get:Rule val compose = createComposeRule()

    @Test fun smallIconRequestStillHasAccessibleTouchTarget() {
        var clicks = 0
        compose.setContent {
            ExpressiveFilesTheme {
                ChunkyIconButton(
                    icon = Icons.Rounded.Close,
                    onClick = { clicks++ },
                    contentDescription = "Close",
                    size = 28.dp
                )
            }
        }
        compose.onNodeWithContentDescription("Close")
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(1, clicks) }
    }

    @Test fun progressIsClampedForAccessibility() {
        compose.setContent {
            ExpressiveFilesTheme {
                SquigglyProgressIndicator(Modifier.testTag("progress"), progress = 2f)
            }
        }
        compose.onNodeWithTag("progress").assert(
            SemanticsMatcher.expectValue(
                SemanticsProperties.ProgressBarRangeInfo,
                ProgressBarRangeInfo(1f, 0f..1f)
            )
        )
    }

    @Test fun sortMetricsSelectAndDeselectBackToPlatformDefault() {
        val mode = mutableStateOf(SortMode.NAME)
        val order = mutableStateOf(SortOrder.ASCENDING)
        val resources = RuntimeEnvironment.getApplication().resources
        compose.setContent {
            ExpressiveFilesTheme {
                CategorySortBar(mode.value, order.value, { newMode, newOrder ->
                    mode.value = newMode
                    order.value = newOrder
                })
            }
        }

        // Tapping the ALREADY selected metric clears the choice: nothing is
        // highlighted and the ordering returns to the platform default
        // (name, A-Z) instead of flipping the direction.
        compose.onNodeWithText(resources.getString(R.string.sort_metric_name))
            .assertIsSelected().assertHeightIsAtLeast(48.dp).performClick()
            .assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(SortMode.DEFAULT, mode.value)
            assertEquals(SortOrder.ASCENDING, order.value)
        }

        // Selecting another metric adopts the deselect-reset direction.
        compose.onNodeWithText(resources.getString(R.string.sort_metric_size))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
            .assertIsSelected()
        compose.runOnIdle {
            assertEquals(SortMode.SIZE, mode.value)
            assertEquals(SortOrder.ASCENDING, order.value)
        }

        // Deselecting the new metric goes back to the platform default too.
        compose.onNodeWithText(resources.getString(R.string.sort_metric_size)).performClick()
            .assertIsNotSelected()
        compose.runOnIdle {
            assertEquals(SortMode.DEFAULT, mode.value)
            assertEquals(SortOrder.ASCENDING, order.value)
        }

        // The direction toggle keeps working for the default ordering as well.
        compose.onNodeWithContentDescription(resources.getString(R.string.sort_order_ascending))
            .assertWidthIsAtLeast(48.dp).assertHeightIsAtLeast(48.dp).performClick()
        compose.runOnIdle { assertEquals(SortOrder.DESCENDING, order.value) }
    }
}
