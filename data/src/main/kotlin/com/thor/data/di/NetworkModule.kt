package com.thor.data.di

import android.content.Context
import com.thor.data.metadata.IgdbProvider
import com.thor.data.metadata.MetadataProvider
import com.thor.data.metadata.RawgProvider
import com.thor.data.metadata.ScreenScraperProvider
import com.thor.data.metadata.SteamGridDbProvider
import com.thor.data.metadata.WikidataProvider
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun providesJson(): Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        isLenient = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun providesOkHttpClient(
        @ApplicationContext context: Context,
    ): OkHttpClient = OkHttpClient.Builder()
        // Metadata responses are highly cacheable and a rescan re-requests the
        // same endpoints; an on-disk cache turns a repeat scrape into no traffic.
        .cache(Cache(java.io.File(context.cacheDir, "http"), HTTP_CACHE_BYTES))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .callTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()

    private const val HTTP_CACHE_BYTES = 64L * 1024 * 1024
}

/**
 * Binds the available metadata providers into a set.
 *
 * Adding a provider is a matter of writing the class and adding one `@Binds`
 * here; nothing downstream changes, because the aggregator only ever sees the
 * set.
 */
@Module
@InstallIn(SingletonComponent::class)
interface MetadataProviderModule {

    @Binds
    @IntoSet
    fun bindsSteamGridDb(provider: SteamGridDbProvider): MetadataProvider

    @Binds
    @IntoSet
    fun bindsRawg(provider: RawgProvider): MetadataProvider

    @Binds
    @IntoSet
    fun bindsIgdb(provider: IgdbProvider): MetadataProvider

    @Binds
    @IntoSet
    fun bindsScreenScraper(provider: ScreenScraperProvider): MetadataProvider

    /**
     * Keyless, so it is the only textual provider that contributes on a fresh
     * install -- every other one needs a credential first.
     */
    @Binds
    @IntoSet
    fun bindsWikidata(provider: WikidataProvider): MetadataProvider
}
