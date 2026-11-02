package app.pengwa.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import app.pengwa.R

/** A quiet scrapbook accent, excluded from accessibility and pointer input. */
@Composable
internal fun HomeBackdropArt(modifier: Modifier = Modifier) {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    Image(
        painter = painterResource(R.drawable.home_encounter_collage),
        contentDescription = null,
        modifier = modifier.width(200.dp),
        alpha = if (dark) 0.18f else 0.42f,
    )
}
