package akkadocumentation.iotexample.example2

import akka.actor.typed.ActorRef
import akka.actor.typed.ActorSystem

fun main() {
    val example2System: ActorRef<String> = ActorSystem.create(StartStopActor1.create(), "StartStopActor1")
    example2System.tell("stop")
}