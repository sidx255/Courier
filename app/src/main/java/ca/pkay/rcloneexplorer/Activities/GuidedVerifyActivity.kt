package ca.pkay.rcloneexplorer.Activities

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.work.WorkInfo
import androidx.work.WorkManager
import ca.pkay.rcloneexplorer.Items.Task
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.GuidedBackupManager
import ca.pkay.rcloneexplorer.guided.GuidedVerificationStatusStore
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.workmanager.SyncManager
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class GuidedVerifyActivity : AppCompatActivity() {

    private lateinit var manager: GuidedBackupManager
    private lateinit var tasks: List<Task>
    private lateinit var title: TextView
    private lateinit var detail: TextView
    private lateinit var icon: ImageView
    private lateinit var progress: View
    private lateinit var action: MaterialButton
    private lateinit var taskContainer: LinearLayout
    private val workStates = mutableMapOf<Long, WorkInfo.State>()
    private var phase = Phase.VERIFY
    private var phaseStartedAt = 0L
    private var phaseHandled = false
    private var repairTaskIds = emptySet<Long>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_guided_verify)
        manager = GuidedBackupManager(this)
        tasks = manager.getGuidedTasks()
        title = findViewById(R.id.verify_status_title)
        detail = findViewById(R.id.verify_status_detail)
        icon = findViewById(R.id.verify_status_icon)
        progress = findViewById(R.id.verify_progress)
        action = findViewById(R.id.verify_action)
        taskContainer = findViewById(R.id.verify_task_container)

        findViewById<MaterialToolbar>(R.id.verify_toolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }
        tasks.forEach { task -> observeTask(task) }
        action.setOnClickListener {
            when (phase) {
                Phase.VERIFY -> if (repairTaskIds.isEmpty()) startVerify() else startRepair()
                Phase.REPAIR -> startRepair()
            }
        }
        startVerify()
    }

    private fun observeTask(task: Task) {
        WorkManager.getInstance(this)
            .getWorkInfosForUniqueWorkLiveData(SyncManager.verifyWorkName(task.id))
            .observe(this) { infos ->
                val state = infos.lastOrNull()?.state ?: return@observe
                workStates[task.id] = state
                renderTaskRows()
                evaluatePhase()
            }
    }

    private fun startVerify() {
        phase = Phase.VERIFY
        phaseStartedAt = System.currentTimeMillis()
        phaseHandled = false
        repairTaskIds = emptySet()
        workStates.clear()
        title.setText(R.string.verify_friendly_running)
        detail.setText(R.string.verify_friendly_description)
        icon.setImageResource(R.drawable.ic_check)
        progress.visibility = View.VISIBLE
        action.visibility = View.GONE
        manager.checkBackupSafety()
        renderTaskRows()
    }

    private fun startRepair() {
        phase = Phase.REPAIR
        phaseStartedAt = System.currentTimeMillis()
        phaseHandled = false
        workStates.clear()
        title.setText(R.string.verify_friendly_repairing)
        detail.setText(R.string.error_plain_differences)
        progress.visibility = View.VISIBLE
        action.visibility = View.GONE
        manager.repairBackupDifferences(repairTaskIds)
        renderTaskRows()
    }

    private fun evaluatePhase() {
        val expectedIds = if (phase == Phase.REPAIR) repairTaskIds else tasks.map(Task::id).toSet()
        if (phaseHandled || expectedIds.isEmpty()) return
        val states = expectedIds.mapNotNull(workStates::get)
        if (states.size != expectedIds.size || states.any { !it.isFinished }) return
        if (phase == Phase.REPAIR) {
            val repairs = expectedIds.mapNotNull { taskId ->
                GuidedVerificationStatusStore.getRepair(this, taskId)
                    ?.takeIf { it.first >= phaseStartedAt }
            }
            if (repairs.size != expectedIds.size) return
            phaseHandled = true
            if (repairs.all { it.second == GuidedVerificationStatusStore.REPAIR_SUCCESS }) {
                startVerify()
            } else {
                showFailure()
            }
            return
        }
        val results = tasks.mapNotNull { task ->
            GuidedVerificationStatusStore.get(this, task.id)?.takeIf { it.completedAt >= phaseStartedAt }
        }
        if (results.size != tasks.size) {
            return
        }
        phaseHandled = true
        val failed = results.any {
            it.outcome == GuidedVerificationStatusStore.OUTCOME_FAILED ||
                    it.outcome == GuidedVerificationStatusStore.OUTCOME_CANCELLED
        }
        if (failed) {
            showFailure()
            return
        }
        repairTaskIds = results.filter { it.differences > 0 }.map { it.taskId }.toSet()
        if (repairTaskIds.isEmpty()) {
            progress.visibility = View.GONE
            icon.setImageResource(R.drawable.ic_check)
            title.setText(R.string.verify_friendly_safe)
            detail.setText(R.string.verify_friendly_safe_detail)
            action.visibility = View.GONE
        } else {
            progress.visibility = View.GONE
            title.text = getString(
                R.string.verify_friendly_differences,
                results.sumOf(GuidedVerificationStatusStore.VerificationStatus::differences)
            )
            detail.setText(R.string.error_plain_differences)
            action.setText(R.string.verify_friendly_repair)
            action.visibility = View.VISIBLE
        }
        renderTaskRows()
    }

    private fun showFailure() {
        progress.visibility = View.GONE
        title.setText(R.string.verify_friendly_failed)
        detail.setText(R.string.error_plain_timeout)
        repairTaskIds = emptySet()
        action.setText(R.string.verify_friendly_retry)
        action.visibility = View.VISIBLE
        renderTaskRows()
    }

    private fun renderTaskRows() {
        taskContainer.removeAllViews()
        tasks.forEach { task ->
            val row = LayoutInflater.from(this).inflate(R.layout.item_simple_backup, taskContainer, false)
            row.findViewById<TextView>(R.id.simple_backup_title).text = task.title
            val verification = GuidedVerificationStatusStore.get(this, task.id)
                ?.takeIf { phase == Phase.VERIFY && it.completedAt >= phaseStartedAt }
            row.findViewById<TextView>(R.id.simple_backup_summary).text = when {
                verification?.outcome == GuidedVerificationStatusStore.OUTCOME_SAFE ->
                    getString(R.string.verify_friendly_task_safe)
                verification?.differences ?: 0 > 0 ->
                    getString(R.string.verify_friendly_task_differences, verification?.differences ?: 0)
                verification != null -> getString(R.string.verify_friendly_task_failed)
                else -> getString(R.string.verify_friendly_task_checking)
            }
            taskContainer.addView(row)
        }
    }

    private enum class Phase {
        VERIFY,
        REPAIR
    }
}