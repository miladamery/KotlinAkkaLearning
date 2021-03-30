package playground.akka.essentials.part5infra

import akka.actor.AbstractActor
import akka.actor.ActorSystem
import akka.actor.Props
import akka.actor.Terminated
import akka.event.Logging
import akka.routing.*
import com.typesafe.config.ConfigFactory

class Routers {
    /**
     * #1 - manual router
     */
    class Master: AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        // Step 1: Create Routees
        // 5 actor routees based off Slave actors
        private val slaves = (1..5).map {
            val slave = context.actorOf(Props.create(Slave::class.java), "Slave_$it")
            context.watch(slave)
            ActorRefRoutee(slave)
        }
        // Step 2: Define router
        private var router = Router(RoundRobinRoutingLogic(), slaves)

        override fun createReceive(): Receive {
            return receiveBuilder()
                // Step 3 - route the messages
                .matchAny {
                    router.route(it, sender)
                }
                // Step 4 - handle termination/lifecycle of the routees
                .match(Terminated::class.java) {
                    var router = router.removeRoutee(it.actor) // immutable operation - returns a new router
                    val newSlave = context.actorOf(Props.create(Slave::class.java))
                    context.watch(newSlave)
                    router = router.addRoutee(newSlave) // immutable operation - returns a new router
                    this.router = router
                }
                .build()
        }
    }

    class Slave: AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny {
                    log.info("${self.path()} $it")
                }
                .build()
        }
    }
}

fun main() {
    val system = ActorSystem.create("RoutersDemo", ConfigFactory.load().getConfig("routersDemo"))
    val master = system.actorOf(Props.create(Routers.Master::class.java))
    (1..10).map {
        //master.tell("[$it] Hello from the world", master)
    }

    /**
     * #2 - a router actor with its children
     * POOL router
     */
    // method 2.1 programmatically (in code)
    val poolMaster =
        system.actorOf(RoundRobinPool(5).props(Props.create(Routers.Slave::class.java)), "simplePoolMaster")
    (1..10).map {
        //poolMaster.tell("[$it] Hello from the world", poolMaster)
    }

    // method 2.2 from configuration
    val poolMaster2 = system.actorOf(FromConfig.getInstance().props(Props.create(Routers.Slave::class.java)), "poolMaster2")
    (1..10).map {
        //poolMaster2.tell("[$it] Hello from the world", poolMaster)
    }

    /**
     * Method #3 - routers with actors created else where
     * GROUP router
     */
    // .. in another part of my application
    val slaveList = (1..5).map { system.actorOf(Props.create(Routers.Slave::class.java), "slave_$it") }

    // need their actor path
    val slavePath = slaveList.map { it.path().toString() }

    // 3.1 in code
    val groupMaster = system.actorOf(RoundRobinGroup(slavePath).props())
    (1..10).map {
        //groupMaster.tell("[$it] Hello from the world", poolMaster)
    }

    // 3.2 From configuration
    val groupMaster2 = system.actorOf(FromConfig.getInstance().props(), "groupMaster2")
    (1..10).map {
        groupMaster.tell("[$it] Hello from the world", poolMaster)
    }

    /**
     * Special Messages
     */
    groupMaster2.tell(Broadcast("Hello, everyone"), groupMaster2)

    // PoisonPill and Kill are NOT routed
    // AddRoutee, Remove, Get handled only by the routing actor
}

// RoundRobinRoutingLogic - RoundRobinPool
// TailChoppingRoutingLogic - TailChoppingPool
// RandomRoutingLogic - RandomPool
// etc