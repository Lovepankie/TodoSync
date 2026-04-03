package com.todoapp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.todoapp.alarm.AlarmReceiver
import com.todoapp.ui.navigation.TodoNavGraph
import com.todoapp.ui.theme.TodoSyncTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val startItemId = intent.getLongExtra(AlarmReceiver.EXTRA_ITEM_ID, -1L)
        setContent {
            TodoSyncTheme {
                TodoNavGraph(startItemId = startItemId)
            }
        }
    }
}
