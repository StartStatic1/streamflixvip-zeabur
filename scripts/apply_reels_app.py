#!/usr/bin/env python3
from pathlib import Path

nm = Path('android/app/src/main/java/com/streamflixvip/app/network/NetworkModule.kt')
nt = nm.read_text()
if 'val reelsApi' not in nt:
    needle = '''    val liveTvApi: LiveTvApi by lazy {'''
    insert = '''    val reelsApi: ReelsApi by lazy {
        Retrofit.Builder()
            .baseUrl(BuildConfig.API_BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(ReelsApi::class.java)
    }

    val liveTvApi: LiveTvApi by lazy {'''
    if needle not in nt:
        raise SystemExit('liveTvApi block missing')
    nt = nt.replace(needle, insert, 1)
    nm.write_text(nt)
    print('network ok')
else:
    print('network ja ok')

ma = Path('android/app/src/main/java/com/streamflixvip/app/MainActivity.kt')
mt = ma.read_text()
if 'import com.streamflixvip.app.ui.reels.ReelsScreen' not in mt:
    mt = mt.replace(
        'import com.streamflixvip.app.ui.livetv.LiveTvScreen',
        'import com.streamflixvip.app.ui.livetv.LiveTvScreen\nimport com.streamflixvip.app.ui.reels.ReelsScreen\nimport com.streamflixvip.app.ui.reels.ReelsViewModel\nimport com.streamflixvip.app.ui.reels.ReelPlayerScreen\nimport com.streamflixvip.app.ui.reels.PendingReel\nimport com.streamflixvip.app.ui.reels.PendingReelSession',
    )
if '"reels"' not in mt.split('showBottomBar')[1][:220]:
    mt = mt.replace(
        'val showBottomBar = currentRoute in listOf("home", "explore", "livetv", "profile")',
        'val showBottomBar = currentRoute in listOf("home", "explore", "reels", "livetv", "profile")',
    )
    mt = mt.replace(
        'val showTopBar = currentRoute in listOf("home", "explore", "livetv", "profile", "mylist", "genres")',
        'val showTopBar = currentRoute in listOf("home", "explore", "reels", "livetv", "profile", "mylist", "genres")',
    )

if 'composable("reels")' not in mt:
    block = '''            composable("reels") {
                val viewModel: ReelsViewModel = viewModel()
                ReelsScreen(
                    viewModel = viewModel,
                    onStoryClick = { story ->
                        if (story.vip_only != false && !VipStatusHolder.isVipNow()) {
                            navController.navigate("profile") { launchSingleTop = true }
                        } else {
                            resumeScope.launch {
                                val detail = runCatching {
                                    com.streamflixvip.app.network.NetworkModule.reelsApi.getStory(id = story.id)
                                }.getOrNull()
                                val eps = detail?.episodes ?: emptyList()
                                PendingReel.set(PendingReelSession(story = detail?.story ?: story, episodes = eps))
                                navController.navigate("reelplayer")
                            }
                        }
                    },
                )
            }

            composable("reelplayer") {
                val session = remember { PendingReel.consume() }
                if (session == null) {
                    LaunchedEffect(Unit) { navController.popBackStack() }
                } else {
                    ReelPlayerScreen(
                        session = session,
                        onBack = { navController.popBackStack() },
                    )
                }
            }

'''
    if 'composable("livetv")' not in mt:
        raise SystemExit('livetv composable missing')
    mt = mt.replace('            composable("livetv") {', block + '            composable("livetv") {', 1)
    print('main routes ok')
else:
    print('main routes ja ok')

ma.write_text(mt)
print('main written', ma.stat().st_size)

g = Path('android/app/build.gradle.kts')
gt = g.read_text()
if 'versionName = "11.9.0"' in gt:
    gt = gt.replace('versionCode = 110900', 'versionCode = 110901')
    gt = gt.replace('versionName = "11.9.0"', 'versionName = "11.9.1"')
    g.write_text(gt)
    print('version 11.9.1')
else:
    print('version skip')
