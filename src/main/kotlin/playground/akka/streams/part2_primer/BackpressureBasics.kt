package playground.akka.streams.part2_primer

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source

class BackpressureBasics

fun main() {
    val system = ActorSystem.create("backpressureBasics")

    val fastSource = Source.range(1, 1000)
    val slowSink = Sink.foreach<Int> {
        // simulate long processing
        Thread.sleep(1000)
        println("Sink: $it")
    }

    // fastSource.to(slowSink).run(system)
    // not backpressure

    val simpleFlow = Flow.of(Int::class.java).map{
        println("Incoming: $it")
        it + 1
    }

    fastSource.async()
        .via(simpleFlow).async()
        .to(slowSink)
        //.run(system)

    /*
        reactions to backpressure (in order):
            - try to slow down if possible
            - buffer elements until there's more demand
            - drop down elements from the buffer if it overflows
            - tear down/kill the whole stream (failure)
     */
    val bufferedFlow = simpleFlow.buffer(10, OverflowStrategy.dropHead())
    fastSource.async()
        .via(bufferedFlow).async()
        .to(slowSink)
        .run(system)
}