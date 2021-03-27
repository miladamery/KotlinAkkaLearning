package akkadocumentation.iotexample.classicactor.example3

import akka.actor.ActorRef
import akka.actor.ActorSystem

fun main() {
    val example3classicSystem = ActorSystem.create("example3classicSystem")
    val supervisingActor = example3classicSystem.actorOf(SupervisingActor.create(), "SupervisingActor")
    supervisingActor.tell("failChild", ActorRef.noSender())
}