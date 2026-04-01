package com.eaglepoint.task136.ui

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.eaglepoint.task136.R
import com.eaglepoint.task136.shared.viewmodel.AuthViewModel
import com.eaglepoint.task136.shared.viewmodel.LearningViewModel
import com.google.android.material.button.MaterialButton
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.core.context.GlobalContext

class LearningFragment : Fragment() {

    private val learningVm: LearningViewModel by lazy { GlobalContext.get().get() }
    private val authVm: AuthViewModel by lazy { GlobalContext.get().get() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        return inflater.inflate(R.layout.fragment_learning, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val recyclerView = view.findViewById<RecyclerView>(R.id.courseRecyclerView)
        val loadingBar = view.findViewById<ProgressBar>(R.id.loadingBar)
        val backBtn = view.findViewById<MaterialButton>(R.id.backButton)

        val userId = authVm.state.value.principal?.userId.orEmpty()
        val role = authVm.state.value.role ?: com.eaglepoint.task136.shared.rbac.Role.Viewer

        val adapter = CourseRecyclerAdapter { course ->
            learningVm.enroll(role, userId, course.id)
            authVm.touchSession()
        }
        recyclerView.layoutManager = LinearLayoutManager(requireContext())
        recyclerView.adapter = adapter
        recyclerView.setHasFixedSize(true)

        backBtn.setOnClickListener { (activity as? NavigationHost)?.navigateBack() }

        learningVm.loadCourses()
        learningVm.loadEnrollments(userId)

        viewLifecycleOwner.lifecycleScope.launch {
            learningVm.state.collectLatest { state ->
                adapter.submitList(state.courses)
                loadingBar.visibility = if (state.isLoading) View.VISIBLE else View.GONE

                val note = state.note
                if (note != null && isAdded) {
                    Toast.makeText(requireContext(), note, Toast.LENGTH_SHORT).show()
                    learningVm.clearNote()
                }
            }
        }
    }
}
