package xyz.mpv.rex.di

import xyz.mpv.rex.domain.anime4k.Anime4KManager
import xyz.mpv.rex.domain.hdr.HdrToysManager
import xyz.mpv.rex.repository.wyzie.WyzieSearchRepository
import xyz.mpv.rex.ui.player.managers.PlaybackManager
import xyz.mpv.rex.ui.player.HeadlessPlaybackController
import okhttp3.OkHttpClient
import org.koin.dsl.module
import org.koin.android.ext.koin.androidContext
import java.util.concurrent.TimeUnit

import xyz.mpv.rex.ui.browser.miniplayer.MiniPlayerStateManager

val domainModule = module {
    single {
        OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
    single { Anime4KManager(androidContext()) }
    single { HdrToysManager(androidContext()) }
    single { WyzieSearchRepository(androidContext(), get(), get(), get()) }
    single { PlaybackManager(get()) }
    single { xyz.mpv.rex.ui.browser.miniplayer.settings.MusicPreferences(get()) }
    single {
        xyz.mpv.rex.ui.browser.miniplayer.settings.MusicAdvancedController(
            xyz.mpv.rex.ui.browser.miniplayer.settings.MpvMusicEngine(), get<xyz.mpv.rex.ui.browser.miniplayer.settings.MusicPreferences>(),
            kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.Main.immediate),
            android.os.SystemClock::elapsedRealtime,
        )
    }
    single {
        androidx.room.Room.databaseBuilder(androidContext(),
            xyz.mpv.rex.ui.browser.miniplayer.settings.MusicBookmarksDatabase::class.java,
            "biscuit_music_bookmarks.db").build()
    }
    single { get<xyz.mpv.rex.ui.browser.miniplayer.settings.MusicBookmarksDatabase>().bookmarks() }
    single { MiniPlayerStateManager() }
    single { HeadlessPlaybackController(androidContext()) }
    single { xyz.mpv.rex.jellyfin.api.JellyfinApi(get(), get(), get()) }
    single { xyz.mpv.rex.jellyfin.remote.JellyfinRemoteClient(get(), get(), get(), androidContext()) }
    single { xyz.mpv.rex.domain.ytdl.YtDlClient(androidContext(), get()) }
}


