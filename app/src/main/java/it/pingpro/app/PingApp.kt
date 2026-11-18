package it.pingpro.app

import android.app.Application
import it.pingpro.app.data.DeviceRepository
import it.pingpro.app.data.HistoryRepository
import it.pingpro.app.data.SettingsRepository
import it.pingpro.app.ping.NetworkInfoProvider
import it.pingpro.app.ping.PingController

class PingApp : Application() {
    lateinit var devices: DeviceRepository; private set
    lateinit var history: HistoryRepository; private set
    lateinit var settings: SettingsRepository; private set
    lateinit var controller: PingController; private set
    lateinit var network: NetworkInfoProvider; private set

    override fun onCreate() {
        super.onCreate()
        devices = DeviceRepository(this)
        history = HistoryRepository(this)
        settings = SettingsRepository(this)
        controller = PingController(this, history, devices, settings)
        network = NetworkInfoProvider(this)
    }
}
