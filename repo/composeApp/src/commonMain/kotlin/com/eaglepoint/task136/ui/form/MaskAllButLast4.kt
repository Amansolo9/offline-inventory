package com.eaglepoint.task136.ui.form

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

object MaskAllButLast4 : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val raw = text.text
        val transformed = if (raw.length <= 4) raw else "*".repeat(raw.length - 4) + raw.takeLast(4)
        return TransformedText(AnnotatedString(transformed), OffsetMapping.Identity)
    }
}
