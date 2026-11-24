package com.hebi.tripcanvas.designsystem.components

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.hebi.tripcanvas.designsystem.theme.TripDesign

@Composable
fun TripTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    supportingText: String? = null,
    isError: Boolean = false,
    singleLine: Boolean = true,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val p = TripDesign.palette
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supportingText?.let {
            { Text(
                it,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isError) p.error else p.textSecondary
            ) }
        },
        modifier = modifier.fillMaxWidth(),
        isError = isError,
        singleLine = singleLine,
        visualTransformation = visualTransformation,
        keyboardOptions = keyboardOptions,
        shape = RectangleShape,
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = p.textPrimary,
            unfocusedBorderColor = p.outline,
            errorBorderColor = p.error,
            focusedLabelColor = p.textPrimary,
            unfocusedLabelColor = p.textSecondary,
            errorLabelColor = p.error,
            cursorColor = p.textPrimary
        )
    )
}
