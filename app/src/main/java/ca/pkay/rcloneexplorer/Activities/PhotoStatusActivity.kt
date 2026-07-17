package ca.pkay.rcloneexplorer.Activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.PhotoStatusProvider
import ca.pkay.rcloneexplorer.util.ActivityHelper
import com.bumptech.glide.Glide
import com.google.android.material.appbar.MaterialToolbar
import java.util.concurrent.Executors

class PhotoStatusActivity : AppCompatActivity() {

    private val executor = Executors.newSingleThreadExecutor()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_photo_status)
        findViewById<MaterialToolbar>(R.id.photo_status_toolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }
        findViewById<RecyclerView>(R.id.photo_status_list).layoutManager = GridLayoutManager(this, 3)
        loadStatus()
    }

    private fun loadStatus() {
        val progress = findViewById<View>(R.id.photo_status_progress)
        val empty = findViewById<TextView>(R.id.photo_status_empty)
        val summary = findViewById<TextView>(R.id.photo_status_summary)
        progress.visibility = View.VISIBLE
        executor.execute {
            val result = PhotoStatusProvider(this).load()
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                progress.visibility = View.GONE
                when (result) {
                    PhotoStatusProvider.Result.Unavailable -> {
                        empty.setText(R.string.photo_status_unavailable)
                        empty.visibility = View.VISIBLE
                    }
                    is PhotoStatusProvider.Result.Success -> {
                        if (result.items.isEmpty()) {
                            empty.setText(R.string.photo_status_empty)
                            empty.visibility = View.VISIBLE
                        } else {
                            val protectedCount = result.items.count {
                                it.state == PhotoStatusProvider.State.PROTECTED
                            }
                            summary.text = getString(
                                R.string.photo_status_summary,
                                protectedCount,
                                result.items.size - protectedCount
                            )
                            findViewById<RecyclerView>(R.id.photo_status_list).adapter =
                                PhotoAdapter(result.items)
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private inner class PhotoAdapter(
        private val items: List<PhotoStatusProvider.MediaStatus>
    ) : RecyclerView.Adapter<PhotoViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PhotoViewHolder {
            return PhotoViewHolder(
                LayoutInflater.from(parent.context).inflate(R.layout.item_photo_status, parent, false)
            )
        }

        override fun getItemCount() = items.size

        override fun onBindViewHolder(holder: PhotoViewHolder, position: Int) {
            val item = items[position]
            Glide.with(holder.thumbnail).load(item.uri).centerCrop().into(holder.thumbnail)
            holder.thumbnail.contentDescription = item.name
            holder.thumbnail.alpha = if (item.state == PhotoStatusProvider.State.PROTECTED) 1f else 0.55f
            holder.badge.setText(
                if (item.state == PhotoStatusProvider.State.PROTECTED) {
                    R.string.photo_status_protected
                } else {
                    R.string.photo_status_waiting
                }
            )
        }
    }

    private class PhotoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val thumbnail: ImageView = view.findViewById(R.id.photo_status_thumbnail)
        val badge: TextView = view.findViewById(R.id.photo_status_badge)
    }
}