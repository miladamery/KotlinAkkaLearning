package akkadocumentation.iotexample.example3

import akka.actor.typed.ActorSystem

fun main() {
    val example3System = ActorSystem.create(SupervisingActor.create(), "example3System")
    example3System.tell("failChild")
}