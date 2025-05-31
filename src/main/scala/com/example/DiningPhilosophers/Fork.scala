package com.example.DiningPhilosophers

import akka.actor.Actor

object ForkMessages {
  case class TryAcquire(philosopherId: Int)
  case class Release(philosopherId: Int)
  case object AcquireSuccess
  case object AcquireFailed
}

/**
 * Fork actor that can be acquired by one philosopher at a time.
 * Runs in the ForksSystem JVM.
 */
class Fork extends Actor {
  import ForkMessages._
  
  private var holder: Option[Int] = None
  private val forkId = self.path.name.stripPrefix("fork-").toInt
  
  override def preStart(): Unit = {
    println(s"[Fork-$forkId] Started and ready")
  }
  
  def receive: Receive = {
    case TryAcquire(philosopherId) =>
      holder match {
        case None =>
          // Fork is available
          holder = Some(philosopherId)
          sender() ! AcquireSuccess
          println(s"[Fork-$forkId] Acquired by Philosopher $philosopherId")
          
        case Some(currentHolder) =>
          // Fork is already taken
          sender() ! AcquireFailed
          println(s"[Fork-$forkId] Denied to Philosopher $philosopherId (held by $currentHolder)")
      }
    
    case Release(philosopherId) =>
      holder match {
        case Some(currentHolder) if currentHolder == philosopherId =>
          holder = None
          println(s"[Fork-$forkId] Released by Philosopher $philosopherId")
          
        case Some(currentHolder) =>
          println(s"[Fork-$forkId] ERROR: Philosopher $philosopherId tried to release fork held by $currentHolder")
          
        case None =>
          println(s"[Fork-$forkId] ERROR: Philosopher $philosopherId tried to release already free fork")
      }
  }
}
