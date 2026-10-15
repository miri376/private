package az.kilid.app

import android.app.Application
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import az.kilid.app.data.AppState
import az.kilid.app.data.KilidRepository
import az.kilid.app.data.Schedule
import az.kilid.app.data.ThemeMode
import az.kilid.app.security.PinManager
import az.kilid.app.security.PinResult
import az.kilid.app.service.PermissionState
import az.kilid.app.service.ServiceController
import kotlinx.coroutines.flow.StateFlow

class Reauth(val reason: String, val action: () -> Unit)

class KilidViewModel(app: Application) : AndroidViewModel(app) {
    val repo = KilidRepository.get(app)
    val pins = PinManager(app)
    val state: StateFlow<AppState> = repo.state

    var locked by mutableStateOf(true)
        private set
    var reauth by mutableStateOf<Reauth?>(null)
        private set
    var banner by mutableStateOf<String?>(null)
    var permissions by mutableStateOf(PermissionState.read(app))
        private set
    var hasPin by mutableStateOf(pins.hasPin())
        private set

    private var lastAuthAt = 0L
    private var backgroundAt = 0L

    // ---- Autentifikasiya -------------------------------------------------

    fun verifyPin(pin: String): PinResult =
        pins.verify(pin).also { if (it is PinResult.Success) markAuthenticated() }

    fun markAuthenticated() {
        lastAuthAt = SystemClock.elapsedRealtime()
    }

    fun unlockApp() {
        markAuthenticated()
        locked = false
    }

    /** Həssas əməliyyatlar son 30 saniyə ərzində təsdiq olunubsa dərhal, əks halda yenidən təsdiqlə icra olunur. */
    fun requireAuth(reason: String, action: () -> Unit) {
        val fresh = lastAuthAt != 0L && SystemClock.elapsedRealtime() - lastAuthAt < 30_000
        if (fresh) action() else reauth = Reauth(reason, action)
    }

    fun completeReauth() {
        markAuthenticated()
        val action = reauth?.action
        reauth = null
        action?.invoke()
    }

    fun cancelReauth() {
        reauth = null
    }

    fun setPin(pin: String) {
        pins.setPin(pin)
        hasPin = true
    }

    // ---- Həyat dövrü ------------------------------------------------------

    fun onForeground() {
        val s = state.value
        if (s.onboarded && backgroundAt != 0L &&
            SystemClock.elapsedRealtime() - backgroundAt >= s.autoLockSeconds * 1000L
        ) {
            locked = true
        }
        refreshPermissions()
    }

    fun onBackground() {
        backgroundAt = SystemClock.elapsedRealtime()
    }

    fun refreshPermissions() {
        permissions = PermissionState.read(getApplication())
        ServiceController.ensureRunning(getApplication())
    }

    fun finishOnboarding() {
        repo.update { it.copy(onboarded = true) }
        unlockApp()
        ServiceController.ensureRunning(getApplication())
    }

    // ---- Kilid və açma ----------------------------------------------------

    fun lockNow() {
        val s = state.value
        when {
            !permissions.core ->
                banner = "Məhdudiyyətin işləməsi üçün “İstifadə statistikası” və “Digər tətbiqlərin üstündə göstər” icazələri lazımdır."
            s.selected.isEmpty() ->
                banner = "Əvvəlcə “Tətbiqlər” bölməsində ən azı bir tətbiq seçin."
            else -> {
                repo.update { it.copy(manualLock = true) }
                banner = null
            }
        }
    }

    fun disableManualLock() = repo.update { it.copy(manualLock = false) }

    fun unlockAll(minutes: Int) = repo.update {
        it.copy(unlocks = it.unlocks + ("*" to (System.currentTimeMillis() + minutes * 60_000L)))
    }

    fun unlockPackage(pkg: String, minutes: Int) = repo.update {
        it.copy(unlocks = it.unlocks + (pkg to (System.currentTimeMillis() + minutes * 60_000L)))
    }

    fun endUnlocks() = repo.update { it.copy(unlocks = emptyMap()) }

    // ---- Seçim ------------------------------------------------------------

    fun toggleApp(pkg: String, on: Boolean) = repo.update {
        it.copy(selected = if (on) it.selected + pkg else it.selected - pkg)
    }

    // ---- Cədvəllər --------------------------------------------------------

    fun saveSchedule(schedule: Schedule): Boolean {
        if (!schedule.isValid) {
            banner = "Cədvəl düzgün deyil: ən azı bir gün seçin, başlama və bitmə vaxtı eyni olmasın."
            return false
        }
        if (!schedule.useMainSelection && schedule.ownPackages.isEmpty()) {
            banner = "Bu cədvəl üçün ən azı bir tətbiq seçin və ya əsas seçimdən istifadə edin."
            return false
        }
        repo.update { st ->
            val index = st.schedules.indexOfFirst { it.id == schedule.id }
            val list = if (index >= 0) st.schedules.toMutableList().also { it[index] = schedule } else st.schedules + schedule
            st.copy(schedules = list)
        }
        banner = null
        return true
    }

    fun deleteSchedule(id: String) = repo.update { st -> st.copy(schedules = st.schedules.filterNot { it.id == id }) }

    fun setScheduleEnabled(id: String, enabled: Boolean) = repo.update { st ->
        st.copy(schedules = st.schedules.map { if (it.id == id) it.copy(enabled = enabled) else it })
    }

    // ---- Parametrlər ------------------------------------------------------

    fun setBiometric(on: Boolean) = repo.update { it.copy(biometricEnabled = on) }
    fun setAutoLock(seconds: Int) = repo.update { it.copy(autoLockSeconds = seconds) }
    fun setTheme(mode: ThemeMode) = repo.update { it.copy(theme = mode) }

    fun resetAll() {
        ServiceController.stop(getApplication())
        pins.wipe()
        repo.wipe()
        hasPin = false
        locked = true
        reauth = null
        lastAuthAt = 0L
    }
}
