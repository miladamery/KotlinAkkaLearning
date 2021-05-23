package playground.akka.cluster.part2_remoting.deployingActorRemotely

import akka.actor.*
import akka.event.Logging
import akka.event.LoggingAdapter
import akka.remote.RemoteScope
import akka.routing.FromConfig
import com.typesafe.config.ConfigFactory
import playground.akka.cluster.part2_remoting.SimpleActor

class DeployingActorsRemotelyLocalApp {
}

fun main() {
    val system = ActorSystem.create(
        "LocalActorSystem",
        ConfigFactory.load("part2_remoting/deployingActorsRemotely.conf").getConfig("localApp")
    )
    val simpleActor = system.actorOf(Props.create(SimpleActor::class.java), "remoteActor") // /user/remoteActor
    // simpleActor.tell("Hello! Remote Actor", ActorRef.noSender())

    // actor path of a remotely deployed actor
    println(simpleActor)
    // expected: akka://RemoteActorSystem@localhost:2552/user/remoteActor actual is something else
    // actual: akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/remoteActor

    // programmatic remote deployment
    val remoteSystemAddress = AddressFromURIString.parse("akka://RemoteActorSystem@localhost:2552")
    val remoteDeployedActor =
        system.actorOf(Props.create(SimpleActor::class.java).withDeploy(Deploy(RemoteScope(remoteSystemAddress))))
    // remoteDeployedActor.tell("Hi, remotely deployed actor", ActorRef.noSender())

    // routers with routees deployed remotely
    val poolRouter = system.actorOf(
        FromConfig.getInstance().props(Props.create(SimpleActor::class.java)),
        "myRouterWithRemoteChildren"
    )
    // (1..10).map { "message $it" }.forEach { poolRouter.tell(it, ActorRef.noSender()) }

    // watching remote actors
    val parentActor = system.actorOf(Props.create(ParentActor::class.java), "watcher")
    parentActor.tell("create", ActorRef.noSender())
    Thread.sleep(1000)
    system
        .actorSelection("akka://RemoteActorSystem@localhost:2552/remote/akka/LocalActorSystem@localhost:2551/user/watcher/remoteChild")
        .tell(PoisonPill::class.java, ActorRef.noSender())
}

class ParentActor: AbstractActor() {
    private val log: LoggingAdapter = Logging.getLogger(context.system, this)
    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("create") {
                log.info("Create remote child")
                val child = context.actorOf(Props.create(SimpleActor::class.java), "remoteChild")
                context.watch(child)
            }
            .match(Terminated::class.java) {
                log.info("Child ${it.actor} terminated")
            }
            .build()
    }

}