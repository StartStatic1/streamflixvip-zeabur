package com.streamflixvip.app.ads

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.util.Log
import com.startapp.sdk.adsbase.Ad
import com.startapp.sdk.adsbase.StartAppAd
import com.startapp.sdk.adsbase.adlisteners.AdDisplayListener
import com.startapp.sdk.adsbase.adlisteners.AdEventListener
import com.startapp.sdk.adsbase.adlisteners.VideoListener

/**
 * Centraliza Start.io no app.
 *
 * Estratégia de monetização (não-VIP):
 *  1. Banner na Home — receita passiva, pouco atrito.
 *  2. Rewarded Video no "Assistir grátis" — usuário escolhe ver o vídeo
 *     (geralmente promo de jogo/app, ~15–30s) e libera o player.
 *     Isso paga bem mais que interstitial e substitui a espera vazia.
 *  3. Interstitial só em momentos secundários (ex.: trailer), nunca
 *     empilhado com o rewarded do play.
 *
 * VIP nunca chama estes métodos (checagem fica no caller).
 */
object AdsHelper {
    private const val TAG = "AdsHelper"

    private fun activityFrom(context: Context): Activity? {
        var ctx: Context = context
        while (ctx is ContextWrapper) {
            if (ctx is Activity) return ctx
            ctx = ctx.baseContext
        }
        return ctx as? Activity
    }

    /**
     * Prepara e exibe Rewarded Video.
     * - onRewarded: usuário assistiu até o fim → liberar o conteúdo.
     * - onDismissedWithoutReward: fechou antes / pulou → não libera.
     * - onFailed: sem fill (comum com pouco tráfego) → caller decide
     *   fallback (ex.: contagem curta ou liberar mesmo assim).
     */
    fun showRewardedVideo(
        context: Context,
        onRewarded: () -> Unit,
        onDismissedWithoutReward: () -> Unit = {},
        onFailed: () -> Unit = {},
    ) {
        val activity = activityFrom(context)
        if (activity == null) {
            Log.w(TAG, "Sem Activity — não dá pra mostrar rewarded")
            onFailed()
            return
        }

        val ad = StartAppAd(activity)
        var rewarded = false

        ad.setVideoListener(VideoListener {
            // Chamado quando o vídeo chega ao fim (usuário ganhou a recompensa).
            rewarded = true
            Log.d(TAG, "Rewarded video completo")
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
                        Log.w(TAG, "Rewarded não exibido")
                        onFailed()
                    }
                })
            }

            override fun onFailedToReceiveAd(ad: Ad?) {
                Log.w(TAG, "Falha ao carregar rewarded video (sem fill?)")
                onFailed()
            }
        })
    }

    /**
     * Interstitial clássico (tela cheia). Use com moderação — eCPM
     * costuma ser menor que rewarded e interrompe mais.
     */
    fun showInterstitial(context: Context) {
        val activity = activityFrom(context) ?: run {
            // Fallback: API estática (menos confiável, mas não quebra).
            try { StartAppAd.showAd(context) } catch (_: Exception) {}
            return
        }
        val ad = StartAppAd(activity)
        ad.loadAd(StartAppAd.AdMode.FULLPAGE, object : AdEventListener {
            override fun onReceiveAd(adReceived: Ad) {
                ad.showAd()
            }
            override fun onFailedToReceiveAd(ad: Ad?) {
                Log.w(TAG, "Falha ao carregar interstitial")
            }
        })
    }
}
