package com.bowe.localledger

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.Surface
import com.bowe.localledger.ui.LocalLedgerAppRoot
import com.bowe.localledger.ui.theme.LocalLedgerTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LocalLedgerTheme {
                Surface {
                    LocalLedgerAppRoot()
                }
            }
        }
    }
}
