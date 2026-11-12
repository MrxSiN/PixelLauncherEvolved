package my.github.MrxSiN.pixellauncherevolved.feature.glance.content.sources

import android.content.ContentResolver

import my.github.MrxSiN.pixellauncherevolved.core.Logger
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarEvents
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarList
import my.github.MrxSiN.pixellauncherevolved.glance.CalendarWire

/**
 * Today's and tomorrow's calendar events as this module's app reads them (Pixel Launcher has no
 * Calendar permission). One Binder call into that app, which may start it: call only off the main
 * thread. Null when the app lacks the permission, cannot be reached or answers wrongly.
 */
internal object CalendarBridge {

    /** [calendars] is the stored option value (calendar ids, comma-separated); null or empty reads every calendar. */
    fun read(resolver: ContentResolver, modulePackage: String, logger: Logger, calendars: String? = null): CalendarEvents? = runCatching {
        val filter = CalendarWire.filter(CalendarWire.parseIds(calendars))
        CalendarWire.decode(resolver.call(CalendarWire.authority(modulePackage), CalendarWire.METHOD_EVENTS, null, filter))
    }.onFailure { logger.warn("The calendar could not be read from this module's app", it) }.getOrNull()

    /** The visible calendars, for settings; same rules as [read]. */
    fun calendars(resolver: ContentResolver, modulePackage: String, logger: Logger): CalendarList? = runCatching {
        CalendarWire.decodeCalendars(resolver.call(CalendarWire.authority(modulePackage), CalendarWire.METHOD_CALENDARS, null, null))
    }.onFailure { logger.warn("The calendars could not be listed from this module's app", it) }.getOrNull()
}
