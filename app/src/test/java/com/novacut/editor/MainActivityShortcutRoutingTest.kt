package com.novacut.editor

import org.junit.Assert.assertEquals
import org.junit.Test

class MainActivityShortcutRoutingTest {

    @Test
    fun staticShortcutActionsHaveDedicatedRoutes() {
        assertEquals(
            ProjectShortcutRoute.NEW_PROJECT,
            projectShortcutRoute(MainActivity.ACTION_NEW_PROJECT),
        )
        assertEquals(
            ProjectShortcutRoute.OPEN_RECENT,
            projectShortcutRoute(MainActivity.ACTION_OPEN_RECENT),
        )
    }

    @Test
    fun unrelatedActionsDoNotFallIntoProjectShortcutRoutes() {
        assertEquals(ProjectShortcutRoute.NONE, projectShortcutRoute("android.intent.action.SEND"))
        assertEquals(ProjectShortcutRoute.NONE, projectShortcutRoute(null))
    }
}
