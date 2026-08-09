package com.streamflixvip.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

/**
 * Anuncios centralizados.
 *
 * Cadeia no Assistir gratis (nao-VIP):
 *   1) AdMob Rewarded
 *   2) AdMob Interstitial
 *   3) Start.io Rewarded
 *   4) onFailed -> caller (contagem / upsell)
 *
 * USE_TEST_ADS = true -> IDs oficiais de teste do Google.
 * Troque para false so depois de validar.
 */
object AdsHelper {
    private const val TAG = "AdsHelper"

    const val USE_TEST_ADS = true

    private val REWARDED_ID: String
        get() = if (USE_TEST_ADS) {
            "ca-app-pub-3940256099942544/5224354917"
        } else {
            "ca-app-pub-2866002449649160/5677217096"
        }

    private val INTERSTITIAL_ID: String
        get() = if (USE_TEST_ADS) {
            "ca-app-pub-3940256099942544/1033173712"
        } else {
            "ca-app-pub-2866002449649160/2684222248"
        }

    val BANNER_ID: String
        get() = if (USE_TEST_ADS) {
            "ca-app-pub-3940256099942544/6300978111"
        } else {
            "ca-app-pub-2866002449649160/7222233052"
        }

    @Volatile
    private var mobileAdsReady = false

    fun init(context: Context) {
        if (mobileAdsReady) return
        try {
            MobileAds.initialize(context.applicationContext) {
                mobileAdsReady = true
                Log.d(TAG, "MobileAds ready (test=$USE_TEST_ADS)")
            }
        } catch (e: Exception) {
            Log.e(TAG, "MobileAds init falhou", e)
        }
    }

    private fun activityFrom(context: Context): Activity? {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    fun showUnlockAd(
        context: Context,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        showAdMobRewarded(
            context = context,
            onRewarded = onRewarded,
            onDismissedWithoutReward = onDismissedWithoutReward,
            onFailed = {
                Log.d(TAG, "AdMob rewarded falhou -> interstitial")
                showAdMobInterstitial(
                    context = context,
                    onClosed = onRewarded,
                    onFailed = {
                        Log.d(TAG, "AdMob interstitial falhou -> Start.io")
                        showStartIoRewarded(
                            context = context,
                            onRewarded = onRewarded,
                            onDismissedWithoutReward = onDismissedWithoutReward,
                            onFailed = onFailed,
                        )
                    },
                )
            },
        )
    }

    fun showRewardedVideo(
        context: Context,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) = showUnlockAd(context, onRewarded, onDismissedWithoutReward, onFailed)

    private fun showAdMobRewarded(
        context: Context,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit,
        onFailed: () -> Unit,
    ) {
        val activity = activityFrom(context)
        if (activity == null) {
            onFailed()
            return
        }
        val request = AdRequest.Builder().build()
        RewardedAd.load(
            activity,
            REWARDED_ID,
            request,
            object : RewardedAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "AdMob rewarded load fail: ${error.message}")
                    onFailed()
                }

                override fun onAdLoaded(ad: RewardedAd) {
                    var earned = false
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            if (earned) onRewarded() else onDismissedWithoutReward()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.w(TAG, "AdMob rewarded show fail: ${adError.message}")
                            onFailed()
                        }
                    }
                    ad.show(activity) { _ ->
                        earned = true
                        Log.d(TAG, "AdMob rewarded earned")
                    }
                }
            },
        )
    }

    private fun showAdMobInterstitial(
        context: Context,
        onClosed: () -> Unit,
        onFailed: () -> Unit,
    ) {
        val activity = activityFrom(context)
        if (activity == null) {
            onFailed()
            return
        }
        val request = AdRequest.Builder().build()
        InterstitialAd.load(
            activity,
            INTERSTITIAL_ID,
            request,
            object : InterstitialAdLoadCallback() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.w(TAG, "AdMob interstitial load fail: ${error.message}")
                    onFailed()
                }

                override fun onAdLoaded(ad: InterstitialAd) {
                    ad.fullScreenContentCallback = object : FullScreenContentCallback() {
                        override fun onAdDismissedFullScreenContent() {
                            onClosed()
                        }

                        override fun onAdFailedToShowFullScreenContent(adError: AdError) {
                            Log.w(TAG, "AdMob interstitial show fail: ${adError.message}")
                            onFailed()
                        }
                    }
                    ad.show(activity)
                }
            },
        )
    }

    private fun showStartIoRewarded(
        context: Context,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit,
        onFailed: () -> Unit,
    ) {
        val activity = activityFrom(context)
        if (activity == null) {
            onFailed()
            return
        }

        val ad = StartAppAd(activity)
        var rewarded = false

        ad.setVideoListener(VideoListener {
            rewarded = true
            Log.d(TAG, "Start.io rewarded completo")
        })

        ad.loadAd(StartAppAd.AdMode.REWARDED_VIDEO, object : AdEventListener {
            override fun onReceiveAd(adReceived: Ad) {
                ad.showAd(object : AdDisplayListener {
                    override fun adHidden(ad: Ad?) {
                        if (rewarded) onRewarded() else onDismissedWithoutReward()
                    }

                    override fun adDisplayed(ad: Ad?) {}
                    override fun adClicked(ad: Ad?) {}
                    override fun adNotDisplayed(ad: Ad?) {
                        Log.w(TAG, "Start.io rewarded not displayed")
                        onFailed()
                    }
                })
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                Log.w(TAG, "Start.io rewarded load fail")
                onFailed()
            }
        })
    }

    fun showInterstitial(context: Context) {
        showAdMobInterstitial(
            context = context,
            onClosed = {},
            onFailed = {
                val activity = activityFrom(context) ?: return@showAdMobInterstitial
                val ad = StartAppAd(activity)
                ad.loadAd(StartAppAd.AdMode.FULLPAGE, object : AdEventListener {
                    override fun onReceiveAd(adReceived: Ad) {
                        ad.showAd()
                    }

                    override fun onFailedToReceiveAd(ad: Ad?) {
                        Log.w(TAG, "Start.io interstitial falhou")
                    }
                })
            },
        )
    }
}
