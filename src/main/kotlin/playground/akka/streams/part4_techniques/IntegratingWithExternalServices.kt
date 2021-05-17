package playground.akka.streams.part4_techniques


import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.pattern.Patterns.ask
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CompletionStage


class IntegratingWithExternalServices {
}

fun main() {
    val system = ActorSystem.create("IntegratingWithExternalServices")
    val dispatcher = system.dispatchers().lookup("dedicated-dispatcher")

    // example: simplified PagerDuty
    val eventSource = Source.fromIterator {
        listOf(
            PagerEvent("AkkaInfra", "Infrastructure broke", Date()),
            PagerEvent("FastDataPipeline", "Illegal elements in the data pipeline", Date()),
            PagerEvent("AkkaInfra", "A service stopped responding", Date()),
            PagerEvent("Suepr front end", "A Button doesn't work", Date()),
        ).iterator()
    }

    val infraEvents = eventSource.filter { it.application == "AkkaInfra" }
    val pagedEngineerEmails =
        infraEvents.mapAsync(2) { CompletableFuture.supplyAsync({ PagerService.processEvent(it) }, dispatcher) }
    val pagedEmailsSink = Sink.foreach<String> { println("Successfully sent notification to $it") }
    // pagedEngineerEmails.to(pagedEmailsSink).run(system)

    // mapAsync guarantees the relative order of elements, mapAsyncUnordered wont
    // it is not recommended to use actor system dispatcher( thread pool) for mapAsync because we may starce
    // ActorSystem from threads. we should use another dedicated dispatcher

    val pagerActor = system.actorOf(Props.create(PagerActor::class.java), "pagerActor")
    infraEvents.mapAsync(4) {
        ask(pagerActor, it, Duration.ofSeconds(2))
    }.to(Sink.foreach(::println)).run(system)

    // do not confuse mapAsync with async (ASYNC boundary)

}

data class PagerEvent(val application: String, val description: String, val date: Date)
object PagerService {
    private val engineers = listOf("Daniel", "John", "me")
    private val emails =
        mapOf("Daniel" to "daniel@rockthejvm.com", "john" to "john@rockthejvm.com", "me" to "me@rockthejvm.com")

    fun processEvent(pagerEvent: PagerEvent): String? {
        val engineerIndex = (pagerEvent.date.toInstant().epochSecond / (24 * 3600)) % engineers.size
        val engineer = engineers[engineerIndex.toInt()]
        val engineerEmail = emails[engineer]

        // page the engineer
        println("Sending engineer $engineerEmail a high priority notification: $pagerEvent")
        Thread.sleep(1000)

        return engineerEmail
    }
}

class PagerActor: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    private val engineers = listOf("Daniel", "John", "me")
    private val emails =
        mapOf("Daniel" to "daniel@rockthejvm.com", "john" to "john@rockthejvm.com", "me" to "me@rockthejvm.com")

    private fun processEvent(pagerEvent: PagerEvent): String? {
        val engineerIndex = (pagerEvent.date.toInstant().epochSecond / (24 * 3600)) % engineers.size
        val engineer = engineers[engineerIndex.toInt()]
        val engineerEmail = emails[engineer]

        // page the engineer
        log.info("Sending engineer $engineerEmail a high priority notification: $pagerEvent")
        Thread.sleep(1000)

        return engineerEmail
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(PagerEvent::class.java) {
                sender.tell(processEvent(it), self)
            }
            .build()
    }
}

fun genericExtService(element: Any): CompletionStage<Any> {
    TODO()
}
