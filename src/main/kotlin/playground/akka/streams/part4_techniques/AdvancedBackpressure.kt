package playground.akka.streams.part4_techniques

import akka.actor.ActorSystem
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import java.time.Duration
import java.util.*
import java.util.stream.Stream

class AdvancedBackpressure {
    data class PagerEvent(val description: String, val date: Date, val nInstances: Int = 1)
    data class Notification(val email: String, val pagerEvent: PagerEvent)
}


fun main() {
    val system = ActorSystem.create("AdvancedBackpressure")
    // control backpressure
    val controlledFlow = Flow.create<Int>().buffer(10, OverflowStrategy.dropHead())
    val events = listOf(
        AdvancedBackpressure.PagerEvent("Service discovery failed", Date()),
        AdvancedBackpressure.PagerEvent("Illegal elements in the date pipeline", Date()),
        AdvancedBackpressure.PagerEvent("Number of HTTP 500 spiked", Date()),
        AdvancedBackpressure.PagerEvent("A service stopped responding", Date()),
    )
    val eventSource = Source.fromIterator { events.iterator() }
    val onCallEngineer = "daniel@rockthejvm.com"
    val notificationSink = Flow
        .create<AdvancedBackpressure.PagerEvent>()
        .map { AdvancedBackpressure.Notification(onCallEngineer, it) }
        .to(Sink.foreach(::sendEmail))

    // standard
    // eventSource.to(notificationSink).run(system)

    /*
        un-backpressurable source
     */
    val aggregateNotificationFlow = Flow
        .create<AdvancedBackpressure.PagerEvent>()
        .conflate { e1, e2 ->
            val nInstances = e1.nInstances + e2.nInstances
            AdvancedBackpressure.PagerEvent(
                "You have $nInstances events that require your attention",
                Date(),
                nInstances
            )
        }
        .map {
            AdvancedBackpressure.Notification(onCallEngineer, it)
        }
    eventSource
        .via(aggregateNotificationFlow)
        .async()
        .to(Sink.foreach { sendEmailSlow(it) })
        .run(system)
    // alternative to backpressure. because sink is slow, conflate method aggregates incoming data from source that
    // sink is not able to process and when sink is ready again it will send reduced form of data (by function we provided)
    // to sink

    /*
        Slow producers: extrapolate/expand
     */
    val slowCounter = Source
        .fromJavaStream { Stream.iterate(1) { it + 1 } }
        .throttle(1, Duration.ofSeconds(1))
    val veryHungrySink = Sink.foreach<Int>(::println)
    /*val extrapolator = Flow.create<Int>().extrapolate {
        Iterator.
    }*/
}

fun sendEmailSlow(notification: AdvancedBackpressure.Notification) {
    Thread.sleep(1000)
    println("Dear ${notification.email}, you have an event: ${notification.pagerEvent}")
}

fun sendEmail(notification: AdvancedBackpressure.Notification) {
    println("Dear ${notification.email}, you have an event: ${notification.pagerEvent}")
}