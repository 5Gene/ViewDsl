package osp.sparkj.viewdsl.qa.theme

import android.app.Activity
import android.content.Context
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.colorResource
import androidx.core.view.ViewCompat
import osp.sparkj.viewdsl.R


@Composable
fun SparkjTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, content: @Composable () -> Unit
) {
    questionTheme(darkTheme, dynamicColor, content)
}

@Composable
fun questionTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = true, content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = if (darkTheme) questionDarkTheme(context) else questionLightTheme(context)
    val view = LocalView.current

    (view.context as Activity).window.statusBarColor = Color.Transparent.toArgb()
    if (!view.isInEditMode) {
        SideEffect {
//            (view.context as Activity).window.statusBarColor = Color.Transparent.toArgb()
//            (view.context as Activity).window.statusBarColor = colorScheme.primary.toArgb()
            ViewCompat.getWindowInsetsController(view)?.isAppearanceLightStatusBars = darkTheme
        }
    }

    MaterialTheme(
        colors = colorScheme, content = content
    )
}

//val oppoSansFamily = FontFamily(
//    Font(R.font.firasans_regular, FontWeight.Normal),
//    Font(R.font.firasans_italic, FontWeight.Normal, FontStyle.Italic),
//    Font(R.font.firasans_medium, FontWeight.Medium),
//    Font(R.font.firasans_bold, FontWeight.Bold)
//)

@Composable
fun questionDarkTheme(context: Context): Colors = darkColors(
    primary = Color(android.graphics.Color.parseColor("#FF2DC84E")), background = colorResource(R.color.card_bg)
)

@Composable
fun questionLightTheme(context: Context): Colors = lightColors(
    primary = Color(android.graphics.Color.parseColor("#FF2DC84E")), background = colorResource(R.color.card_bg)
)