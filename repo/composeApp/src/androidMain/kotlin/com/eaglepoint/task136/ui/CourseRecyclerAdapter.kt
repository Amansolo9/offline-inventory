package com.eaglepoint.task136.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.db.CourseEntity
import com.google.android.material.button.MaterialButton

class CourseRecyclerAdapter(
    private val onEnroll: (CourseEntity) -> Unit,
) : ListAdapter<CourseEntity, CourseRecyclerAdapter.ViewHolder>(CourseDiffCallback()) {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val title: TextView = view.findViewById(R.id.courseTitle)
        val description: TextView = view.findViewById(R.id.courseDescription)
        val duration: TextView = view.findViewById(R.id.courseDuration)
        val enrollBtn: MaterialButton = view.findViewById(R.id.enrollBtn)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_course, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = getItem(position)
        holder.title.text = item.title
        holder.description.text = item.description
        holder.duration.text = "${item.durationMinutes} min | ${item.category}"
        holder.enrollBtn.setOnClickListener { onEnroll(item) }
    }
}

class CourseDiffCallback : DiffUtil.ItemCallback<CourseEntity>() {
    override fun areItemsTheSame(oldItem: CourseEntity, newItem: CourseEntity) = oldItem.id == newItem.id
    override fun areContentsTheSame(oldItem: CourseEntity, newItem: CourseEntity) = oldItem == newItem
}
