package playground.akka.cluster.part2_remoting.remoteActors

import akka.actor.*
import akka.event.Logging
import akka.event.LoggingAdapter
import com.typesafe.config.ConfigFactory
import playground.akka.cluster.part2_remoting.SimpleActor
import java.time.Duration
import java.util.concurrent.CompletableFuture

class RemoteActors {
}

fun main() {
    val localSystem = ActorSystem.create("localSystem", ConfigFactory.load("part2_remoting/remoteActors.conf"))
    val localSimpleActor = localSystem.actorOf(Props.create(SimpleActor::class.java), "localSimpleActor")
    localSimpleActor.tell("Hello, local actor", ActorRef.noSender())

    // send a message to the REMOTE simple actor

    // Method 1: actor selection
    val remoteActorSelection = localSystem.actorSelection("akka://remoteSystem@localhost:2552/user/remoteSimpleActor")
    remoteActorSelection.tell("Hello from the local JVM", ActorRef.noSender())

    // Method 2: resolve the actor selection to an actor ref
    val remoteActorRef = remoteActorSelection.resolveOne(Duration.ofSeconds(3))
    (remoteActorRef as CompletableFuture).get().tell("I've resolved you in a future!", ActorRef.noSender())

    localSystem.actorOf(Props.create(ActorResolver::class.java))
    // Method 3: Actor identification via messages
    /*
        - actor resolver will ask for an actor selection from the local actor system
        - actor resolver will send a Identify(42) to the actor selection
        - the remote actor will AUTOMATICALLY respond with ActorIdentity(42, actorRef)
        - the actor resolver is free to use the remote actorRef
     */
}

class ActorResolver: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)

    override fun preStart() {
        val selection = context.actorSelection("akka://remoteSystem@localhost:2552/user/remoteSimpleActor")
        selection.tell(Identify(42), self)

    }
    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(ActorIdentity::class.java) {
                if (it.actorRef.isPresent)
                    it.actorRef.get().tell("Thank you for identifying yourself!", self)
            }
            .build()
    }
}