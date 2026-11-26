package app.youplay.data.notify

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

/** Periodic background check of the subscriptions feed (see [NewVideoNotifier]). */
class NewVideoWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface Deps { fun notifier(): NewVideoNotifier }

    override suspend fun doWork(): Result = try {
        EntryPointAccessors.fromApplication(applicationContext, Deps::class.java).notifier().check()
        Result.success()
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Throwable) {
        if (runAttemptCount < 2) Result.retry() else Result.success()
    }

    companion object {
        private const val NAME = "new_video_check"

        fun schedule(context: Context, enabled: Boolean) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) { wm.cancelUniqueWork(NAME); return }
            val req = PeriodicWorkRequestBuilder<NewVideoWorker>(30, TimeUnit.MINUTES)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()
            wm.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}
