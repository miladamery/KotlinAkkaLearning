package playground.akka.streams.part5_advanced

import akka.actor.ActorSystem
import akka.stream.javadsl.Keep
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import java.util.concurrent.CompletableFuture

/**
 * Goal:
 *  - Create streams dynamically
 *  - Process substreams uniformly
 */
class SubStreams {
}

fun main() {
    val system = ActorSystem.create("SubStreams")

    // 1 - grouping a stream by a certain function
    val wordsSource = Source.fromIterator { listOf("Akka", "is", "amazing", "learning", "substreams").iterator() }
    val groups = wordsSource.groupBy(30) {
        if (it.isBlank()) "\\0" else it.toLowerCase()[0]
    }

    // results to different sources based on given key - given lambda above -
    groups.to(Sink.fold(0) { count, word ->
        val newCount = count + 1
        println("I just received $word, count is $newCount")
        newCount
    })
    //.run(system)

    // 2 - merge substreams back
    val textSource = Source.fromIterator {
        listOf(
            "I love Akka Streams",
            "this is amazing",
            "learning from rock the JVM"
        ).iterator()
    }
    val totalCharacterCount = textSource
        .groupBy(2) {
            it.length % 2
        }
        .map { it.length } // do your expensive computation here
        .mergeSubstreamsWithParallelism(2)
        .toMat(Sink.reduce { a, b -> a + b }, Keep.right())
        .run(system)
    println((totalCharacterCount as CompletableFuture).get())

    // 3 - splitting a stream into substreams, when a condition is met
    val text =
        "I love Akka Streams\n" +
                "this is amazing\n" +
                "learning from rock the JVM\n"
    val anotherCharCount = Source.from(text.toList())
        .splitWhen { it == '\n' }
        .filter { it != '\n' }
        .map { 1 }
        .mergeSubstreams()
        .toMat(Sink.reduce { a, b -> a + b }, Keep.right())
        .run(system)
    println("Total Char count is:" + (anotherCharCount as CompletableFuture).get())

    // 4 - flattening
    val simpleSource = Source.range(1, 5)
    simpleSource.flatMapConcat { Source.range(it, it * 3) }.runWith(Sink.foreach(::println), system)
    // simpleSource.flatMapMerge
}