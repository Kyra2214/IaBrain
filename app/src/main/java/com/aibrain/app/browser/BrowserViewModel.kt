package com.aibrain.app.browser

import androidx.lifecycle.ViewModel

/**
 * Fase 21.1 — esqueleto do BrowserViewModel.
 * Vai concentrar o estado do conjunto de abas ([AbaNavegador], Fase 21.2)
 * exposto para a [BrowserActivity], mesmo padrão de separação de
 * responsabilidades já usado por [com.aibrain.app.viewmodel.MainViewModel]
 * (Fase 12.4). Sem lógica ainda — entra a partir da Fase 21.5
 * ([BrowserTabManager]).
 */
class BrowserViewModel : ViewModel()
