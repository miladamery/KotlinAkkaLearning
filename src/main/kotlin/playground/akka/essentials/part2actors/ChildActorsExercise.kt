package playground.akka.essentials.part2actors

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props

fun main() {
    // Distributed Word Counting

    /*
        create WordCounterMaster
        send Initialize(10) to wordCounterMaster
        send "Akka is awesome" to wordCounterMaster
            wcm will send a WordCountTask("...") to one of its children
                child replies with a WordCountReply(3) to the master
            master replies with 3 to master

        requester -> wcm -> wcw
                  <- wcm <-
     */
    // round robin logic
    // 1,2,3,4,5 and 7 tasks
    // 1,2,3,4,5,1,2
    val system = ActorSystem.create("ChildActorExerciseSystem")
    val wcm = system.actorOf(WordCounterMaster.create(), "wcm")
    val requester = system.actorOf(WordCounterRequester.create(), "requester")

    wcm.tell(Initialize(3), ActorRef.noSender())
    val texts = listOf("I Love Akka", "Scala is super dope", "yes", "me too")
    texts.forEach {
        wcm.tell(it, requester)
    }
}

class WordCounterMaster : AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(WordCounterMaster::class.java)
        }
    }

    override fun createReceive(): Receive = wordCounterMasterReceive()

    private fun wordCounterMasterReceive(
        children: List<ActorRef> = listOf(),
        workerIndex: Int? = null,
        currentTaskId: Int = 0,
        requestMap: Map<Int, ActorRef> = mapOf()
    ): Receive {
        return receiveBuilder()
            .match(Initialize::class.java) { initialize ->
                println("Initializing...")
                var newChildren = mutableListOf<ActorRef>()
                (1..initialize.nChildren).forEach {
                    newChildren.add(context.actorOf(WordCounterWorker.create(), "worker_$it"))
                }
                context.become(wordCounterMasterReceive(newChildren.toList(), 0))
            }
            .match(String::class.java) {
                if (children.isNotEmpty() && workerIndex != null) {
                    var workerIndexForList = if (workerIndex > children.size - 1) 0 else workerIndex
                    println("[master] i have received: $it - i will send it to child $workerIndexForList")
                    children[workerIndexForList].tell(WordCountTask(currentTaskId, it), self)
                    val newRequestMap = requestMap + (currentTaskId to sender)
                    context.become(
                        wordCounterMasterReceive(
                            children,
                            workerIndexForList + 1,
                            currentTaskId + 1,
                            newRequestMap
                        )
                    )
                }
            }
            .match(WordCountReply::class.java) {
                println("[master] i hav received a reply for task id ${it.id} with ${it.count}")
                requestMap[it.id]?.tell(it.count, self)
                context.become(
                    wordCounterMasterReceive(
                        children,
                        workerIndex,
                        currentTaskId,
                        requestMap - currentTaskId
                    )
                )
            }
            .build()
    }

}

class WordCounterWorker : AbstractActor() {

    companion object {
        fun create(): Props {
            return Props.create(WordCounterWorker::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .match(WordCountTask::class.java) {
                println("${self.path()} I have received task ${it.id} with: ${it.text}")
                sender.tell(WordCountReply(it.id, it.text.split(" ").size), self)
            }
            .build()
    }
}

class WordCounterRequester : AbstractActor() {
    companion object {
        fun create(): Props {
            return Props.create(WordCounterRequester::class.java)
        }
    }

    override fun createReceive(): Receive {
        return receiveBuilder()
            .matchAny {
                println("Requester received: $it")
            }
            .build()
    }

}

data class Initialize(val nChildren: Int)
data class WordCountTask(val id: Int, val text: String)
data class WordCountReply(val id: Int, val count: Int)