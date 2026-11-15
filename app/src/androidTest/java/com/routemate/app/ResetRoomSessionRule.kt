package com.routemate.app

import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.runBlocking
import org.junit.rules.ExternalResource

/** A production call outlives an Activity. Device tests must explicitly isolate and close their fixture. */
class ResetRoomSessionRule:ExternalResource(){
    private val app get()=InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as RouteMateApplication
    override fun before(){InstrumentationRegistry.getInstrumentation().runOnMainSync{app.graph.resetForTest()}}
    override fun after(){runBlocking{app.graph.session.finishForTest()}}
}
