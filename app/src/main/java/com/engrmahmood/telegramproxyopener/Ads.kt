package com.engrmahmood.telegramproxyopener

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback

private const val ADS_TAG = "Ads"

// Google's official public test ad unit IDs. Debug builds always use these so that
// development/testing never generates impressions or clicks on the real ad units,
// which AdMob can flag as invalid traffic and use to suspend the account.
private const val TEST_BANNER_AD_UNIT_ID = "ca-app-pub-3940256099942544/6300978111"
private const val TEST_INTERSTITIAL_AD_UNIT_ID = "ca-app-pub-3940256099942544/1033173712"

fun bannerAdUnitId(context: Context): String =
    if (BuildConfig.DEBUG) TEST_BANNER_AD_UNIT_ID else context.getString(R.string.admob_banner_ad_unit_id)

fun interstitialAdUnitId(context: Context): String =
    if (BuildConfig.DEBUG) TEST_INTERSTITIAL_AD_UNIT_ID else context.getString(R.string.admob_interstitial_ad_unit_id)

@Composable
fun BannerAd(adUnitId: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    AndroidView(
        modifier = modifier.fillMaxWidth(),
        factory = {
            AdView(context).apply {
                setAdSize(AdSize.BANNER)
                this.adUnitId = adUnitId
                loadAd(AdRequest.Builder().build())
            }
        }
    )
}

// Loads one interstitial at a time and reloads after each show, so a fresh ad is
// usually ready by the time the user triggers the next one.
class InterstitialAdManager(private val adUnitId: String) {
    private var interstitialAd: InterstitialAd? = null
    private var isLoading = false

    fun load(context: Context) {
        if (isLoading || interstitialAd != null) return
        isLoading = true
        InterstitialAd.load(
            context,
            adUnitId,
            AdRequest.Builder().build(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitialAd = ad
                    isLoading = false
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    Log.e(ADS_TAG, "Interstitial failed to load: ${error.message}")
                    interstitialAd = null
                    isLoading = false
                }
            }
        )
    }

    // Shows the interstitial if one is ready; onComplete always fires afterwards
    // (immediately if no ad is ready) so callers can chain the real action.
    fun showIfReady(activity: Activity, onComplete: () -> Unit) {
        val ad = interstitialAd
        if (ad == null) {
            onComplete()
            load(activity)
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitialAd = null
                load(activity)
                onComplete()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                interstitialAd = null
                load(activity)
                onComplete()
            }
        }
        ad.show(activity)
    }
}
