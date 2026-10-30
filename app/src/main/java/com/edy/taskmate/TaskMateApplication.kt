package com.edy.taskmate

import android.app.Application
import com.edy.taskmate.alarm.Notifications

class TaskMateApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        Notifications.ensureChannel(this)
    }
}