package com.edy.taskmate

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import com.edy.taskmate.alarm.Notifications
import com.edy.taskmate.ui.AppRoot
import com.edy.taskmate.ui.TaskMateTheme

class MainActivity : ComponentActivity() {

    private val viewModel: TaskViewModel by viewModels()

    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* 无论允许与否，App 都能用，只是提醒不会弹 */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Notifications.ensureChannel(this)
        viewModel.handleOpenTask(intent?.getStringExtra("open_task_id"))

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            TaskMateTheme {
                AppRoot(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        viewModel.handleOpenTask(intent.getStringExtra("open_task_id"))
    }

    override fun onResume() {
        super.onResume()
        viewModel.onResume()
    }
}