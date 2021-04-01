package akkadocumentation.iotexample

import akka.actor.typed.ActorRef

data class RequestAllTemperatures(val requestId: Long, val groupId: String, val replyTo: ActorRef<RespondAllTemperatures>)
    : DeviceGroupQuery.Command, DeviceGroup.Command, DeviceManager.Command

data class RespondAllTemperatures(val requestId: Long, val temperatures: Map<String, TemperatureReading>)

interface TemperatureReading

data class Temperature(val value: Double): TemperatureReading

enum class TemperatureNotAvailable: TemperatureReading {
    INSTANCE
}

enum class DeviceNotAvailable: TemperatureReading {
    INSTANCE
}

enum class DeviceTimedOut: TemperatureReading {
    INSTANCE
}