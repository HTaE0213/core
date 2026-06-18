package com.maxrave.data.helper

import com.maxrave.kotlinytmusicscraper.YouTube
import com.maxrave.kotlinytmusicscraper.models.YouTubeLocale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import com.maxrave.logger.Logger
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

object MetadataLanguageHelper : KoinComponent {
    private val youTube: YouTube by inject()
    private const val TAG = "MetadataLangHelper"
    private val cacheMutex = Mutex()
    private val _resolvedSongs = MutableStateFlow<Map<String, ResolvedSong>>(emptyMap())
    val resolvedSongs = _resolvedSongs.asStateFlow()

    private val _resolvedArtists = MutableStateFlow<Map<String, String>>(emptyMap())
    val resolvedArtists = _resolvedArtists.asStateFlow()

    // 解決中の重複実行を防ぐセット
    private val resolvingSongs = mutableSetOf<String>()
    private val resolvingArtists = mutableSetOf<String>()

    private val enLocale = YouTubeLocale(gl = "US", hl = "en")

    data class ResolvedSong(
        val title: String,
        val artist: String
    )

    /**
     * ひらがな・漢字を含まず、カタカナが1文字以上含まれる文字列を「自動翻訳されたカタカナ表記」と判定する。
     */
    fun isKatakanaTranslation(text: String): Boolean {
        val hasHiragana = text.any { it in '\u3040'..'\u309F' }
        val hasKanji = text.any { it in '\u4E00'..'\u9FFF' || it in '\uF900'..'\uFAFF' }
        val hasKatakana = text.any { it in '\u30A0'..'\u30FF' || it in '\uFF65'..'\uFF9F' }

        return !hasHiragana && !hasKanji && hasKatakana
    }

    fun shouldResolveSongMetadata(
        title: String,
        artist: String,
    ): Boolean = isKatakanaTranslation(title) && isKatakanaTranslation(artist)

    /**
     * 曲の英語（オリジナル）タイトルおよびアーティスト名を取得・解決する（非同期スレッドで実行）。
     */
    fun resolveSongMetadata(
        scope: CoroutineScope,
        videoId: String,
        currentTitle: String,
        currentArtist: String
    ) {
        if (!shouldResolveSongMetadata(currentTitle, currentArtist)) {
            return
        }

        if (_resolvedSongs.value.containsKey(videoId)) {
            return
        }

        scope.launch(Dispatchers.Default) {
            val shouldStart = cacheMutex.withLock {
                if (_resolvedSongs.value.containsKey(videoId) || resolvingSongs.contains(videoId)) {
                    false
                } else {
                    resolvingSongs.add(videoId)
                    true
                }
            }
            if (!shouldStart) return@launch

            try {
                Logger.d(TAG, "Resolving song metadata for $videoId ($currentTitle - $currentArtist) in English...")
                val result = youTube.player(videoId = videoId, customLocale = enLocale)
                result.onSuccess { triple ->
                    val response = triple.second
                    val resolvedTitle = response.videoDetails?.title ?: currentTitle
                    val resolvedArtist = response.videoDetails?.author ?: currentArtist

                    cacheMutex.withLock {
                        _resolvedSongs.value = _resolvedSongs.value + (videoId to ResolvedSong(resolvedTitle, resolvedArtist))
                        resolvingSongs.remove(videoId)
                    }
                    Logger.d(TAG, "Resolved metadata successfully: $resolvedTitle - $resolvedArtist")
                }.onFailure { e ->
                    Logger.e(TAG, "Failed to resolve song: ${e.message}")
                    cacheMutex.withLock {
                        _resolvedSongs.value = _resolvedSongs.value + (videoId to ResolvedSong(currentTitle, currentArtist))
                        resolvingSongs.remove(videoId)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Exception while resolving song metadata: ${e.message}")
                cacheMutex.withLock {
                    _resolvedSongs.value = _resolvedSongs.value + (videoId to ResolvedSong(currentTitle, currentArtist))
                    resolvingSongs.remove(videoId)
                }
            }
        }
    }

    /**
     * アーティストの英語（オリジナル）名を取得・解決する（非同期スレッドで実行）。
     */
    fun resolveArtistMetadata(
        scope: CoroutineScope,
        artistId: String,
        currentName: String
    ) {
        if (!isKatakanaTranslation(currentName)) {
            return
        }

        if (_resolvedArtists.value.containsKey(artistId)) {
            return
        }

        scope.launch(Dispatchers.Default) {
            val shouldStart = cacheMutex.withLock {
                if (_resolvedArtists.value.containsKey(artistId) || resolvingArtists.contains(artistId)) {
                    false
                } else {
                    resolvingArtists.add(artistId)
                    true
                }
            }
            if (!shouldStart) return@launch

            try {
                Logger.d(TAG, "Resolving artist metadata for $artistId ($currentName) in English...")
                val result = youTube.artist(browseId = artistId, customLocale = enLocale)
                result.onSuccess { page ->
                    val resolvedName = page.artist?.title ?: currentName
                    cacheMutex.withLock {
                        _resolvedArtists.value = _resolvedArtists.value + (artistId to resolvedName)
                        resolvingArtists.remove(artistId)
                    }
                    Logger.d(TAG, "Resolved artist successfully: $resolvedName")
                }.onFailure { e ->
                    Logger.e(TAG, "Failed to resolve artist: ${e.message}")
                    cacheMutex.withLock {
                        _resolvedArtists.value = _resolvedArtists.value + (artistId to currentName)
                        resolvingArtists.remove(artistId)
                    }
                }
            } catch (e: Exception) {
                Logger.e(TAG, "Exception while resolving artist metadata: ${e.message}")
                cacheMutex.withLock {
                    _resolvedArtists.value = _resolvedArtists.value + (artistId to currentName)
                    resolvingArtists.remove(artistId)
                }
            }
        }
    }
}
