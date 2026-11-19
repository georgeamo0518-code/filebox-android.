package dev.yourname.filebox

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import coil3.video.VideoFrameDecoder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class FileboxApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.appScope.launch {
            container.trash.purgeExpired(container.settings.settings.first().binRetentionDays)
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components { add(VideoFrameDecoder.Factory()) }
            .crossfade(100) // Motion.FAST
            .build()
}
