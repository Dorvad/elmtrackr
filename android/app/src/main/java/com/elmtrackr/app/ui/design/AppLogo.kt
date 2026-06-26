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
import com.elmtrackr.app.R

@Composable
fun AppLogo(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 18.dp,
    contentDescription: String? = "ElmTrackr logo",
) {
    Image(
        painter = painterResource(R.drawable.app_logo),
        contentDescription = contentDescription,
        contentScale = ContentScale.Crop,
        modifier = modifier.clip(RoundedCornerShape(cornerRadius)),
    )
}
