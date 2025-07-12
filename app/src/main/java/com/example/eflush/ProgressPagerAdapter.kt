
// Stack Universal

package com.example.eflush

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

class ProgressPagerAdapter(
    private val layouts: List<Int>
) : RecyclerView.Adapter<ProgressPagerAdapter.ViewHolder>() {

    class ViewHolder(val view: View) : RecyclerView.ViewHolder(view)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(layouts[viewType], parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount(): Int = layouts.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        // Logic untuk update view di masing-masing page diatur di MainActivity
    }

    override fun getItemViewType(position: Int): Int = position
}
