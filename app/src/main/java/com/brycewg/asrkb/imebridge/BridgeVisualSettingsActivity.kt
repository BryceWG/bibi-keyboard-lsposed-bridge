/*
 * Settings host for bridge capture-strip visuals and display language.
 *
 * Module: lsposed-ime-bridge
 */
package com.brycewg.asrkb.imebridge

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge

class BridgeVisualSettingsActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(BridgeLocaleHelper.wrap(newBase))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BridgeVisualSettingsApp(activity = this)
        }
    }
}
