// Space, radius, size and motion tokens from tokens.json.
package dev.yourname.filebox.ui.theme

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

object Space {
    val none = 0.dp
    val xxs = 4.dp
    val xs = 8.dp
    val sm = 12.dp
    val md = 16.dp
    val lg = 20.dp
    val xl = 24.dp
    val xxl = 32.dp
    val xxxl = 48.dp
}

object Radius {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 28.dp
}

val FileboxShapes = Shapes(
    extraSmall = RoundedCornerShape(Radius.xs),  // snackbar, tooltip
    small = RoundedCornerShape(Radius.sm),       // chips, thumbnails, text fields
    medium = RoundedCornerShape(Radius.md),      // cards, category tiles, grid tiles
    large = RoundedCornerShape(Radius.lg),       // FAB, drawer item highlight
    extraLarge = RoundedCornerShape(Radius.xl),  // dialogs, bottom sheets (top corners)
)

object Size {
    val touchMin = 48.dp
    val topBar = 64.dp
    val breadcrumbBar = 40.dp
    val row = 56.dp
    val rowTwoLine = 72.dp
    val thumb = 40.dp
    val icon = 24.dp
    val iconSm = 20.dp
    val gridTileMin = 104.dp
    val categoryTile = 88.dp
    val fab = 56.dp
    val bottomActions = 80.dp
    val drawer = 300.dp
    val dialogMax = 560.dp
    val screenPadding = 16.dp
}

object Elevation {
    val flat = 0.dp
    val card = 1.dp
    val bar = 3.dp
    val fab = 6.dp
    val sheet = 1.dp
    val dialog = 3.dp
}

object Motion {
    const val FAST = 100
    const val BASE = 200
    const val SLOW = 300
    val Ease = CubicBezierEasing(0.2f, 0f, 0f, 1f)
    val EaseOut = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
}

object Breakpoints {
    val medium = 600.dp
    val expanded = 840.dp
}
