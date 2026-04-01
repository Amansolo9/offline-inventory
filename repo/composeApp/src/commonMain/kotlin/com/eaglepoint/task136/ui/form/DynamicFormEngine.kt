package com.eaglepoint.task136.ui.form

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
data class DataDictionary(
    val version: Int,
    val layouts: List<FormLayout>,
)

@Serializable
data class FormLayout(
    val name: String,
    val version: Int,
    val fields: List<FormFieldDefinition>,
)

@Serializable
data class FormFieldDefinition(
    val key: String,
    val label: String,
    val type: String,
    @SerialName("masked") val isMasked: Boolean = false,
)

@Composable
fun DynamicForm(
    json: String,
    layoutName: String,
    layoutVersion: Int,
    onValuesChanged: (Map<String, String>) -> Unit,
) {
    val dictionary = remember(json) { Json.decodeFromString<DataDictionary>(json) }
    val layout = dictionary.layouts.firstOrNull { it.name == layoutName && it.version == layoutVersion } ?: return
    val values = remember { mutableStateMapOf<String, String>() }

    Column(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
        layout.fields.forEach { field ->
            OutlinedTextField(
                value = values[field.key].orEmpty(),
                onValueChange = {
                    values[field.key] = it
                    onValuesChanged(values)
                },
                label = { Text(field.label) },
                visualTransformation = if (field.isMasked) MaskAllButLast4 else VisualTransformation.None,
                modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp),
            )
        }
    }
}
