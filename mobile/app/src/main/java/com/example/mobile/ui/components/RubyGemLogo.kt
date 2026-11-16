package com.example.mobile.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.mobile.R

/**
 * Displays the RuBank monogram logo (bright crimson squircle with interlocking white/rose 'ru' ribbon).
 */
@Composable
fun RubyGemIcon(
    modifier: Modifier = Modifier,
    size: Dp = 36.dp
) {
    Image(
        painter = painterResource(id = R.drawable.ic_ruby_gem),
        contentDescription = "RuBank Logo",
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size)
    )
}

/**
 * Clean Nubank-inspired brand header mark: interlocking 'ru' logo + "rubank" wordmark.
 */
@Composable
fun RuBankBrandBadge(
    modifier: Modifier = Modifier,
    gemSize: Dp = 36.dp,
    fontSizeSp: Int = 22,
    textColor: Color = Color.White
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        RubyGemIcon(size = gemSize)
        Text(
            text = buildAnnotatedString {
                withStyle(
                    SpanStyle(
                        color = textColor,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = fontSizeSp.sp
                    )
                ) {
                    append("ru")
                }
                withStyle(
                    SpanStyle(
                        color = textColor.copy(alpha = 0.92f),
                        fontWeight = FontWeight.Normal,
                        fontSize = fontSizeSp.sp
                    )
                ) {
                    append("bank")
                }
            },
            letterSpacing = (-0.5).sp
        )
    }
}
