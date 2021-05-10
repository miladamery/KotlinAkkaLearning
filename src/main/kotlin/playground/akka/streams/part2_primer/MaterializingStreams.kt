package playground.akka.streams.part2_primer

import akka.Done
import akka.actor.ActorSystem
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Keep
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import java.util.concurrent.CompletableFuture

class MaterializingStreams {
}

fun main() {
    val system = ActorSystem.create("materializingStreams")

    val simpleGraph = Source.range(1, 10).to(Sink.foreach(::println))
    //val simpleMaterializedValue = simpleGraph.run(system)

    /*val source = Source.range(1, 10)
    val sink = Sink.reduce { a: Int, b: Int -> a + b }
    val sum = source.runWith(sink, system)
    print((sum as CompletableFuture).join())*/

    val simpleSource = Source.range(1, 10)
    val simpleFlow = Flow.of(Int::class.java).map { it + 1 }
    val simpleSink = Sink.foreach<Int>(::println)
   /* val graph = simpleSource.viaMat(simpleFlow, Keep.right()).toMat(simpleSink, Keep.right())
    (graph.run(system) as CompletableFuture).get()

    val sum = Source.range(1, 10).runWith(Sink.reduce{a: Int, b: Int -> a + b}, system) // source.to(Sink.reduce, Keep.right())
    val sameSum = Source.range(1, 10).runReduce({a: Int, b: Int -> a + b}, system)*/

    // backwards
    //Sink.foreach<Int>(::println).runWith(Source.single(42), system)
    // both ways
    //Flow.of(Int::class.java).map { it * 2 }.runWith(simpleSource, simpleSink, system)

    /**
     * - return the last element out of a source( Sink.last)
     * - compute the total word count out of a stream of sentences
     *  - map, fold, reduce
     */

    val exe1Source = Source.range(1, 10)
    val exe1Sink = Sink.last<Int>()
    println((exe1Source.runWith(exe1Sink, system) as CompletableFuture).get())

    val exe2Source = Source
        .from(listOf("first sentence", "2 sentence", "third sentence with extra word"))
        .map { it.split(" ") }
        .fold(0) {a: Int, b: List<String> -> a + b.fold(0) { c: Int, d: String -> c + d.length } }
    val exe2Sink = Sink.foreach<Int>(::println)
    println(exe2Source.runWith(exe2Sink, system))
}