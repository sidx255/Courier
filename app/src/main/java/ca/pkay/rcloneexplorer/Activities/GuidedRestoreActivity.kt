package ca.pkay.rcloneexplorer.Activities

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import ca.pkay.rcloneexplorer.FilePicker
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.GuidedBackupManager
import ca.pkay.rcloneexplorer.util.ActivityHelper
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox

class GuidedRestoreActivity : AppCompatActivity() {

    private lateinit var manager: GuidedBackupManager
    private val selectedTaskIds = mutableSetOf<Long>()
    private var destinationRoot: String? = null
    private val destinationLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) return@registerForActivityResult
        destinationRoot = result.data?.getStringExtra(FilePicker.FILE_PICKER_RESULT)
        if (destinationRoot.isNullOrBlank() || !manager.isRestoreDestinationWritable(destinationRoot!!)) {
            destinationRoot = null
            Toast.makeText(this, R.string.restore_destination_not_writable, Toast.LENGTH_SHORT).show()
            return@registerForActivityResult
        }
        findViewById<TextView>(R.id.restore_destination_value).text = destinationRoot?.let {
            getString(R.string.restore_destination_value, it)
        }.orEmpty()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_guided_restore)
        manager = GuidedBackupManager(this)

        findViewById<MaterialToolbar>(R.id.restore_toolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }

        val container = findViewById<LinearLayout>(R.id.restore_task_container)
        val chooseDestination = findViewById<MaterialButton>(R.id.restore_choose_destination)
        findViewById<RadioGroup>(R.id.restore_destination_group).setOnCheckedChangeListener { _, checkedId ->
            chooseDestination.isEnabled = checkedId == R.id.restore_chosen_location
        }
        chooseDestination.setOnClickListener {
            destinationLauncher.launch(
                Intent(this, FilePicker::class.java)
                    .putExtra(FilePicker.FILE_PICKER_PICK_DESTINATION_TYPE, true)
            )
        }
        manager.getGuidedTasks().forEach { task ->
            val checkBox = MaterialCheckBox(this).apply {
                text = task.title
                isChecked = true
                minHeight = resources.getDimensionPixelSize(R.dimen.settingsIconSize)
            }
            selectedTaskIds += task.id
            checkBox.setOnCheckedChangeListener { _, checked ->
                if (checked) selectedTaskIds += task.id else selectedTaskIds -= task.id
            }
            container.addView(checkBox)
        }

        findViewById<MaterialButton>(R.id.restore_button).setOnClickListener {
            if (selectedTaskIds.isEmpty()) {
                Toast.makeText(this, R.string.restore_choose_one, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val customDestination = findViewById<RadioGroup>(R.id.restore_destination_group)
                .checkedRadioButtonId == R.id.restore_chosen_location
            if (customDestination && destinationRoot.isNullOrBlank()) {
                Toast.makeText(this, R.string.restore_destination_not_selected, Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            val queued = manager.restore(selectedTaskIds, if (customDestination) destinationRoot else null)
            Toast.makeText(this, getString(R.string.restore_queued, queued), Toast.LENGTH_LONG).show()
            finish()
        }
    }
}