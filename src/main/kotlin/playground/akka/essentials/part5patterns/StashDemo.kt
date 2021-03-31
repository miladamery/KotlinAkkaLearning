package playground.akka.essentials.part5patterns

import akka.actor.*
import akka.event.Logging

class StashDemo {

    /*
        ResourceActor
            - open => it can receive read/write requests to the resource
            - otherwise it will postpone all read/writes until state is open

        ResourceActor is closed
            - Open => switch to the open state
            - Read,Write messages are POSTPONED

        ResourceActor is Open
            - Read, Write are handled
            - Close => witch to close state

        [Open, Read, Read, Write]
        - switch to open state
        - read the data
        - read the data again
        - write the data

        [Read, Open, Write]
        - stash Read
          Stash: [Read]
        - open => switch to the open state
          Mailbox: [Read, Write]
        - read and write are handled
     */
    object Open
    object Close
    object Read
    data class Write(val data: String)
    // step1 - mix-in the stash
    class ResourceActor : AbstractActorWithStash() {
        private val log = Logging.getLogger(context.system, this)
        private var innerData: String = ""
        override fun createReceive(): Receive = closed()

        private fun closed(): Receive {
            return receiveBuilder()
                .match(Open::class.java) {
                    log.info("Opening resource")
                    // step 3 - Unstash all when you switch the message handler
                    unstashAll()
                    context.become(open())
                }
                .matchAny {
                    log.info("Stashing $it because i cant handle it in the closed state")
                    // step 2 - stash away what you can't handle
                    stash()
                }
                .build()
        }

        private fun open(): Receive {
            return receiveBuilder()
                .match(Read::class.java) {
                    // do some actual computation
                    log.info("I have read $it")
                }
                .match(Write::class.java) {
                    log.info("I am writing $innerData")
                    innerData = it.data
                }
                .match(Close::class.java) {
                    log.info("Closing resource")
                    context.become(closed())
                }
                .matchAny {
                    log.info("Stashing $it because i cant handle it in the open state")
                    stash()
                }
                .build()
        }
    }
}

fun main() {
    val system = ActorSystem.create("StashDemo")
    val resourceActor = system.actorOf(Props.create(StashDemo.ResourceActor::class.java))

    /*resourceActor.tell(StashDemo.Write("I love stash"), ActorRef.noSender())
    resourceActor.tell(StashDemo.Read, ActorRef.noSender())
    resourceActor.tell(StashDemo.Open, ActorRef.noSender())*/

    resourceActor.tell(StashDemo.Read, ActorRef.noSender()) // stashed
    resourceActor.tell(StashDemo.Open, ActorRef.noSender()) // switch open; i have read ""
    resourceActor.tell(StashDemo.Open, ActorRef.noSender()) // stashed
    resourceActor.tell(StashDemo.Write("I love stash"), ActorRef.noSender()) // i am writing i love stash
    resourceActor.tell(StashDemo.Close, ActorRef.noSender())// switch to closed; switch to open again
    resourceActor.tell(StashDemo.Read, ActorRef.noSender()) // i have read i love stash
}