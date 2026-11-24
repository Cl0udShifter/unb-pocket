package com.unbpocket.app

import android.app.Application
import com.unbpocket.core.data.UnbPocketCoreFactory
import com.unbpocket.core.model.contract.UnbPocketCore

/**
 * Ponto de inicializacao do aplicativo Android expondo a instancia unica do Core.
 */
class UnbPocketApplication : Application() {
    @Volatile
    private var cachedCore: UnbPocketCore? = null

    val coreOrNull: UnbPocketCore?
        get() = cachedCore

    val core: UnbPocketCore by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
        UnbPocketCoreFactory.create(this).also { created ->
            cachedCore = created
        }
    }
}
