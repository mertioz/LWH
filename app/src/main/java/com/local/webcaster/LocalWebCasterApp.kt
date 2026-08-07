package com.local.webcaster

import android.app.Application
import com.local.webcaster.adblock.FilterRuleParser
import com.local.webcaster.adblock.FilterListUpdater
import com.local.webcaster.adblock.SimpleAdBlockEngine
import com.local.webcaster.data.PreferencesRepository
import com.local.webcaster.data.BrowserDataRepository
import com.local.webcaster.detection.MediaCandidateRepository
import com.local.webcaster.detection.MediaDetector
import com.local.webcaster.hls.HlsCandidateEnricher
import com.local.webcaster.relay.LocalMediaRelay
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class LocalWebCasterApp : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob())
    lateinit var preferences: PreferencesRepository
        private set
    lateinit var browserData: BrowserDataRepository
        private set
    lateinit var mediaRepository: MediaCandidateRepository
        private set
    lateinit var mediaDetector: MediaDetector
        private set
    lateinit var hlsEnricher: HlsCandidateEnricher
        private set
    lateinit var adBlockEngine: SimpleAdBlockEngine
        private set
    lateinit var mediaRelay: LocalMediaRelay
        private set

    override fun onCreate() {
        super.onCreate()
        preferences = PreferencesRepository(this)
        browserData = BrowserDataRepository(this, applicationScope)
        mediaRepository = MediaCandidateRepository()
        mediaDetector = MediaDetector(mediaRepository)
        hlsEnricher = HlsCandidateEnricher(mediaRepository, applicationScope)
        mediaDetector.onCandidate = hlsEnricher::enrich
        val ruleText = assets.open("adblock_rules.txt").bufferedReader().use { it.readText() }
        val parser = FilterRuleParser()
        val fallbackRules = parser.parse(ruleText)
        adBlockEngine = SimpleAdBlockEngine(fallbackRules, preferences)
        FilterListUpdater(this, applicationScope, parser).start(fallbackRules, adBlockEngine::updateRules)
        mediaRelay = LocalMediaRelay(this)
    }

    override fun onTerminate() {
        mediaRelay.stop()
        browserData.close()
        applicationScope.cancel()
        super.onTerminate()
    }
}
