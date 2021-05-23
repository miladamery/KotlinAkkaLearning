package playground.akka.cluster.part2_remoting.deployingActorRemotely

import akka.actor.ActorSystem
import com.typesafe.config.ConfigFactory

class DeployingActorsRemotelyRemoteApp {
}

fun main() {
    val system = ActorSystem.create("RemoteActorSystem", ConfigFactory.load("part2_remoting/deployingActorsRemotely.conf").getConfig("remoteApp"))
}