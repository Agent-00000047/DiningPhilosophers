package com.example.DiningPhilosophers

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Forks JVM - Creates and manages all fork actors
 * Run this first before starting the philosophers
 */
object ForksApp extends App {

  val n: Int = if (args.length > 0) args(0).toInt else 5
  require(n >= 2, s"Need at least 2 philosophers/forks, but got $n")
  
  println(s"Starting Forks System with $n forks")
  println("Press ENTER to stop the forks system...")
  
  // Fork system configuration with remote capabilities
  val forks_port: Int = 2552
  val forks_config = ConfigFactory
    .parseString(
      s"""
      akka {
        actor {
          provider = "akka.remote.RemoteActorRefProvider"
          
          serializers {
            jackson-json = "akka.serialization.jackson.JacksonJsonSerializer"
          }

          serialization-bindings {
            "com.example.DiningPhilosophers.ForkMessages$$TryAcquire" = jackson-json
            "com.example.DiningPhilosophers.ForkMessages$$Release" = jackson-json
            "com.example.DiningPhilosophers.ForkMessages$$AcquireSuccess$$" = jackson-json
            "com.example.DiningPhilosophers.ForkMessages$$AcquireFailed$$" = jackson-json
          
            "com.example.DiningPhilosophers.PhilosopherMessages$$StartThinking$$" = jackson-json
            "com.example.DiningPhilosophers.PhilosopherMessages$$FinishedThinking$$" = jackson-json
            "com.example.DiningPhilosophers.PhilosopherMessages$$TryToEat$$" = jackson-json
            "com.example.DiningPhilosophers.PhilosopherMessages$$StartEating$$" = jackson-json
            "com.example.DiningPhilosophers.PhilosopherMessages$$FinishedEating$$" = jackson-json
            "com.example.DiningPhilosophers.PhilosopherMessages$$RetryLater$$" = jackson-json
          }
        }
        
        remote {
          artery {
            canonical.hostname = "localhost"
            canonical.port = $forks_port
          }
        }
        
        # Reduce log noise
        loglevel = "INFO"
        stdout-loglevel = "INFO"
        log-dead-letters = 10
        log-dead-letters-during-shutdown = off
      }
      """)

  val forks_system = ActorSystem("ForksSystem", forks_config)

  try {
    // Create all fork actors
    val forks: Seq[ActorRef] =
      (0 until n).map(i => forks_system.actorOf(Props[Fork], s"fork-$i"))
    
    println(s"Created $n forks:")
    forks.zipWithIndex.foreach { case (fork, i) =>
      println(s"  Fork $i: ${fork.path}")
    }
    println()
    println("Forks system is ready and waiting for philosophers...")

    Await.ready(forks_system.whenTerminated, Duration.Inf)

  } finally {
    println("Shutting down forks system...")
    forks_system.terminate()
  }
}