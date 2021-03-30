package playground.akka.essentials.part5infra

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import com.typesafe.config.ConfigFactory
import java.util.*

class Dispatchers {
    class Counter : AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        private var count = 0
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny { msg ->
                    count += 1
                    log.info("${self.path()} [$count] $msg")
                }
                .build()
        }

    }

    class DBActor: AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny {
                    Thread.sleep(50000)
                    log.info("Success: $it")
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("DispatchersDemo") // ConfigFactory.load().getConfig("dispatchersDemo")

    /**
     * Method #1 - programmatic/in code
     */
    val actors = (1..10).map {
        system.actorOf(Props.create(Dispatchers.Counter::class.java).withDispatcher("my-dispatcher"), "counter_$it")
    }
    val r = Random()
    (1..1000).map {
        actors[r.nextInt(10)].tell(it, ActorRef.noSender())
    }

    /**
     * Method #2 - from config
     */
    val rtjvmActor = system.actorOf(Props.create(Dispatchers.Counter::class.java), "rtjvm") // name should be same as in config

    /**
     * Dispatchers implement the ExecutionContext trait
     */


}