package com.aibrain.app

import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.action.ViewActions.click
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.isRoot
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.espresso.matcher.ViewMatchers.withText
import androidx.test.espresso.ViewAction
import androidx.test.espresso.UiController
import android.view.View
import org.hamcrest.Matcher
import androidx.recyclerview.widget.RecyclerView
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/**
 * E2E da navegação global principal do IaBrain.
 *
 * Jornada:
 * Brain -> Chat -> Navegador -> Brain -> Prompts/Comandos -> Brain
 *
 * Não acessa sites externos nem envia prompts.
 * Valida apenas a navegação interna e a presença das telas de destino.
 */
@RunWith(AndroidJUnit4::class)
class GlobalNavigationE2ETest {

    @Test
    fun usuarioPercorreAsQuatroAreasPrincipais() {
        ActivityScenario.launch(MainActivity::class.java).use {

            // Brain inicial
            onView(withId(R.id.btnAbrirColecoes))
                .check(matches(isDisplayed()))

            // Brain -> Chat
            onView(withId(R.id.nav_chat))
                .perform(click())

            onView(withId(R.id.editPergunta))
                .check(matches(isDisplayed()))

            onView(withId(R.id.btnPerguntar))
                .check(matches(isDisplayed()))

            // Chat -> Navegador
            onView(withId(R.id.nav_navegador))
                .perform(click())

            onView(withId(R.id.recyclerAbasBrowser))
                .check(matches(isDisplayed()))

            onView(isRoot()).perform(aguardarAbasRenderizadas())

            onView(withId(R.id.recyclerAbasBrowser))
                .check(matches(isDisplayed()))
                .check(matches(androidx.test.espresso.matcher.ViewMatchers.hasMinimumChildCount(1)))

            onView(withId(R.id.containerWebViewBrowser))
                .check(matches(isDisplayed()))

            // Navegador -> Brain
            onView(withId(R.id.nav_brain))
                .perform(click())

            onView(withId(R.id.btnAbrirColecoes))
                .check(matches(isDisplayed()))

            onView(withId(R.id.btnAbrirGuias))
                .check(matches(isDisplayed()))

            // Brain -> Prompts/Comandos
            onView(withId(R.id.nav_prompts))
                .perform(click())

            onView(withText("Prompts / Comandos"))
                .check(matches(isDisplayed()))

            onView(withText("Biblioteca de prompts"))
                .check(matches(isDisplayed()))

            onView(withText("Criar prompt"))
                .check(matches(isDisplayed()))

            onView(withText("Comandos disponíveis"))
                .check(matches(isDisplayed()))

            // Prompts/Comandos -> Brain
            onView(withId(R.id.nav_brain))
                .perform(click())

            onView(withId(R.id.btnAbrirColecoes))
                .check(matches(isDisplayed()))
        }
    }

    private fun aguardarAbasRenderizadas(timeoutMs: Long = 10_000): ViewAction =
        object : ViewAction {
            override fun getConstraints(): Matcher<View> = isRoot()
            override fun getDescription(): String =
                "aguardar adapter do Browser populado e child-count maior que zero"

            override fun perform(uiController: UiController, view: View) {
                val limite = android.os.SystemClock.uptimeMillis() + timeoutMs
                fun populado(): Boolean {
                    val recycler = view.findViewById<RecyclerView>(R.id.recyclerAbasBrowser)
                    return recycler?.visibility == View.VISIBLE &&
                        (recycler.adapter?.itemCount ?: 0) > 0 &&
                        recycler.childCount > 0
                }
                while (!populado() && android.os.SystemClock.uptimeMillis() < limite) {
                    uiController.loopMainThreadForAtLeast(50)
                }
                check(populado()) {
                    val recycler = view.findViewById<RecyclerView>(R.id.recyclerAbasBrowser)
                    "Browser adapter não populou: itemCount=${recycler?.adapter?.itemCount}, childCount=${recycler?.childCount}"
                }
            }
        }
}
