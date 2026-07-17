package ca.pkay.rcloneexplorer.Activities

import android.content.Intent
import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.guided.GuidedBackupManager
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.util.AppMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton
import com.google.android.material.materialswitch.MaterialSwitch

class SimpleSettingsActivity : AppCompatActivity() {

    private lateinit var manager: GuidedBackupManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_simple_settings)
        manager = GuidedBackupManager(this)

        findViewById<MaterialToolbar>(R.id.simple_settings_toolbar).apply {
            setNavigationIcon(R.drawable.ic_arrow_back)
            setNavigationOnClickListener { finish() }
        }

        findViewById<MaterialSwitch>(R.id.simple_auto_backup).apply {
            isChecked = manager.isAutoBackupEnabled()
            setOnCheckedChangeListener { _, checked -> manager.setAutoBackupEnabled(checked) }
        }
        findViewById<MaterialSwitch>(R.id.simple_wifi_only).apply {
            isChecked = manager.isWifiOnly()
            setOnCheckedChangeListener { _, checked -> manager.setWifiOnly(checked) }
        }
        findViewById<MaterialButton>(R.id.simple_edit_categories).setOnClickListener {
            openGuidedMode(GuidedSetupActivity.MODE_CATEGORIES)
        }
        findViewById<MaterialButton>(R.id.simple_storage).setOnClickListener {
            openGuidedMode(GuidedSetupActivity.MODE_CONNECTION)
        }
        findViewById<MaterialButton>(R.id.simple_restore).setOnClickListener {
            startActivity(Intent(this, GuidedRestoreActivity::class.java))
        }
        findViewById<MaterialButton>(R.id.simple_advanced).setOnClickListener {
            AppMode.setMode(this, AppMode.MODE_ADVANCED)
            val intent = Intent(this, MainActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
        findViewById<TextView>(R.id.simple_storage_summary).text = manager.getConnectionSummary()?.let {
            getString(
                R.string.simple_storage_device_summary,
                it.host,
                it.port,
                it.share,
                manager.getDeviceName()
            )
        } ?: getString(R.string.simple_storage_unknown)
    }

    private fun openGuidedMode(mode: String) {
        startActivity(
            Intent(this, GuidedSetupActivity::class.java)
                .putExtra(GuidedSetupActivity.EXTRA_MODE, mode)
        )
    }
}