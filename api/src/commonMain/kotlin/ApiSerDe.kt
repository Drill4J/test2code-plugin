package com.epam.drill.plugins.test2code.api

import com.epam.drill.plugin.api.*
import com.epam.drill.plugins.test2code.common.api.*
import kotlinx.serialization.modules.*

val apiSerDe = SerDe(
    actionSerializer = commonSerDe.actionSerializer,
    ctx = commonSerDe.ctx + SerializersModule {
        polymorphic<Action> {
            subclass(UpdateSettings.serializer())
            subclass(SwitchActiveScope.serializer())
            subclass(RenameScope.serializer())
            subclass(ToggleScope.serializer())
            subclass(DropScope.serializer())
            subclass(StartNewSession.serializer())
        }
    }
)
