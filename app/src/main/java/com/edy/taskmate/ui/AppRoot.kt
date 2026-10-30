package com.edy.taskmate.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import com.edy.taskmate.TaskViewModel
import com.edy.taskmate.data.Dates
import com.edy.taskmate.data.Task

@Composable
fun AppRoot(vm: TaskViewModel) {
    var tab by rememberSaveable { mutableStateOf(0) }
    var quickAdd by remember { mutableStateOf(false) }
    var quickAddDate by remember { mutableStateOf<Long?>(null) }
    var editingTask by remember { mutableStateOf<Task?>(null) }
    val snackbar = remember { SnackbarHostState() }

    LaunchedEffect(vm.lastDeleted?.id) {
        val deleted = vm.lastDeleted ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(
            message = "已删除「${deleted.title}」",
            actionLabel = "撤销",
            withDismissAction = true,
            duration = SnackbarDuration.Short
        )
        if (result == SnackbarResult.ActionPerformed) vm.undoDelete() else vm.dismissUndo()
    }

    LaunchedEffect(vm.pendingOpenTaskId) {
        val id = vm.pendingOpenTaskId ?: return@LaunchedEffect
        vm.tasks.firstOrNull { it.id == id }?.let {
            tab = 0
            editingTask = it
        }
        vm.consumePendingOpenTask()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        bottomBar = {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                NavigationBarItem(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    icon = { Icon(Icons.Filled.Home, contentDescription = null) },
                    label = { Text("今天") }
                )
                NavigationBarItem(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    icon = { Icon(Icons.Filled.DateRange, contentDescription = null) },
                    label = { Text("本周") }
                )
                NavigationBarItem(
                    selected = tab == 2,
                    onClick = { tab = 2 },
                    icon = { Icon(Icons.Filled.List, contentDescription = null) },
                    label = { Text("全部") }
                )
                NavigationBarItem(
                    selected = tab == 3,
                    onClick = { tab = 3 },
                    icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
                    label = { Text("设置") }
                )
            }
        }
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding)) {
            when (tab) {
                0 -> TodayScreen(
                    vm = vm,
                    onAdd = {
                        quickAddDate = Dates.defaultDue()
                        quickAdd = true
                    },
                    onEdit = { editingTask = it }
                )

                1 -> WeekScreen(
                    vm = vm,
                    onAdd = { day ->
                        quickAddDate = day
                        quickAdd = true
                    },
                    onEdit = { editingTask = it }
                )

                2 -> AllScreen(
                    vm = vm,
                    onAdd = {
                        quickAddDate = null
                        quickAdd = true
                    },
                    onEdit = { editingTask = it }
                )

                else -> SettingsScreen(vm = vm)
            }
        }
    }

    if (quickAdd) {
        QuickAddSheet(
            initialDate = quickAddDate,
            onDismiss = { quickAdd = false },
            onSave = { title, notes, dueAt, remindAt, priority, repeat ->
                vm.addTask(title, notes, dueAt, remindAt, priority, repeat)
                quickAdd = false
            }
        )
    }

    val task = editingTask
    if (task != null) {
        TaskDetailSheet(
            task = task,
            onDismiss = { editingTask = null },
            onSave = { updated ->
                vm.updateTask(updated)
                editingTask = null
            },
            onDelete = {
                vm.deleteTask(it)
                editingTask = null
            },
            onToggleDone = {
                vm.toggleDone(it)
                editingTask = null
            },
            onPostpone = {
                vm.postponeToTomorrow(it)
                editingTask = null
            }
        )
    }
}