package akkadocumentation.iotexample.classicactor.example3

import akka.actor.*
import akka.actor.typed.Behavior
import akka.japi.pf.DeciderBuilder
import java.lang.RuntimeException
import java.time.Duration

class SupervisingActor private constructor() : AbstractActor() {
    val child = context.actorOf(SupervisedActor.create(), "SupervisedActor")

    companion object {
        fun create(): Props {
            return Props.create(SupervisingActor::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchEquals("failChild") { onFailChild() }
            .build()
    }

    private fun onFailChild() {
        child.tell("fail", self)
    }

    override fun supervisorStrategy(): SupervisorStrategy {
        return OneForOneStrategy(10,
            Duration.ofMinutes(1),
            DeciderBuilder
                .match(RuntimeException::class.java) { SupervisorStrategy.restart() as SupervisorStrategy.Directive? }
                .build())
    }
}