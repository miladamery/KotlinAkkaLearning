package playground.akka.streams.part2_primer

import akka.actor.ActorSystem
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source

class OperatorFusion {
}

fun main() {
    val system = ActorSystem.create("operatorFusion")

    val simpleSource = Source.range(1, 1000)
    val simpleFlow = Flow.of(Int::class.java).map { it + 1 }
    val simpleFlow2 = Flow.of(Int::class.java).map { it * 10 }
    val simpleSink = Sink.foreach<Int>(::println)

    // this runs on the SAME ACTOR
    //simpleSource.via(simpleFlow).via(simpleFlow2).to(simpleSink).run(system)
    // Operator/component FUSION

    val complexFlow = Flow.of(Int::class.java).map {
        Thread.sleep(1000)
        it + 1
    }

    val complexFlow2 = Flow.of(Int::class.java).map {
        Thread.sleep(1000)
        it * 2
    }

    // async boundary
    /*simpleSource.via(complexFlow).async() // runs on one actor
        .via(complexFlow2).async() // runs on another actor
        .to(simpleSink) // runs on a third actor
        .run(system)*/

    // ordering guarantees
    Source.range(1, 3)
        .map { println("Flow A: $it"); it }.async()
        .map { println("Flow B: $it"); it }.async()
        .map { println("Flow C: $it"); it }.async()
        .runWith(Sink.ignore(), system)
}