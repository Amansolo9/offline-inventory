package com.eaglepoint.task136.ui

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.ResourceEntity

class ResourceRecyclerAdapter : ListAdapter<ResourceEntity, ResourceRecyclerAdapter.ViewHolder>(ResourceDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val categoryIcon: TextView = view.findViewById(R.id.categoryIcon)
        val nameText: TextView = view.findViewById(R.id.resourceName)
        val categoryText: TextView = view.findViewById(R.id.resourceCategory)
        val allergenText: TextView = view.findViewById(R.id.resourceAllergens)
        val unitsText: TextView = view.findViewById(R.id.resourceUnits)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_resource, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.nameText.text = item.name
        holder.categoryText.text = item.category

        val isLogistics = item.category == "Logistics"
        val iconColor = if (isLogistics) Color.parseColor("#74B9FF") else Color.parseColor("#6C5CE7")
        val bg = GradientDrawable().apply {
            setColor(Color.argb(30, Color.red(iconColor), Color.green(iconColor), Color.blue(iconColor)))
            cornerRadius = 24f
        }
        holder.categoryIcon.background = bg
        holder.categoryIcon.text = if (isLogistics) "L" else "O"
        holder.categoryIcon.setTextColor(iconColor)

        holder.unitsText.text = "${item.availableUnits}"
        holder.unitsText.setTextColor(
            if (item.availableUnits > 0) Color.parseColor("#00B894") else Color.parseColor("#FF7675"),
        )

        if (item.allergens.isNotBlank() && item.allergens != "none") {
            holder.allergenText.visibility = View.VISIBLE
            holder.allergenText.text = item.allergens
        } else {
            holder.allergenText.visibility = View.GONE
        }
    }
}

class ResourceDiffCallback : DiffUtil.ItemCallback<ResourceEntity>() {
    override fun areItemsTheSame(oldItem: ResourceEntity, newItem: ResourceEntity): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: ResourceEntity, newItem: ResourceEntity): Boolean {
        return oldItem == newItem
    }
}
