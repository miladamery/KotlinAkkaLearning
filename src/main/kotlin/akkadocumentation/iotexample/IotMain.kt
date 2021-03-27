package akkadocumentation.iotexample

import akka.actor.typed.ActorSystem

fun main() {
    val IotSystem = ActorSystem.create(IotSupervisor.create(), "IotSystem")
}