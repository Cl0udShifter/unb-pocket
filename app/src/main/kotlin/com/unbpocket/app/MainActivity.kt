package com.unbpocket.app

import android.os.Bundle
import androidx.activity.ComponentActivity

/**
 * Activity hospedeira minima. A camada de UI sera acoplada a `UnbPocketCore` posteriormente.
 */
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        (application as UnbPocketApplication).core
    }
}
