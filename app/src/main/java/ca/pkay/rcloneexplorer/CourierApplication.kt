package ca.pkay.rcloneexplorer

import android.app.Application
import com.google.android.material.color.DynamicColors

class CourierApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        DynamicColors.applyToActivitiesIfAvailable(this)
    }
}
