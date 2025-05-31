package com.example.DiningPhilosophers

import akka.actor.{Actor, ActorSelection, Cancellable}
import akka.util.Timeout
import akka.pattern.ask
import scala.concurrent.duration._
import scala.util.{Random, Success, Failure}

object PhilosopherMessages {
  case object StartThinking
  case object FinishedThinking
  case object TryToEat
  case object StartEating
  case object FinishedEating
  case object RetryLater
}

/**
 * Philosopher actor that communicates with remote fork actors.
 * Uses the classic deadlock-prone strategy: left fork first, then right fork.
 * Runs in the PhilosophersSystem JVM.
 */
class Philosopher(id: Int, leftFork: ActorSelection, rightFork: ActorSelection) extends Actor {
  
  import PhilosopherMessages._
  import ForkMessages._
  import context.dispatcher
  
  // Configuration
  implicit val timeout: Timeout = Timeout(5.seconds)
  private def thinkingTime: FiniteDuration = 3000.millis
  private def eatingTime: FiniteDuration = 2000.millis
  private def retryDelay: FiniteDuration = 1000.millis
  
  // State tracking
  private var hasLeftFork = false
  private var hasRightFork = false
  private var currentTask: Option[Cancellable] = None
  
  override def preStart(): Unit = {
    println(s"[Philosopher-$id] Started and ready to think")
  }
  
  override def postStop(): Unit = {
    currentTask.foreach(_.cancel())
    // Release any held forks
    if (hasLeftFork) leftFork ! Release(id)
    if (hasRightFork) rightFork ! Release(id)
  }
  
  def receive: Receive = {
    case StartThinking =>
      println(s"[Philosopher-$id] Started thinking...")
      scheduleMessage(thinkingTime, FinishedThinking)
      
    case FinishedThinking =>
      println(s"[Philosopher-$id] Finished thinking, now hungry!")
      self ! TryToEat
      
    case TryToEat =>
      tryToAcquireForks()
      
    case StartEating =>
      println(s"[Philosopher-$id] Got both forks, started eating!")
      scheduleMessage(eatingTime, FinishedEating)
      
    case FinishedEating =>
      println(s"[Philosopher-$id] Finished eating, releasing forks")
      releaseBothForks()
      self ! StartThinking
      
    case RetryLater =>
      println(s"[Philosopher-$id] Retrying to acquire forks...")
      self ! TryToEat
  }
  
  /**
   * Classic deadlock-prone strategy: always try left fork first
   */
  private def tryToAcquireForks(): Unit = {
    if (!hasLeftFork) {
      println(s"[Philosopher-$id] Trying to acquire left fork...")
      val leftFuture = (leftFork ? TryAcquire(id))
      leftFuture.onComplete {
        case Success(AcquireSuccess) =>
          hasLeftFork = true
          println(s"[Philosopher-$id] Successfully acquired left fork")
          self ! TryToEat // Try for right fork next
          
        case Success(AcquireFailed) =>
          println(s"[Philosopher-$id] Failed to acquire left fork, will retry")
          scheduleMessage(retryDelay, RetryLater)

        case Success(other) =>
          println(s"[Philosopher-$id] Unexpected response when acquiring left fork: $other")
          scheduleMessage(retryDelay, RetryLater)
          
        case Failure(exception) =>
          println(s"[Philosopher-$id] Error acquiring left fork: ${exception.getMessage}")
          scheduleMessage(retryDelay, RetryLater)
      }
      
    } else if (!hasRightFork) {
      println(s"[Philosopher-$id] Trying to acquire right fork...")
      val rightFuture = (rightFork ? TryAcquire(id))
      rightFuture.onComplete {
        case Success(AcquireSuccess) =>
          hasRightFork = true
          println(s"[Philosopher-$id] Successfully acquired right fork")
          self ! StartEating // Both forks acquired!
          
        case Success(AcquireFailed) =>
          println(s"[Philosopher-$id] Failed to acquire right fork, Retrying to acquire it")
          scheduleMessage(retryDelay, RetryLater)

        case Success(other) =>
          println(s"[Philosopher-$id] Unexpected response when acquiring right fork: $other")
          scheduleMessage(retryDelay, RetryLater)
          
        case Failure(exception) =>
          println(s"[Philosopher-$id] Error acquiring right fork: ${exception.getMessage}")
          scheduleMessage(retryDelay, RetryLater)
      }
      
    } else {
      // Both forks already acquired
      self ! StartEating
    }
  }
  
  private def releaseBothForks(): Unit = {
    if (hasLeftFork) {
      leftFork ! Release(id)
      hasLeftFork = false
    }
    if (hasRightFork) {
      rightFork ! Release(id)
      hasRightFork = false
    }
  }
  
  private def scheduleMessage(delay: FiniteDuration, message: Any): Unit = {
    currentTask.foreach(_.cancel())
    currentTask = Some(
      context.system.scheduler.scheduleOnce(delay) {
        self ! message
      }
    )
  }
}