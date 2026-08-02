package com.elmtrackr.app.ui.design

import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.theme.CornerRadius

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = CornerRadius.Button,
    // A composable default may call a composable, so this resolves from
    // resources rather than shipping an English literal to Hebrew users.
    contentDescription: String? = stringResource(R.string.a11y_app_logo),
) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
    )
}
