package playground.akka.essentials.part5infra

import akka.actor.AbstractActor
import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.dispatch.ControlMessage
import akka.dispatch.PriorityGenerator
import akka.dispatch.UnboundedPriorityMailbox
import akka.event.Logging
import com.typesafe.config.Config
import com.typesafe.config.ConfigFactory

class Mailboxes {
    class SimpleActor : AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny {
                    log.info(it.toString())
                }
                .build()
        }
    }

    // Step 1 - mailbox definition
    // constructor parameters are necessary for akka it self. they get created via reflection
    class SupportTicketPriorityMailBox(val settings: ActorSystem.Settings, val config: Config) :
        UnboundedPriorityMailbox(object : PriorityGenerator() {
            override fun gen(message: Any?): Int {
                //val msg = message as String
                return when {
                    message.toString().startsWith("[P0]") -> 0
                    message.toString().startsWith("[P1]") -> 1
                    message.toString().startsWith("[P2]") -> 2
                    message.toString().startsWith("[P3]") -> 3
                    else -> 4
                }
            }
        })
    // step 2  - make it knows in the config
    // step 3 - attach the dispatcher to an actor

    //Interesting case #2
    // step 1 - mark important messages as control messages
    object ManagementTicket : ControlMessage

    /*
        step 2 - configure who gets the mailbox
        - make the actor attach to the mailbox
     */

}

fun main() {
    val system = ActorSystem.create("MailboxDemo", ConfigFactory.load().getConfig("mailboxesDemo"))

    /**
     * Interesting case #1 - custom priority mailbox
     * P0 -> most important
     * P1
     * P2
     * P3
     */
    val supportTicketLogger =
        system.actorOf(Props.create(Mailboxes.SimpleActor::class.java).withDispatcher("support-ticket-dispatcher"))
    /*supportTicketLogger.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    supportTicketLogger.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    supportTicketLogger.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    supportTicketLogger.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    supportTicketLogger.tell("[P0] this thing needs to be solved now", ActorRef.noSender())
    supportTicketLogger.tell("[P1] do this when you have the time", ActorRef.noSender())*/

    // after which time can i send another message and be prioritized accordingly?
    // neither can you know nor you can figure the wait

    /**
     * Interesting case #2 - control-aware mailbox
     * Problem to solve: some messages need to be processed first regardless of what has been queued in mailbox.
     * we use UnboundedControlAwareMailBox
     */

    /*
        step 2 - configure who gets the mailbox
        - make the actor attach to the mailbox
     */
    // method #1
    val controlAwareActor =
        system.actorOf(Props.create(Mailboxes.SimpleActor::class.java).withMailbox("control-mailbox"))
    /*controlAwareActor.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    controlAwareActor.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    controlAwareActor.tell("[P0] this thing needs to be solved now", ActorRef.noSender())
    controlAwareActor.tell("[P1] do this when you have the time", ActorRef.noSender())
    controlAwareActor.tell(Mailboxes.ManagementTicket, ActorRef.noSender())*/

    // method #2 - using deployment config
    val altControlAwareActor = system.actorOf(Props.create(Mailboxes.SimpleActor::class.java), "altControlAwareActor")
    altControlAwareActor.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    altControlAwareActor.tell("[P3] this thing would be nice to have", ActorRef.noSender())
    altControlAwareActor.tell("[P0] this thing needs to be solved now", ActorRef.noSender())
    altControlAwareActor.tell("[P1] do this when you have the time", ActorRef.noSender())
    altControlAwareActor.tell(Mailboxes.ManagementTicket, ActorRef.noSender())
}