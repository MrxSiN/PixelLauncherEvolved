package my.github.MrxSiN.pixellauncherevolved.feature.overview.bubble

import android.content.Intent
import android.os.UserHandle

/** Immutable description of what a bubble should open. */
data class BubbleTarget(val intent: Intent, val user: UserHandle)
