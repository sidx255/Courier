package ca.pkay.rcloneexplorer.Activities

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import ca.pkay.rcloneexplorer.R
import ca.pkay.rcloneexplorer.util.ActivityHelper
import ca.pkay.rcloneexplorer.util.AppMode
import com.google.android.material.appbar.MaterialToolbar
import com.google.android.material.button.MaterialButton

class HomeActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ActivityHelper.applyTheme(this)
        setContentView(R.layout.activity_home)

        val toolbar = findViewById<MaterialToolbar>(R.id.home_toolbar)
        toolbar.title = getString(R.string.app_short_name)
        setSupportActionBar(toolbar)

        findViewById<MaterialButton>(R.id.home_advanced_button).setOnClickListener {
            switchToAdvanced()
        }
    }

    private fun switchToAdvanced() {
        AppMode.setMode(this, AppMode.MODE_ADVANCED)
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}
