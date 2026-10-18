package com.beluga.e2ee

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.beluga.e2ee.storage.DatabaseRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Beluga Application class.
 *
 * Configures process-lifecycle auto-lock (resolving Issue #12) to protect sensitive keys in memory
 * when the app remains backgrounded past the auto-lock timeout.
 */
class BelugaApplication : Application(), DefaultLifecycleObserver {

    val databaseRepository: DatabaseRepository by lazy {
        DatabaseRepository(this)
    }

    private val applicationScope = CoroutineScope(Dispatchers.Default + Job())
    private var lockJob: Job? = null

    companion object {
        const val AUTO_LOCK_DELAY_MS = 2 * 60 * 1000L // 2 minutes auto-lock
    }

    override fun onCreate() {
        super<Application>.onCreate()
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStop(owner: LifecycleOwner) {
        // App entered background: schedule auto-lock
        lockJob?.cancel()
        lockJob = applicationScope.launch {
            delay(AUTO_LOCK_DELAY_MS)
            databaseRepository.lock()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        // App returned to foreground: cancel pending auto-lock timer
        lockJob?.cancel()
        lockJob = null
    }
}
