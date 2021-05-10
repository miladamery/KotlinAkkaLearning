package playground.akka.persistence.part4_practices

import akka.actor.ActorRef
import akka.actor.ActorSystem
import akka.actor.Props
import akka.event.Logging
import akka.persistence.AbstractPersistentActor
import akka.persistence.journal.EventAdapter
import akka.persistence.journal.EventSeq
import com.typesafe.config.ConfigFactory
import java.io.Serializable

object DomainModel {
    data class User(val id: String, val email: String): Serializable
    data class Coupon(val code: String, val promotionAmount: Int)

    //commands
    data class ApplyCoupon(val coupon: Coupon, val user: User)
    //events
    data class CouponApplied(val code: String, val user: User): Serializable
}

object DataModel {
    data class WrittenCouponApplied(val code: String, val userId: String, val userEmail: String): Serializable
}

class ModelAdapter: EventAdapter {
    override fun manifest(event: Any?): String = "CMA"

    // actor -> toJournal -> serializer -> journal
    override fun toJournal(event: Any?): Any {
        return if (event is DomainModel.CouponApplied) {
            println("Converting $event to DATA model")
            DataModel.WrittenCouponApplied(event.code, event.user.id, event.user.email)
        } else {
            throw RuntimeException()
        }
    }

    // journal -> serializer -> fromJournal -> to the actor
    override fun fromJournal(event: Any?, manifest: String?): EventSeq {
        return if (event is DataModel.WrittenCouponApplied) {
            println("Converting $event to DOMAIN model")
            EventSeq.single(DomainModel.CouponApplied("1", DomainModel.User(event.userId, event.userEmail)))
        } else {
            EventSeq.single(event)
        }
    }
}

class DetachingModels {

    class CouponManager: AbstractPersistentActor() {
        private val log = Logging.getLogger(this)
        private val coupons = mutableMapOf<String, DomainModel.User>()

        override fun persistenceId(): String = "coupon-manager"

        override fun createReceive(): Receive {
            return receiveBuilder()
                .match(DomainModel.ApplyCoupon::class.java) {
                    if (!coupons.containsKey(it.coupon.code)) {
                        persist(DomainModel.CouponApplied(it.coupon.code, it.user)) { ca ->
                            log.info("Persisted: $ca")
                            println("Persisted: $ca")
                            coupons[ca.code] = ca.user
                        }
                    }
                }
                .build()
        }

        override fun createReceiveRecover(): Receive {
            return receiveBuilder()
                .match(DomainModel.CouponApplied::class.java) {
                    log.info("Recovered $it")
                    coupons[it.code] = it.user
                }
                .build()
        }

    }
}

fun main() {
    val system = ActorSystem.create("DetachingModels", ConfigFactory.load().getConfig("detachingModels"))
    val couponManager = system.actorOf(Props.create(DetachingModels.CouponManager::class.java))

    val coupons = (1..5).map { DomainModel.Coupon("MEGA_COUPON_$it", 100) }
    val users = (1..5).map { DomainModel.User("$it", "user_$it@rtjvm.com")}

    /*(0..4).forEach {
        couponManager.tell(DomainModel.ApplyCoupon(coupons[it], users[it]), ActorRef.noSender())
    }*/
}