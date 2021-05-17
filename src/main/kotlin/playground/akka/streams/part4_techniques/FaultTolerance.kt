package playground.akka.streams.part4_techniques

import akka.actor.ActorSystem
import akka.stream.ActorAttributes
import akka.stream.RestartSettings
import akka.stream.Supervision
import akka.stream.javadsl.RestartSource
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import java.time.Duration
import java.util.*

class FaultTolerance {
}

fun main() {
    val system = ActorSystem.create("FaultTolerance")

    // 1 - logging
    val faultySource = Source.range(1, 10).map { if (it == 6) throw RuntimeException() else it }
    faultySource.log("trackingElements").to(Sink.ignore())//.run(system)

    // 2 - gracefully terminating a stream
    faultySource.recover(RuntimeException::class.java) {
        Int.MIN_VALUE
    }
        .log("gracefulSource")
        .to(Sink.ignore())
    //.run(system)

    // 3 - recover with another stream
    faultySource.recoverWithRetries(3, RuntimeException::class.java) {
        Source.range(90, 99)
    }
        .log("recoverWithRetries")
        .to(Sink.ignore())
    //.run(system)

    // 4 - backoff supervision
    val rc = RestartSettings.create(Duration.ofSeconds(1), Duration.ofSeconds(30), 0.2)
    val restartSource = RestartSource.onFailuresWithBackoff(rc) {
        val randomNumber = Random().nextInt(20)
        Source.range(1, 10).map { if (it == randomNumber) throw RuntimeException() else it }
    }
        .log("restartBackoff")
        .to(Sink.ignore())
    //.run(system)

    // 5 - supervision strategy
    /*
            Resume = skips the faulty element
            Stop = stop the stream
            Restart = resume + clears internal state ( clearing folds, zip and anything like that)
         */
    val numbers = Source.range(1, 20).map { if (it == 12) throw RuntimeException() else it }.log("supervision")
    numbers.withAttributes(ActorAttributes.supervisionStrategy {
        if (it is RuntimeException)
            Supervision.resume() as Supervision.Directive
        else
            Supervision.stop() as Supervision.Directive
    })
        .to(Sink.ignore())
        .run(system)


}