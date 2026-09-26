package cu.jcmachadoh.simplecargador.ui.navigation

import android.app.Activity
import androidx.annotation.StringRes
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import cu.jcmachadoh.simplecargador.R
import cu.jcmachadoh.simplecargador.di.AppContainer
import cu.jcmachadoh.simplecargador.ui.components.AppTopBar
import cu.jcmachadoh.simplecargador.ui.history.HistoryScreen
import cu.jcmachadoh.simplecargador.ui.home.HomeScreen
import cu.jcmachadoh.simplecargador.ui.payment.PaymentScreen
import cu.jcmachadoh.simplecargador.ui.settings.SettingsView
import cu.jcmachadoh.simplecargador.ui.stats.StatsScreen

object Routes {
    const val HOME = "home"
    const val HISTORY = "history"
    const val STATS = "stats"
    const val PAYMENT = "payment"
    const val SETTINGS = "settings"
}

private data class Tab(val route: String, @StringRes val labelRes: Int, val iconRes: Int)

private val tabs = listOf(
    Tab(Routes.HOME, R.string.nav_home, R.drawable.ic_nav_home),
    Tab(Routes.HISTORY, R.string.nav_history, R.drawable.ic_nav_history),
    Tab(Routes.STATS, R.string.nav_stats, R.drawable.ic_nav_stats)
)

@Composable
private fun titleFor(route: String?): String = when (route) {
    Routes.HOME -> stringResource(R.string.nav_home)
    Routes.HISTORY -> stringResource(R.string.nav_history)
    Routes.STATS -> stringResource(R.string.nav_stats)
    Routes.SETTINGS -> stringResource(R.string.nav_settings)
    else -> ""
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()
    val backStack by navController.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val isTabRoute = tabs.any { it.route == currentRoute }
    val activity = LocalContext.current as? android.app.Activity

    Scaffold(
        topBar = {
            val showTopBar = isTabRoute || currentRoute == Routes.SETTINGS
            if (showTopBar) {
                AppTopBar(
                    title = titleFor(currentRoute),
                    container = container,
                    onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                    onNavigateUp = if (currentRoute == Routes.SETTINGS) {
                        { navController.navigateUp() }
                    } else null,
                    showSettingsButton = isTabRoute
                )
            }
        },
        bottomBar = {
            if (isTabRoute) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                navController.navigate(tab.route) {
                                    popUpTo(navController.graph.startDestinationId) { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = {
                                Icon(
                                    painter = painterResource(id = tab.iconRes),
                                    contentDescription = stringResource(tab.labelRes)
                                )
                            },
                            label = { Text(stringResource(tab.labelRes)) }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Routes.HOME,
            modifier = Modifier.padding(innerPadding),
            enterTransition = {
                fadeIn(tween(200, easing = EaseOutCubic)) +
                    slideInVertically(tween(220, easing = EaseOutCubic)) { it / 28 }
            },
            exitTransition = { fadeOut(tween(140)) },
            popEnterTransition = { fadeIn(tween(200, easing = EaseOutCubic)) },
            popExitTransition = { fadeOut(tween(140)) }
        ) {
            composable(Routes.HOME) { HomeScreen(container = container) }
            composable(Routes.HISTORY) {
                HistoryScreen(
                    container = container,
                    onGoToPayment = { navController.navigate(Routes.PAYMENT) }
                )
            }
            composable(Routes.STATS) {
                StatsScreen(
                    container = container,
                    onGoToPayment = { navController.navigate(Routes.PAYMENT) }
                )
            }
            composable(Routes.PAYMENT) {
                PaymentScreen(
                    container = container,
                    onBack = { navController.navigateUp() }
                )
            }
            composable(Routes.SETTINGS) {
                SettingsView(
                    container = container,
                    onChangeLanguage = { activity?.recreate() },
                    onGoToPayment = { navController.navigate(Routes.PAYMENT) }
                )
            }
        }
    }
}
