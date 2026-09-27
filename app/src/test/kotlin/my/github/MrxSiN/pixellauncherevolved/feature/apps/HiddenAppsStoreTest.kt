package my.github.MrxSiN.pixellauncherevolved.feature.apps

import android.content.SharedPreferences

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HiddenAppsStoreTest {

    private val preferences = TextPreferences()
    private val store = SharedPreferencesHiddenAppsStore(preferences)

    @Test
    fun `stored text is read as the set it always was`() {
        preferences.text = "com.b,, ,com.a"
        assertEquals(setOf("com.b", "com.a"), store.hidden())

        preferences.text = null
        assertTrue(store.hidden().isEmpty())
    }

    @Test
    fun `the same text is parsed once`() {
        preferences.text = "com.a,com.b"
        assertSame(store.hidden(), store.hidden())

        // An equal string that is not the same instance still hits.
        preferences.text = StringBuilder("com.a,com.b").toString()
        val first = store.hidden()
        assertSame(first, store.hidden())
    }

    @Test
    fun `a change is seen on the next call`() {
        preferences.text = "com.a"
        assertEquals(setOf("com.a"), store.hidden())

        store.hide(setOf("com.c", "com.b"))
        assertEquals(setOf("com.b", "com.c"), store.hidden())

        store.hide(emptySet())
        assertTrue(store.hidden().isEmpty())
    }

    /** One string preference, written through an editor as the launcher's is. */
    private class TextPreferences : SharedPreferences {
        var text: String? = null

        override fun getString(key: String?, defValue: String?): String? = text ?: defValue

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private var next: String? = text
            override fun putString(key: String?, value: String?) = apply { next = value }
            override fun remove(key: String?) = apply { next = null }
            override fun apply() { text = next }
            override fun commit(): Boolean { apply(); return true }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = this
            override fun putInt(key: String?, value: Int) = this
            override fun putLong(key: String?, value: Long) = this
            override fun putFloat(key: String?, value: Float) = this
            override fun putBoolean(key: String?, value: Boolean) = this
            override fun clear() = apply { next = null }
        }

        override fun getAll(): MutableMap<String, *> = mutableMapOf<String, Any?>()
        override fun getStringSet(key: String?, defValues: MutableSet<String>?) = defValues
        override fun getInt(key: String?, defValue: Int) = defValue
        override fun getLong(key: String?, defValue: Long) = defValue
        override fun getFloat(key: String?, defValue: Float) = defValue
        override fun getBoolean(key: String?, defValue: Boolean) = defValue
        override fun contains(key: String?) = text != null
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
    }
}
