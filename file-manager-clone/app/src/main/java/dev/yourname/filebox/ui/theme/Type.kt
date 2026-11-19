// Type scale from tokens.json "type". System sans, no bundled font.
package dev.yourname.filebox.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private fun style(size: Int, line: Int, weight: Int) = TextStyle(
    fontFamily = FontFamily.Default,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = FontWeight(weight),
)

// Token -> Material 3 slot. Components use MaterialTheme.typography.<slot>, never raw sizes.
val FileboxTypography = Typography(
    headlineSmall = style(28, 36, 500),  // headline: storage total on S01
    titleLarge = style(22, 28, 500),     // title-lg: top bar titles, dialog titles
    titleMedium = style(16, 24, 500),    // title: section headers, sheet titles
    titleSmall = style(14, 20, 500),     // title-sm: file row primary line
    bodyLarge = style(16, 24, 400),      // body-lg: dialog body, text fields
    bodyMedium = style(14, 20, 400),     // body: default text
    bodySmall = style(12, 16, 400),      // body-sm: file row meta (size, date)
    labelLarge = style(14, 20, 500),     // buttons
    labelMedium = style(12, 16, 500),    // label: chips, category tile labels, badges
    labelSmall = style(11, 16, 500),     // label-sm: counts, bottom action labels
)

val MonoPath = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp, lineHeight = 18.sp)
