package com.eaglepoint.task136.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.eaglepoint.task136.shared.db.ResourceEntity

@Composable
expect fun PlatformResourceList(
    resources: List<ResourceEntity>,
    roleLabel: String,
    modifier: Modifier = Modifier,
)
