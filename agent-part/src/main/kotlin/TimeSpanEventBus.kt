package com.epam.drill.plugins.test2code

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

interface TimeSpanEventBus<T> : Channel<T>, Flow<Sequence<T>>

class TimeSpanEventBusImpl<T>(
    timeout: Long,
    private val coroutineScope: CoroutineScope = GlobalScope,
    private val mainChannel: Channel<T> = Channel(),
    private val ticker: ReceiveChannel<Unit> = ticker(timeout, 0)
) : TimeSpanEventBus<T>, Channel<T> by mainChannel, Flow<Sequence<T>> {

    override fun close(cause: Throwable?): Boolean = run {
        ticker.cancel()
        mainChannel.close(cause)
    }

    override suspend fun collect(collector: FlowCollector<Sequence<T>>) {
        mainChannel.consumeAsFlow().timeSpanChunked(ticker).collect(collector)
    }

    private fun <T> Flow<T>.timeSpanChunked(
        ticker: ReceiveChannel<Unit>
    ): Flow<Sequence<T>> = windowed(ticker) { it }

    private fun <T, R> Flow<T>.windowed(
        ticker: ReceiveChannel<Unit>,
        transform: suspend (Sequence<T>) -> R
    ): Flow<R> = channelFlow {
        val buffer = Channel<T>()
        coroutineScope.launch {
            ticker.consumeEach {
                if (!buffer.isEmpty) {
                    send(transform(buffer.takeAvailable()))
                }
            }
        }
        collect { value ->
            coroutineScope.launch {
                buffer.send(value)
            }
        }
    }
}

private suspend fun <T> ReceiveChannel<T>.takeAvailable() = sequence {
    while (poll()?.also { yield(it) } != null) Unit
}
