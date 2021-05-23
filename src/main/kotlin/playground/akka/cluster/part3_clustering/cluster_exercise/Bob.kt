package playground.akka.cluster.part3_clustering.cluster_exercise

class Bob {
}

fun main() {
    val alice = ChatApp("Bob", 2552)
    alice.readLineAndSendToCluster()
}