package com.epam.drill.plugins.test2code.common.api

import com.epam.drill.plugin.api.*
import kotlinx.serialization.modules.*

val commonSerDe = SerDe(
    actionSerializer = Action.serializer(),
    ctx = SerializersModule {
        polymorphic<Action> {
            subclass(InitActiveScope.serializer())
            subclass(StartSession.serializer())
            subclass(StopSession.serializer())
            subclass(CancelSession.serializer())
        }
        polymorphic<CoverMessage> {
            subclass(InitInfo.serializer())
            subclass(InitDataPart.serializer())
            subclass(Initialized.serializer())

            subclass(ScopeInitialized.serializer())
            subclass(SessionStarted.serializer())
            subclass(CoverDataPart.serializer())
            subclass(SessionCancelled.serializer())
            subclass(AllSessionsCancelled.serializer())
            subclass(SessionFinished.serializer())
        }
    }
)
