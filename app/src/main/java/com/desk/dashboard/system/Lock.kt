package com.desk.dashboard.system

import android.app.admin.DeviceAdminReceiver
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent

/** Device admin with only the force-lock policy (res/xml/device_admin.xml). */
class LockAdmin : DeviceAdminReceiver()

object Lock {
    private fun admin(context: Context) = ComponentName(context, LockAdmin::class.java)

    fun isEnabled(context: Context) =
        context.getSystemService(DevicePolicyManager::class.java).isAdminActive(admin(context))

    /** Locks the screen, or opens the system prompt to enable the admin the first time. */
    fun lockOrAsk(context: Context) {
        if (isEnabled(context)) {
            context.getSystemService(DevicePolicyManager::class.java).lockNow()
        } else {
            context.startActivity(askIntent(context))
        }
    }

    fun askIntent(context: Context): Intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN)
        .putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, admin(context))
        .putExtra(
            DevicePolicyManager.EXTRA_ADD_EXPLANATION,
            "Lets the dashboard's Lock button turn the screen off and lock the tablet. It can't do anything else. " +
                "To uninstall the app later, turn this off first in Settings › Security › Device admin apps.",
        )
}
