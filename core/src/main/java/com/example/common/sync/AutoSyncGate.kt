package com.example.common.sync

/**
 * Otomatik eşitlemenin ne zaman çalışacağına karar verir.
 *
 * Android arka plandaki uygulamanın ağını engelleyebildiği için otomatik eşitleme yalnızca
 * uygulama ekrandayken çalışır. Uygulamaya dönüldüğünde son eşitleme başarısızsa veya
 * eskidiyse yeni eşitleme istenir.
 */
class AutoSyncGate(
    private val minForegroundIntervalMs: Long = DEFAULT_MIN_FOREGROUND_INTERVAL_MS,
    private val clock: () -> Long = System::currentTimeMillis
) {
    private var lastSuccessAt: Long? = null
    private var lastSyncFailed = false
    private var retryAfterCurrent = false

    var isForeground = false
        private set

    /** Uygulama ekrana geldi. Hemen eşitleme gerekiyorsa true döner. */
    fun onForeground(isSyncing: Boolean): Boolean {
        isForeground = true
        if (isSyncing) {
            // Süren eşitleme arka plandayken başlamış olabilir; başarısız biterse yenilenir.
            retryAfterCurrent = true
            return false
        }
        val last = lastSuccessAt
        return lastSyncFailed || last == null || clock() - last >= minForegroundIntervalMs
    }

    fun onBackground() {
        isForeground = false
        retryAfterCurrent = false
    }

    /** Eşitleme bitti. Bir kez daha denenmesi gerekiyorsa true döner. */
    fun onSyncFinished(success: Boolean): Boolean {
        lastSyncFailed = !success
        if (success) lastSuccessAt = clock()
        val retry = retryAfterCurrent && !success && isForeground
        retryAfterCurrent = false
        return retry
    }

    companion object {
        const val DEFAULT_MIN_FOREGROUND_INTERVAL_MS = 60_000L
    }
}
