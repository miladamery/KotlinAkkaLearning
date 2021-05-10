package playground.akka.streams.part2_primer

import akka.actor.ActorSystem
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source

class FirstPrinciples {

}

fun main() {
    val system = ActorSystem.create("firstPrinciples")

    // source
    val source = Source.range(1, 10)
    //sink
    val sink = Sink.foreach<Int> { println(it) }

    val graph = source.to(sink)
    //graph.run(system)

    // flows transform elements
    val flow = Flow.of(Int::class.java).map {
        it + 1
    }
    val sourceWithFlow = source.via(flow)
    val flowWithSink = flow.to(sink)
    // sourceWithFlow.to(sink).run(system)
    // source.to(flowWithSink).run(system)
    // source.via(flow).to(sink).run(system)

    // Nulls are NOT allowed
    //val illegalSource = Source.single(null)
    //illegalSource.to(Sink.foreach{ println(it)})
    // use options instead

    // various kind of sources
    val finiteSource = Source.single(1)
    val anotherFiniteSource = Source.from(listOf(1, 2, 3))
    val emptySource = Source.empty<Int>()

    // sinks
    val theMostBoringSink = Sink.ignore<Int>()
    val forEachSink = Sink.foreach<String> { println(it) }
    val headSink = Sink.head<Int>() // retrieves head and then closes the stream
    val foldSink = Sink.fold(0) { a: Int, b: Int ->
        a + b
    }

    // flows - usually mapped to collection operators
    val mapFlow = Flow.of(Int::class.java).map { it * 2 }
    val takeFlow = Flow.of(Int::class.java).take(5)
    // drop, filter
    // NOT have flatMap

    // source -> flow -> flow -> ... -> sink
    //source.via(mapFlow).via(takeFlow).to(sink).run(system)

    // syntactic sugars
    val mapSource = Source.range(1, 10).map { it * 2 } // eq: Source.range(1, 10).via(Flow.of(Int::class.java).map { it * 2 })

    // run streams directly
    //mapSource.runForeach(::println, system)

    // OPERATORS = components

    /**
     * Excersice: create a stream that takes the names of persons, then you will keep the first 2 names
     * with length > 5 characters
     */
    Source
        .from(listOf("Alice", "Bob", "Charlie", "David", "Martin", "AkkaStreams"))
        .filter { it.length > 5 }
        .take(2)
        .runForeach(::println, system)
}