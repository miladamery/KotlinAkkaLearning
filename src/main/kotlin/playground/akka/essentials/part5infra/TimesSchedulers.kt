package playground.akka.essentials.part5infra

import akka.actor.*
import akka.event.Logging
import java.time.Duration

class TimesSchedulers {
    class SimpleActor : AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchAny { log.info(it.toString()) }
                .build()
        }

    }

    class SelfClosingActor : AbstractActor() {
        private val log = Logging.getLogger(context.system, this)
        private var schedule = createTimeoutWindow()
        override fun createReceive(): Receive {
            return receiveBuilder()
                .matchEquals("timeout") {
                    log.info("Stopping myself")
                    context.stop(self)
                }
                .matchAny {
                    log.info("Received $it, staying alive")
                    schedule.cancel()
                    schedule = createTimeoutWindow()
                }
                .build()
        }

        private fun createTimeoutWindow(): Cancellable {
            return context.system.scheduler.scheduleOnce(
                Duration.ofSeconds(1),
                { self.tell("timeout", self) },
                context.system.dispatcher
            )
        }
    }

    object TimerKey
    object Start
    object Reminder
    object Stop

    class TimerBasedHeartBeatActor : AbstractActorWithTimers() {

        init {
            timers.startSingleTimer(TimerKey, Start, Duration.ofMillis(500))
        }

        private val log = Logging.getLogger(context.system, this)
        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(Start::class.java) {
                    log.info("Bootstrapping")
                    // When we define another timers with the same key, the previous times (s) will be canceled automatically
                    timers.startPeriodicTimer(TimerKey, Reminder, Duration.ofSeconds(1))
                }
                .match(Reminder::class.java) {
                    log.info("I am alive")
                }
                .match(Stop::class.java) {
                    log.warning("Stopping!")
                    timers.cancel(TimerKey)
                    context.stop(self)
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("SchedulersTimersDemo")
    /*val simpleActor = system.actorOf(Props.create(TimesSchedulers.SimpleActor::class.java))

    system.log().info("Scheduling reminder of simpleActor")


    system.scheduler.scheduleOnce(
        Duration.ofSeconds(1),
        { simpleActor.tell("reminder", ActorRef.noSender()) },
        system.dispatcher
    )

    val routine: Cancellable = system.scheduler.schedule(
        Duration.ofSeconds(1),
        Duration.ofSeconds(2),
        { simpleActor.tell("heartbeat", ActorRef.noSender()) },
        system.dispatcher
    )*/

    /*system.scheduler.scheduleOnce(Duration.ofSeconds(5), { routine.cancel() }, system.dispatcher)*/

    /**
     * Exercise: implement a self-closing actor
     *
     * - if the actor receives a message (anything) you have one second to send it another message
     * - if the time window expires, the actor will stop itself
     * - if you send another message, the time window is reset
     */

    /*val selfClosingActor = system.actorOf(Props.create(TimesSchedulers.SelfClosingActor::class.java))
    system.scheduler.scheduleOnce(
        Duration.ofMillis(250),
        { selfClosingActor.tell("ping", ActorRef.noSender()) },
        system.dispatcher
    )

    system.scheduler.scheduleOnce(
        Duration.ofSeconds(2),
        { selfClosingActor.tell("pong", ActorRef.noSender()) },
        system.dispatcher
    )*/

    val timerBasedHeartBeatActor = system.actorOf(Props.create(TimesSchedulers.TimerBasedHeartBeatActor::class.java))
    system.scheduler.scheduleOnce(
        Duration.ofSeconds(5),
        { timerBasedHeartBeatActor.tell(TimesSchedulers.Stop, ActorRef.noSender()) },
        system.dispatcher
    )
}