package app.aulune.mobile

import android.content.Context
import android.content.Intent

/**
 * Native adapter for the login flow.
 *
 * The original PiliPlus implementation is Flutter/Dart and cannot be copied
 * into this Kotlin/Compose application without bringing in a second runtime.
 * Aulune therefore keeps the authentication boundary on Bilibili's official
 * HTTPS pages and only receives the resulting WebView session through the
 * existing BilibiliSession consent flow.
 */
object BilibiliLoginAdapter {
    fun openOfficialLogin(context: Context) {
        context.startActivity(
            BilibiliWebActivity.createIntent(context, BilibiliDestination.Login)
        )
    }

    fun createOfficialLoginIntent(context: Context): Intent =
        BilibiliWebActivity.createIntent(context, BilibiliDestination.Login)
}
