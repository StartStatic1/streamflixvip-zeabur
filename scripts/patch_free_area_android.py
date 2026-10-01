#!/usr/bin/env python3
"""Area Free no app: is_free, series locked, Home row, FreeCatalogApi."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1]

def patch(path, replacements, label):
    p = ROOT / path
    if not p.exists():
        print('skip missing', path)
        return
    t = p.read_text(encoding='utf-8')
    for old, new, name in replacements:
        if new.strip() in t and name:
            print('ok', label, name)
            continue
        if old not in t:
            print('aviso', label, name)
            continue
        t = t.replace(old, new, 1)
        print('aplicou', label, name)
    p.write_text(t, encoding='utf-8')

# VipTitleConfig + requiresVip
sa = ROOT / 'android/app/src/main/java/com/streamflixvip/app/network/SupabaseApi.kt'
if sa.exists():
    t = sa.read_text(encoding='utf-8')
    if 'is_free: Boolean?' not in t:
        t = t.replace(
            'data class VipTitleConfig(\n    val vip_lock: Boolean? = null,\n    val vip_free_episode_limit: Int? = null,\n)',
            'data class VipTitleConfig(\n    val vip_lock: Boolean? = null,\n    val vip_free_episode_limit: Int? = null,\n    val is_free: Boolean? = null,\n)',
        )
        print('VipTitleConfig is_free')
    t = t.replace('select: String = "vip_lock,vip_free_episode_limit"', 'select: String = "vip_lock,vip_free_episode_limit,is_free"')
    old_r = '''fun requiresVip(config: VipTitleConfig?, episodeNumber: Int?): Boolean {
    if (config == null) return false
    if (config.vip_lock == true) return true
    val limit = config.vip_free_episode_limit
    if (limit != null && episodeNumber != null) {
        return episodeNumber > limit
    }
    return false
}'''
    new_r = '''fun requiresVip(config: VipTitleConfig?, episodeNumber: Int?): Boolean {
    if (config?.is_free == true) return false
    if (config == null) return true
    if (config.vip_lock == true) return true
    val limit = config.vip_free_episode_limit
    if (limit != null && episodeNumber != null) {
        return episodeNumber > limit
    }
    return true
}'''
    if old_r in t:
        t = t.replace(old_r, new_r)
        print('requiresVip Area Free')
    elif 'config?.is_free == true' in t:
        print('requiresVip already')
    sa.write_text(t, encoding='utf-8')

# DetailViewModel locks
vm = ROOT / 'android/app/src/main/java/com/streamflixvip/app/ui/detail/DetailViewModel.kt'
if vm.exists():
    t = vm.read_text(encoding='utf-8')
    old = 'fun movieIsLocked(isVip: Boolean): Boolean = !isVip && requiresVip(vipConfig, episodeNumber = null)\n        fun episodeIsLocked(episodeNumber: Int, isVip: Boolean): Boolean = !isVip && requiresVip(vipConfig, episodeNumber)'
    new = 'fun movieIsLocked(isVip: Boolean): Boolean = !isVip && vipConfig?.is_free != true\n        /** Series: free sempre bloqueado (Area Free so filmes). */\n        fun episodeIsLocked(episodeNumber: Int, isVip: Boolean): Boolean = !isVip'
    if old in t:
        t = t.replace(old, new)
        print('DetailViewModel locks')
    elif 'vipConfig?.is_free != true' in t:
        print('DetailViewModel already')
    vm.write_text(t, encoding='utf-8')

# NetworkModule freeCatalogApi
nm = ROOT / 'android/app/src/main/java/com/streamflixvip/app/network/NetworkModule.kt'
if nm.exists() and 'freeCatalogApi' not in nm.read_text(encoding='utf-8'):
    t = nm.read_text(encoding='utf-8')
    needle = '''    val mediaSourcesApi: MediaSourcesApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(MediaSourcesApi::class.java)
    }
'''
    insert = needle + '''
    val freeCatalogApi: FreeCatalogApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(FreeCatalogApi::class.java)
    }
'''
    if needle in t:
        t = t.replace(needle, insert, 1)
        nm.write_text(t, encoding='utf-8')
        print('NetworkModule freeCatalogApi')
    else:
        print('aviso NetworkModule needle')
elif nm.exists():
    print('NetworkModule already')

# CatalogRepository
cr = ROOT / 'android/app/src/main/java/com/streamflixvip/app/data/CatalogRepository.kt'
if cr.exists():
    t = cr.read_text(encoding='utf-8')
    if 'getFreeCatalog' not in t:
        last = t.rfind('\n}')
        method = '''

    /** Area Free — filmes is_free no painel. */
    suspend fun getFreeCatalog(limit: Int = 50): List<TmdbItem> {
        return try {
            NetworkModule.freeCatalogApi.getFreeCatalog(limit).items
        } catch (_: Exception) {
            emptyList()
        }
    }
'''
        t = t[:last] + method + t[last:]
        if 'import com.streamflixvip.app.network.TmdbItem' not in t:
            t = t.replace(
                'package com.streamflixvip.app.data\n',
                'package com.streamflixvip.app.data\n\nimport com.streamflixvip.app.network.TmdbItem\n',
            )
        cr.write_text(t, encoding='utf-8')
        print('CatalogRepository getFreeCatalog')
    else:
        print('CatalogRepository already')

# HomeViewModel
hv = ROOT / 'android/app/src/main/java/com/streamflixvip/app/ui/home/HomeViewModel.kt'
if hv.exists():
    t = hv.read_text(encoding='utf-8')
    if 'freeArea' not in t:
        t = t.replace(
            '''val trash = runCatching {
                    repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = 27, year = trashYear)
                }.getOrElse { emptyList() }

                prefetchContinueSources''',
            '''val trash = runCatching {
                    repository.exploreCatalog(category = GenreCategory.MOVIES, genreId = 27, year = trashYear)
                }.getOrElse { emptyList() }

                val freeArea = runCatching {
                    repository.getFreeCatalog(50)
                }.getOrElse { emptyList() }

                prefetchContinueSources''',
        )
        t = t.replace(
            '''rows = listOfNotNull(
                        trending.takeIf { it.isNotEmpty() }?.let {
                            HomeRow("Top 10 da Semana", it.take(10), "movie", isRanked = true)
                        },''',
            '''rows = listOfNotNull(
                        freeArea.takeIf { it.isNotEmpty() }?.let {
                            HomeRow("Area Free", it, "movie")
                        },
                        trending.takeIf { it.isNotEmpty() }?.let {
                            HomeRow("Top 10 da Semana", it.take(10), "movie", isRanked = true)
                        },''',
        )
        hv.write_text(t, encoding='utf-8')
        print('HomeViewModel Area Free')
    else:
        print('HomeViewModel already')

print('done patch_free_area_android')
