package ca.pkay.rcloneexplorer.Activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.util.AppMode
import com.google.android.material.card.MaterialCardView

class ModeChooserActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_mode_chooser)
        findViewById<MaterialCardView>(R.id.mode_simple_card).setOnClickListener {
            AppMode.setMode(this, AppMode.MODE_SIMPLE)
            launchTarget(HomeActivity::class.java)
        }
        findViewById<MaterialCardView>(R.id.mode_advanced_card).setOnClickListener {
            AppMode.setMode(this, AppMode.MODE_ADVANCED)
            launchTarget(MainActivity::class.java)
        }
    }

    private fun launchTarget(target: Class<*>) {
        val intent = Intent(this, target)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
