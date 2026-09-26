package cu.jcmachadoh.simplecargador.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import cu.jcmachadoh.simplecargador.di.AppContainer

/** Fábrica genérica para inyección manual del contenedor en los ViewModel (RNF-09). */
@Composable
inline fun <reified VM : ViewModel> containerViewModel(
    container: AppContainer,
    crossinline create: (AppContainer) -> VM
): VM {
    val factory = remember {
        viewModelFactory { initializer { create(container) } }
    }
    return viewModel(factory = factory)
}
