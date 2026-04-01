package com.eaglepoint.task136.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.shared.db.ResourceEntity

@Composable
actual fun PlatformResourceList(
    resources: List<ResourceEntity>,
    roleLabel: String,
    modifier: Modifier,
) {
    val adapter = remember { ResourceRecyclerAdapter() }
    adapter.submitList(resources)

    AndroidView(
        factory = { context ->
            RecyclerView(context).apply {
                layoutManager = LinearLayoutManager(context)
                this.adapter = adapter
                setHasFixedSize(true)
                setItemViewCacheSize(20)
            }
        },
        update = { recyclerView ->
            (recyclerView.adapter as? ResourceRecyclerAdapter)?.submitList(resources)
        },
        modifier = modifier,
    )
}
