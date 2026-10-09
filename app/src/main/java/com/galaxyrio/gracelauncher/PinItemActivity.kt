package com.galaxyrio.gracelauncher

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import com.galaxyrio.gracelauncher.platform.PinItemViewModel
import com.galaxyrio.gracelauncher.ui.LauncherAppTheme
import com.galaxyrio.gracelauncher.ui.LauncherViewModel

/** Android sends authenticated pin requests to this activity in the default HOME package. */
class PinItemActivity : ComponentActivity() {
    private val model: PinItemViewModel by viewModels()
    private val themeModel: LauncherViewModel by viewModels()

    private val bindWidget = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        model.onBindResult(it.resultCode == Activity.RESULT_OK)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        model.initialize(intent, savedInstanceState?.getInt(STATE_WIDGET_ID, -1) ?: -1)
        setContent {
            LauncherAppTheme(themeModel) {
                BackHandler { if (!model.busy) model.cancel() }
                LaunchedEffect(model.finished) {
                    if (model.finished) {
                        model.error?.let { Toast.makeText(this@PinItemActivity, it, Toast.LENGTH_LONG).show() }
                        if (model.added) startActivity(Intent(this@PinItemActivity, MainActivity::class.java)
                            .setAction(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME))
                        finish()
                    }
                }
                Surface(modifier = Modifier.fillMaxSize()) {
                    Box(Modifier.fillMaxSize()) {
                        if (model.ready && !model.finished) AlertDialog(
                            onDismissRequest = { if (!model.busy) model.cancel() },
                            title = { Text(stringResource(R.string.pin_item_title)) },
                            text = { Text(stringResource(if (model.isWidget) R.string.pin_widget_message
                                else R.string.pin_shortcut_message, model.label)) },
                            confirmButton = {
                                TextButton(enabled = !model.busy,
                                    onClick = { model.confirm { bindIntent ->
                                        try { bindWidget.launch(bindIntent) }
                                        catch (_: Exception) { model.fail(R.string.widget_setup_error) }
                                    } }) { Text(stringResource(R.string.pin_item_add)) }
                            },
                            dismissButton = {
                                TextButton(enabled = !model.busy, onClick = model::cancel) {
                                    Text(stringResource(R.string.cancel))
                                }
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt(STATE_WIDGET_ID, model.pendingWidgetId)
        super.onSaveInstanceState(outState)
    }

    companion object {
        private const val STATE_WIDGET_ID = "pinWidgetId"
    }
}