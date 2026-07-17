package ca.pkay.rcloneexplorer.Activities

import android.content.Intent
import android.os.Bundle
import android.text.format.DateUtils
import android.text.format.Formatter
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.GuidedBackupManager
import ca.pkay.rcloneexplorer.guided.GuidedBackupStatusStore
import ca.pkay.rcloneexplorer.guided.HomeNetworkAwareness
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.util.AppMode
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import ca.pkay.rcloneexplorer.widgets.BackupStatusWidget
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import java.text.NumberFormat
import java.util.concurrent.Executors

class HomeActivity : AppCompatActivity() {

    private lateinit var manager: GuidedBackupManager
    private lateinit var statusIcon: ImageView
    private lateinit var statusValue: TextView
    private lateinit var statusDetail: TextView
    private lateinit var lastValue: TextView
    private lateinit var nextValue: TextView
    private lateinit var filesValue: TextView
    private lateinit var bytesValue: TextView
    private lateinit var refreshProgress: View
    private lateinit var guidedContainer: LinearLayout
    private lateinit var customSection: TextView
    private lateinit var customContainer: LinearLayout
    private lateinit var memoryMoment: TextView
    private val executor = Executors.newFixedThreadPool(2)
    private val observedTaskIds = mutableSetOf<Long>()
    private val workStates = mutableMapOf<Long, DashboardWorkState>()
    private var guidedTasks: List<Task> = emptyList()
    private var refreshGeneration = 0
    private var networkState: HomeNetworkAwareness.State? = null
    private var protectedMemories = 0L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        if (!AppMode.isGuidedSetupComplete(this)) {
            startActivity(Intent(this, GuidedSetupActivity::class.java))
            finish()
            return
        }
        setContentView(R.layout.activity_home)
        manager = GuidedBackupManager(this)
        if (!manager.isGuidedConfigurationValid()) {
            AppMode.clearGuidedConfiguration(this)
            startActivity(Intent(this, GuidedSetupActivity::class.java))
            finish()
            return
        }

        val toolbar = findViewById<MaterialToolbar>(R.id.home_toolbar)
        toolbar.title = getString(R.string.app_short_name)
        toolbar.setOnMenuItemClickListener { item ->
            if (item.itemId == R.id.action_simple_settings) {
                startActivity(Intent(this, SimpleSettingsActivity::class.java))
                true
            } else {
                false
            }
        }

        statusIcon = findViewById(R.id.home_status_icon)
        statusValue = findViewById(R.id.home_status_value)
        statusDetail = findViewById(R.id.home_status_detail)
        lastValue = findViewById(R.id.home_last_value)
        nextValue = findViewById(R.id.home_next_value)
        filesValue = findViewById(R.id.home_files_value)
        bytesValue = findViewById(R.id.home_bytes_value)
        refreshProgress = findViewById(R.id.home_refresh_progress)
        guidedContainer = findViewById(R.id.home_guided_container)
        customSection = findViewById(R.id.home_custom_section)
        customContainer = findViewById(R.id.home_custom_container)
        memoryMoment = findViewById(R.id.home_memory_moment)

        findViewById<MaterialButton>(R.id.home_backup_button).setOnClickListener {
            if (manager.backupNow()) {
                Toast.makeText(this, R.string.home_backup_queued, Toast.LENGTH_SHORT).show()
            }
        }
        findViewById<MaterialButton>(R.id.home_verify_button).setOnClickListener {
            startActivity(Intent(this, GuidedVerifyActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.home_photo_status_button).setOnClickListener {
            startActivity(Intent(this, PhotoStatusActivity::class.java))
        }
    }

    override fun onResume() {
        super.onResume()
        refreshDashboard()
    }

    private fun refreshDashboard() {
        guidedTasks = manager.getGuidedTasks()
        if (guidedTasks.isEmpty()) {
            AppMode.clearGuidedConfiguration(this)
            startActivity(Intent(this, GuidedSetupActivity::class.java))
            finish()
            return
        }
        renderTasks()
        observeWork()
        updateSafetyStatus()
        updateTimes()
        refreshStorageStats()
        refreshNetworkState()
        BackupStatusWidget.updateAll(this)
    }

    private fun observeWork() {
        guidedTasks.filterNot { it.id in observedTaskIds }.forEach { task ->
            observedTaskIds += task.id
            WorkManager.getInstance(this)
                .getWorkInfosForUniqueWorkLiveData(SyncManager.transferWorkName(task.id))
                .observe(this) { workInfos ->
                    workStates[task.id] = when {
                        workInfos.any { it.state == WorkInfo.State.RUNNING } -> DashboardWorkState.RUNNING
                        workInfos.any {
                            it.state == WorkInfo.State.ENQUEUED || it.state == WorkInfo.State.BLOCKED
                        } -> DashboardWorkState.QUEUED
                        else -> DashboardWorkState.IDLE
                    }
                    updateSafetyStatus()
                }
        }
    }

    private fun updateSafetyStatus() {
        val states = guidedTasks.map { workStates[it.id] ?: DashboardWorkState.IDLE }
        val statuses = guidedTasks.mapNotNull { GuidedBackupStatusStore.get(this, it.id) }
        when {
            DashboardWorkState.RUNNING in states -> setSafety(
                R.drawable.ic_cloud_download_black_24dp,
                R.string.home_status_backing_up,
                R.string.home_detail_running
            )
            networkState == HomeNetworkAwareness.State.ROUTE_UNAVAILABLE -> setSafety(
                R.drawable.ic_info_outline,
                R.string.home_network_route_missing,
                R.string.home_network_route_missing_detail
            )
            networkState == HomeNetworkAwareness.State.NAS_UNAVAILABLE -> setSafety(
                R.drawable.ic_info_outline,
                R.string.home_network_nas_unavailable,
                R.string.home_network_nas_unavailable_detail
            )
            networkState == HomeNetworkAwareness.State.AWAY ||
                    networkState == HomeNetworkAwareness.State.OFFLINE -> setSafety(
                R.drawable.ic_info_outline,
                R.string.home_network_away,
                R.string.home_network_away_detail
            )
            DashboardWorkState.QUEUED in states -> setSafety(
                R.drawable.ic_info_outline,
                R.string.home_status_queued,
                R.string.home_detail_queued
            )
            statuses.size < guidedTasks.size -> setSafety(
                R.drawable.ic_info_outline,
                R.string.home_status_never,
                R.string.home_detail_never
            )
            statuses.all { it.outcome == GuidedBackupStatusStore.OUTCOME_SUCCESS } -> setSafety(
                R.drawable.ic_check,
                R.string.home_status_safe,
                R.string.home_detail_safe
            )
            else -> setSafety(
                R.drawable.ic_info_outline,
                R.string.home_status_attention,
                R.string.home_detail_attention
            )
        }
        updateMemoryMoment()
    }

    private fun setSafety(icon: Int, title: Int, detail: Int) {
        statusIcon.setImageResource(icon)
        statusValue.setText(title)
        statusDetail.setText(detail)
    }

    private fun updateTimes() {
        val latest = guidedTasks.mapNotNull { GuidedBackupStatusStore.get(this, it.id) }
            .maxByOrNull(GuidedBackupStatusStore.TaskStatus::completedAt)
        lastValue.text = latest?.let { relativeTime(it.completedAt) } ?: getString(R.string.home_never)
        nextValue.text = manager.getNextRunTime()?.let(::relativeTime)
            ?: getString(R.string.home_not_scheduled)
    }

    private fun relativeTime(timeMillis: Long): CharSequence {
        return DateUtils.getRelativeDateTimeString(
            this,
            timeMillis,
            DateUtils.MINUTE_IN_MILLIS,
            DateUtils.WEEK_IN_MILLIS,
            DateUtils.FORMAT_ABBREV_RELATIVE
        )
    }

    private fun refreshStorageStats() {
        val generation = ++refreshGeneration
        refreshProgress.visibility = View.VISIBLE
        filesValue.setText(R.string.home_calculating)
        bytesValue.setText(R.string.home_calculating)
        executor.execute {
            val stats = manager.getStorageStats()
            runOnUiThread {
                if (isFinishing || isDestroyed || generation != refreshGeneration) return@runOnUiThread
                refreshProgress.visibility = View.GONE
                if (stats.complete) {
                    filesValue.text = NumberFormat.getIntegerInstance().format(stats.files)
                    bytesValue.text = Formatter.formatFileSize(this, stats.bytes)
                    protectedMemories = stats.memories
                } else {
                    filesValue.setText(R.string.home_unavailable)
                    bytesValue.setText(R.string.home_unavailable)
                }
                updateMemoryMoment()
            }
        }
    }

    private fun refreshNetworkState() {
        val summary = manager.getConnectionSummary() ?: return
        executor.execute {
            val result = HomeNetworkAwareness.check(this, summary.host, summary.port)
            runOnUiThread {
                if (isFinishing || isDestroyed) return@runOnUiThread
                networkState = result.state
                updateSafetyStatus()
            }
        }
    }

    private fun updateMemoryMoment() {
        val statuses = guidedTasks.mapNotNull { GuidedBackupStatusStore.get(this, it.id) }
        val activeWork = workStates.values.any {
            it == DashboardWorkState.RUNNING || it == DashboardWorkState.QUEUED
        }
        val show = protectedMemories > 0 && statuses.size == guidedTasks.size &&
                statuses.all { it.outcome == GuidedBackupStatusStore.OUTCOME_SUCCESS } && !activeWork
        memoryMoment.visibility = if (show) View.VISIBLE else View.GONE
        if (show) {
            val quantity = protectedMemories.coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
            memoryMoment.text = resources.getQuantityString(
                R.plurals.home_memories_protected,
                quantity,
                quantity
            )
        }
    }

    private fun renderTasks() {
        guidedContainer.removeAllViews()
        guidedTasks.forEach { task ->
            guidedContainer.addView(createTaskRow(task, false, guidedContainer))
        }
        val customTasks = manager.getCustomTasks()
        val customVisibility = if (customTasks.isEmpty()) View.GONE else View.VISIBLE
        customSection.visibility = customVisibility
        customContainer.visibility = customVisibility
        customContainer.removeAllViews()
        customTasks.forEach { task -> customContainer.addView(createTaskRow(task, true, customContainer)) }
    }

    private fun createTaskRow(task: Task, custom: Boolean, parent: LinearLayout): View {
        return LayoutInflater.from(this).inflate(R.layout.item_simple_backup, parent, false).apply {
            findViewById<TextView>(R.id.simple_backup_title).text = task.title
            findViewById<TextView>(R.id.simple_backup_summary).text = if (custom) {
                getString(R.string.home_custom_backup_summary)
            } else {
                getString(R.string.home_guided_task_summary, task.remotePath)
            }
            findViewById<ImageView>(R.id.simple_backup_icon).setImageResource(
                if (custom) R.drawable.ic_settings else R.drawable.ic_check
            )
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private enum class DashboardWorkState {
        IDLE,
        QUEUED,
        RUNNING
    }
}
