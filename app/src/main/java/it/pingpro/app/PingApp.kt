package it.pingpro.app

import android.app.Application
import it.pingpro.app.data.DeviceRepository
import it.pingpro.app.data.HistoryRepository
import it.pingpro.app.data.SettingsRepository
import it.pingpro.app.ping.NetworkInfoProvider
import it.pingpro.app.ping.MultiPingController
import it.pingpro.app.data.GroupRepository
import it.pingpro.app.data.Updater
import it.pingpro.app.ping.PingController

class PingApp : Application() {
    lateinit var devices: DeviceRepository; private set
    lateinit var history: HistoryRepository; private set
    lateinit var settings: SettingsRepository; private set
    lateinit var controller: PingController; private set
    lateinit var network: NetworkInfoProvider; private set
    lateinit var multi: MultiPingController; private set
    lateinit var groups: GroupRepository; private set
    lateinit var updater: Updater; private set

    override fun onCreate() {
        super.onCreate()
        devices = DeviceRepository(this)
        history = HistoryRepository(this)
        settings = SettingsRepository(this)
        controller = PingController(this, history, devices, settings)
        network = NetworkInfoProvider(this)
        multi = MultiPingController(this, history, devices, settings)
        groups = GroupRepository(this)
        updater = Updater(this)
    }
}
