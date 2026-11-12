package my.github.MrxSiN.pixellauncherevolved.glance

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.os.Bundle

/** Shows the system Calendar permission dialog for this app and closes; it draws nothing itself. */
class CalendarAccessActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        if (checkSelfPermission(Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED) finish()
        else requestPermissions(arrayOf(Manifest.permission.READ_CALENDAR), 0)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) = finish()
}
