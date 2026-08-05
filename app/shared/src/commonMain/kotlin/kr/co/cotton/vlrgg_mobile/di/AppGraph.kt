package kr.co.cotton.vlrgg_mobile.di

import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.createGraph

@DependencyGraph
interface AppGraph {
    val appViewModelFactory: AppViewModelFactory
}

fun createAppGraph(): AppGraph = createGraph<AppGraph>()
