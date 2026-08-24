package com.elmtrackr.app.ui.design

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.elmtrackr.app.R
import com.elmtrackr.app.ui.theme.CornerRadius
import com.elmtrackr.app.ui.theme.GoogleButtonDark
import com.elmtrackr.app.ui.theme.GoogleButtonLight
import com.elmtrackr.app.ui.theme.isAuroraDarkTheme

/**
 * The "Sign in with Google" button, to Google's published spec.
 *
 * Deliberately not an [ElmOutlinedButton] with a logo bolted on. This is the one
 * control on the screen the user has seen a hundred times elsewhere, and its
 * value comes entirely from being instantly recognisable — repainting it in the
 * app's palette would trade that away for visual tidiness. Only the corner radius
 * follows Aurora, which the guidelines allow, so it does not sit at odds with the
 * button directly below it.
 *
 * The mark is drawn from the asset unmodified and untinted: it must keep its four
 * colours and its aspect ratio in both themes.
 *
 * @param text one of Google's approved calls to action, localized. The caller
 *   supplies it because "sign in" and "sign up" are different promises even
 *   though this makes the same call either way.
 */
@Composable
fun GoogleSignInButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    isLoading: Boolean = false,
) {
    val colors = if (isAuroraDarkTheme()) GoogleButtonDark else GoogleButtonLight
    val interactionSource = remember { MutableInteractionSource() }

    Button(
        onClick = onClick,
        enabled = enabled && !isLoading,
        shape = RoundedCornerShape(CornerRadius.Button),
        border = BorderStroke(1.dp, colors.stroke),
        colors = ButtonDefaults.buttonColors(
            containerColor = colors.container,
            contentColor = colors.text,
            // The button keeps its own colours while disabled rather than fading
            // to Material's grey: a greyed-out Google mark reads as a broken
            // image. Only the click is withheld.
            disabledContainerColor = colors.container,
            disabledContentColor = colors.text,
        ),
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp),
        elevation = ButtonDefaults.buttonElevation(
            defaultElevation = 0.dp,
            pressedElevation = 0.dp,
            disabledElevation = 0.dp,
        ),
        interactionSource = interactionSource,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .auroraPressScale(interactionSource),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Image(
                painter = painterResource(R.drawable.ic_google_g),
                contentDescription = null,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(12.dp))
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = colors.text,
                )
            } else {
                Text(
                    text = text,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}
