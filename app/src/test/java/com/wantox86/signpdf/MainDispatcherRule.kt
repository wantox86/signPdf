package com.wantox86.signpdf

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.rules.TestWatcher
import org.junit.runner.Description

// viewModelScope always dispatches onto Dispatchers.Main, which has no real implementation in
// a plain JVM/Robolectric unit test. Swaps in an UnconfinedTestDispatcher (runs coroutine
// bodies eagerly) for the duration of each test so ViewModel coroutines actually execute
// instead of silently never running.
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    private val dispatcher = UnconfinedTestDispatcher()

    override fun starting(description: Description) {
        Dispatchers.setMain(dispatcher)
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}
