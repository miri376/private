package az.kilid.app.data

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

/**
 * Lokal vəziyyət (SharedPreferences). Server və bulud yoxdur.
 * PIN burada DEYİL, security/PinManager-də şifrələnmiş saxlanır.
 */
class KilidRepository private constructor(context: Context) {
    private val prefs = context.getSharedPreferences("kilid_state", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(load())
    val state: StateFlow<AppState> = _state.asStateFlow()

    @Synchronized
    fun update(block: (AppState) -> AppState) {
        val now = System.currentTimeMillis()
        val next = block(_state.value).let { s -> s.copy(unlocks = s.unlocks.filterValues { it > now }) }
        save(next)
        _state.value = next
    }

    @Synchronized
    fun wipe() {
        prefs.edit().clear().apply()
        _state.value = AppState()
    }

    private fun load(): AppState {
        val schedules = try {
            val arr = JSONArray(prefs.getString("schedules", "[]"))
            (0 until arr.length()).map { i ->
                val o = arr.getJSONObject(i)
                Schedule(
                    id = o.getString("id"),
                    name = o.getString("name"),
                    weekdays = o.getJSONArray("weekdays").toIntSet(),
                    startMinutes = o.getInt("start"),
                    endMinutes = o.getInt("end"),
                    enabled = o.getBoolean("enabled"),
                    useMainSelection = o.getBoolean("useMain"),
                    ownPackages = o.getJSONArray("own").toStringSet()
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
        val unlocks = try {
            val o = JSONObject(prefs.getString("unlocks", "{}"))
            o.keys().asSequence().associateWith { o.getLong(it) }
        } catch (e: Exception) {
            emptyMap()
        }
        return AppState(
            selected = prefs.getStringSet("selected", emptySet())?.toSet() ?: emptySet(),
            manualLock = prefs.getBoolean("manualLock", false),
            schedules = schedules,
            unlocks = unlocks,
            onboarded = prefs.getBoolean("onboarded", false),
            biometricEnabled = prefs.getBoolean("biometric", false),
            autoLockSeconds = prefs.getInt("autoLock", 60),
            theme = runCatching { ThemeMode.valueOf(prefs.getString("theme", "SYSTEM") ?: "SYSTEM") }
                .getOrDefault(ThemeMode.SYSTEM)
        )
    }

    private fun save(s: AppState) {
        val schedulesJson = JSONArray().also { arr ->
            s.schedules.forEach { sc ->
                arr.put(JSONObject().apply {
                    put("id", sc.id)
                    put("name", sc.name)
                    put("weekdays", JSONArray(sc.weekdays.toList()))
                    put("start", sc.startMinutes)
                    put("end", sc.endMinutes)
                    put("enabled", sc.enabled)
                    put("useMain", sc.useMainSelection)
                    put("own", JSONArray(sc.ownPackages.toList()))
                })
            }
        }.toString()
        val unlocksJson = JSONObject().also { o -> s.unlocks.forEach { (k, v) -> o.put(k, v) } }.toString()
        prefs.edit()
            .putStringSet("selected", HashSet(s.selected))
            .putBoolean("manualLock", s.manualLock)
            .putString("schedules", schedulesJson)
            .putString("unlocks", unlocksJson)
            .putBoolean("onboarded", s.onboarded)
            .putBoolean("biometric", s.biometricEnabled)
            .putInt("autoLock", s.autoLockSeconds)
            .putString("theme", s.theme.name)
            .apply()
    }

    private fun JSONArray.toIntSet(): Set<Int> = (0 until length()).map { getInt(it) }.toSet()
    private fun JSONArray.toStringSet(): Set<String> = (0 until length()).map { getString(it) }.toSet()

    companion object {
        @Volatile
        private var instance: KilidRepository? = null

        fun get(context: Context): KilidRepository =
            instance ?: synchronized(this) {
                instance ?: KilidRepository(context.applicationContext).also { instance = it }
            }
    }
}
