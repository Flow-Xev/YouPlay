package app.youplay.di

import android.content.Context
import androidx.room.Room
import app.youplay.data.local.YouPlayDatabase
import com.metrolist.innertubex.InnerTube
import com.metrolist.innertubex.cipher.PlayerConfigRepository
import com.metrolist.innertubex.cipher.RemotePlayerConfigStore
import com.metrolist.innertubex.cipher.YouTubeCipherService
import com.metrolist.innertubex.extraction.InnerTubeExtractor
import com.metrolist.innertubex.extraction.YtConfigParserImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.logging.LogLevel
import io.ktor.client.plugins.logging.Logging
import io.ktor.serialization.kotlinx.json.json
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
        explicitNulls = false
        encodeDefaults = true
    }

    @Provides
    @Singleton
    fun provideHttpClient(json: Json): HttpClient = HttpClient(OkHttp) {
        expectSuccess = false
        install(HttpTimeout) {
            requestTimeoutMillis = 45_000
            connectTimeoutMillis = 20_000
            socketTimeoutMillis = 45_000
        }
        install(ContentNegotiation) {
            json(json)
        }
        install(Logging) {
            level = LogLevel.INFO
        }
    }

    @Provides
    @Singleton
    fun provideInnerTube(httpClient: HttpClient): InnerTube = InnerTube(httpClient = httpClient)

    @Provides
    @Singleton
    fun provideInnerTubeExtractor(innerTube: InnerTube, httpClient: HttpClient): InnerTubeExtractor {
        val configStore = RemotePlayerConfigStore(
            httpClient = httpClient,
            repository = PlayerConfigRepository.disabled(),
        )
        val cipher = YouTubeCipherService(httpClient, configStore)
        return InnerTubeExtractor(
            configParser = YtConfigParserImpl(httpClient, innerTube, configStore, cipherService = cipher),
            cipherService = cipher,
            innerTube = innerTube,
        )
    }

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): YouPlayDatabase =
        Room.databaseBuilder(context, YouPlayDatabase::class.java, "youplay.db")
            .addMigrations(app.youplay.data.local.MIGRATION_1_2, app.youplay.data.local.MIGRATION_2_3, app.youplay.data.local.MIGRATION_3_4)
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideWatchHistoryDao(db: YouPlayDatabase) = db.watchHistoryDao()
    @Provides fun provideLikedVideoDao(db: YouPlayDatabase) = db.likedVideoDao()
    @Provides fun provideSubscriptionDao(db: YouPlayDatabase) = db.subscriptionDao()
    @Provides fun provideSearchHistoryDao(db: YouPlayDatabase) = db.searchHistoryDao()
    @Provides fun provideDownloadDao(db: YouPlayDatabase) = db.downloadDao()
    @Provides fun providePendingActionDao(db: YouPlayDatabase) = db.pendingActionDao()
    @Provides fun provideDislikedVideoDao(db: YouPlayDatabase) = db.dislikedVideoDao()
    @Provides fun provideSavedVideoDao(db: YouPlayDatabase) = db.savedVideoDao()
}
