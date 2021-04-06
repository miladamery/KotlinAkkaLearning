package playground.akka.persistence.part2_event_sourcing

import akka.actor.AbstractLoggingActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import java.io.Serializable

class PersistAsyncDemo {

    data class Command(val contents: String)
    data class Event(val contents: String): Serializable

    class CriticalStreamProcessor(val eventAggregator: ActorRef): AbstractPersistentActor() {
        private val log = Logging.getLogger(this)

        companion object {
            fun create(eventAggregator: ActorRef): Props {
                return Props.create(CriticalStreamProcessor::class.java, eventAggregator)
            }
        }

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Command::class.java) {
                    eventAggregator.tell("Processing ${it.contents}", self)
                    /* TIME GAP
                    * In The time gap defined below messages will be stashed if we user Persist
                    * This is not true for PersistAsync. this is good if Time Gap is large for given operation
                    * because it wont hold message in stash and just process given message.
                    * but draw back is persisting event and handling the call back wont be atomic for given command anymore.
                    */
                    persistAsync(Event(it.contents)) /* TIME GAP*/ { event ->
                        eventAggregator.tell(event, self)

                    }

                    // some actual computation
                    val processedContents = it.contents + "_processed"
                    persistAsync(Event(processedContents)) /* TIME GAP*/ { event ->
                        eventAggregator.tell(event, self)
                    }
                }
                .build()
        }

        override fun persistenceId(): String = "critical-stream-processor"

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .matchAny {
                    log.info("Recovered: $it")
                }
                .build()
        }
    }

    class EventAggregator: AbstractLoggingActor() {

        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny {
                    log().info("$it")
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("PersistAsyncDemo")
    val eventAggregator = system.actorOf(Props.create(PersistAsyncDemo.EventAggregator::class.java))
    val streamProcessor = system.actorOf(PersistAsyncDemo.CriticalStreamProcessor.create(eventAggregator), "streamProcessor")

    streamProcessor.tell(PersistAsyncDemo.Command("Command1"), ActorRef.noSender())
    streamProcessor.tell(PersistAsyncDemo.Command("Command2"), ActorRef.noSender())

    /*
        persistAsync vs persist
        - performance: high-throughput

        persist vs persistAsync
        - ordering guarantees
     */
}