package playground.akka.streams.part4_techniques

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.stream.OverflowStrategy
import akka.stream.javadsl.Flow
import akka.stream.javadsl.Sink
import akka.stream.javadsl.Source
import akka.util.Timeout
import java.time.Duration
import java.util.*

class IntegratingWithActors {
}

fun main() {
    val system = ActorSystem.create("IntegratingWithActors")
    val simpleActor = system.actorOf(Props.create(SimpleActor::class.java))

    val numberSource = Source.range(1, 10)

    // actor as a flow
    val actorBasedFlow = Flow
        .create<Int>()
        .ask(4, simpleActor, Int::class.java, Timeout.create(Duration.ofSeconds(2)))

    // numberSource.via(actorBasedFlow).runWith(Sink.foreach(::println), system)
    // numberSource.ask(4, simpleActor, Int::class.java, Timeout.create(Duration.ofSeconds(2))) // equivalent

    /*
        Actor as a source
     */
    // first lambda is for to ending stream if we saw some especial message. here we provided nothing.
    // second lambda fails the stream if given message comes. here we provided nothing.
    // example:
    /*
        Source.actorRef(
        elem -> {
          // complete stream immediately if we send it Done
          if (elem == Done.done()) return Optional.of(CompletionStrategy.immediately());
          else return Optional.empty();
        },
        // never fail the stream because of a message
        elem -> Optional.empty(),
        bufferSize,
        OverflowStrategy.dropHead()); // note: backpressure is not supported
     */
    val actorPoweredSource =
        Source.actorRef<Int>({ Optional.empty() }, { Optional.empty() }, 10, OverflowStrategy.dropHead())
    val materializedActorRef =
        actorPoweredSource.to(Sink.foreach { println("Actor powered flow got number: $it") }).run(system)
    materializedActorRef.tell(10, ActorRef.noSender())

    // terminate the stream
    materializedActorRef.tell(akka.actor.Status.Success("complete"), ActorRef.noSender())

    /*
        Actor as a destination/ Sink
        - an init message
        - an ack message to confirm the reception
        - a complete message
        - a function to generate a message in case the stream throws an exception
     */
    val destinationActor = system.actorOf(Props.create(DestinationActor::class.java))
    val actorPoweredSink = Sink.actorRefWithBackpressure<Int>(destinationActor, StreamInit, StreamAck, StreamComplete) {
        StreamFail(it)
    }
    Source.range(1, 10).to(actorPoweredSink).run(system)

    // Sink.actorRef<>() do not use. unable to backpressure.
}

class SimpleActor : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(String::class.java) {
                log.info("just received a string: $it")
                sender.tell("$it$it", self)
            }
            .match(Integer::class.java) {
                log.info("just received a number: $it")
                sender.tell(it.toInt() * 2, self)
            }
            .matchAny {
                log.info(it.javaClass.toString())
                //log.info("just received: $it")
            }
            .build()
    }

}

object StreamInit
object StreamAck
object StreamComplete
data class StreamFail(val ex: Throwable)

class DestinationActor : AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(StreamInit::class.java) {
                log.info("Stream initialized")
                sender.tell(StreamAck, self)
            }
            .match(StreamComplete::class.java) {
                log.info("Stream Complete")
                context.stop(self)
            }
            .match(StreamFail::class.java) {
                log.warning("Stream failed: $it")
            }
            .matchAny {
                log.info("Message $it has come to its final resting point.")
                sender.tell(StreamAck, self) /* we have to send this message when actor is going to be a sink
                    because lack of StreamAck will be interpreted as backpressure
                */
            }
            .build()
    }

}