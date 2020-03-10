package com.epam.drill.plugins.test2code

import kotlinx.coroutines.*
import kotlinx.coroutines.channels.*
import kotlinx.coroutines.flow.*

interface TimeSpanEventBus<T> : Channel<T>, Flow<List<T>>

class TimeSpanEventBusImpl<T>(
    timeout: Long,
    private val coroutineScope: CoroutineScope = GlobalScope,
    private val mainChannel: Channel<T> = Channel(),
    private val ticker: ReceiveChannel<Unit> = ticker(timeout, 0)
) : TimeSpanEventBus<T>, Channel<T> by mainChannel, Flow<List<T>> {

    override fun close(cause: Throwable?): Boolean {
        ticker.cancel()
        return mainChannel.close(cause)
    }

    override suspend fun collect(collector: FlowCollector<List<T>>) {
        mainChannel.consumeAsFlow().timeSpanChunked(ticker).collect(collector)
    }

    private fun <T> Flow<T>.timeSpanChunked(ticker: ReceiveChannel<Unit>): Flow<List<T>> {
        return windowed(ticker) { it.toList() }
    }

    private fun <T, R> Flow<T>.windowed(ticker: ReceiveChannel<Unit>, transform: suspend (List<T>) -> R): Flow<R> {
        return channelFlow {
            val buffer = Channel<T>()
            coroutineScope.launch {
                ticker.consumeEach {
                    if (!buffer.isEmpty)
                        send(transform(buffer.takeAvailable()))
                }
            }
            collect { value ->
                coroutineScope.launch {
                    buffer.send(value)
                }
            }

        }
    }
}

suspend fun <T> ReceiveChannel<T>.takeAvailable() =
    flow<T> {
        @Suppress("ControlFlowWithEmptyBody")
        while (run { poll()?.let { emit(it) } } != null);
    }.toList()
