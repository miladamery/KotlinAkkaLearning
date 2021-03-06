package playground.akka.cluster.part2_remoting

import akka.actor.AbstractActor
import akka.event.Logging
import akka.event.LoggingAdapter
import java.io.Serializable

class SimpleActor: AbstractActor(), Serializable {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny {
                log.info("Received $it from $sender")
            }
            .build()
    }
}