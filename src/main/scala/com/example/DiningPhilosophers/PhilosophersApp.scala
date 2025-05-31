package com.example.DiningPhilosophers

import akka.actor.{ActorRef, ActorSystem, Props}
import com.typesafe.config.ConfigFactory
import scala.concurrent.Await
import scala.concurrent.duration._

/**
 * Philosophers JVM - Creates philosopher actors that connect to remote forks
 * Make sure to start ForksApp first!
 */
object PhilosophersApp extends App {

  val n: Int = if (args.length > 0) args(0).toInt else 5
  require(n >= 2, s"Need at least 2 philosophers/forks, but got $n")

  val is_with_solution: Boolean = if (args.length > 1) args(1).toBoolean else false
  println("Using solution strategy: " + (if (is_with_solution) "Yes" else "No"))
  
  println(s"Starting Philosophers System with $n philosophers")
  println("Make sure the Forks system is running first!")
  println("Press ENTER to stop the philosophers system...")
  
  // Philosophers system configuration
  val philosophers_port: Int = 2553
  val philosophers_config = ConfigFactory
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
            canonical.port = $philosophers_port
          }
        }
        
        # Reduce log noise
        loglevel = "INFO"
        stdout-loglevel = "INFO"
        log-dead-letters = 10
        log-dead-letters-during-shutdown = off
      }
      """)

  val philosophers_system = ActorSystem("PhilosophersSystem", philosophers_config)

  try {
    val forks_port: Int = 2552

    var solution_phil_id : Int = -1
    if (is_with_solution) {
      // choosing a random philosopher to use the solution strategy
      solution_phil_id = scala.util.Random.nextInt(n)
    }
    
    // Create all philosopher actors with remote fork references
    val philosophers: Seq[ActorRef] =
      (0 until n).map { i =>
        // Create actor selections for left and right forks on remote system
        val left = philosophers_system.actorSelection(s"akka://ForksSystem@localhost:$forks_port/user/fork-$i")
        val right = philosophers_system.actorSelection(s"akka://ForksSystem@localhost:$forks_port/user/fork-${(i + 1) % n}")
        if (solution_phil_id == i && is_with_solution) {
          philosophers_system.actorOf(Props(new Philosopher(i, right, left)), s"phil-$i")

        } else {
          philosophers_system.actorOf(Props(new Philosopher(i, left, right)), s"phil-$i")
        }
      }
    
    println(s"Created $n philosophers:")


    // Display fork assignments for each philosopher
    philosophers.foreach { philosopher =>
      val phil_id = philosopher.path.name.stripPrefix("phil-").toInt
      var left_index = phil_id
      var right_index = (phil_id + 1) % n
      val left_fork_path = s"akka://ForksSystem@localhost:$forks_port/user/fork-$left_index"
      val right_fork_path = s"akka://ForksSystem@localhost:$forks_port/user/fork-$right_index"
      if (solution_phil_id == phil_id && is_with_solution) {
        println(s"  Philosopher $phil_id: left=$right_fork_path, right=$left_fork_path")
      }else {
        println(s"  Philosopher $phil_id: left=$left_fork_path, right=$right_fork_path")
      }
      
    }
    println()
    
    // Start all philosophers thinking
    Thread.sleep(2000) // Give time for remote connections to establish
    philosophers.foreach(_ ! PhilosopherMessages.StartThinking)
    println("All philosophers started thinking...")
    
    Await.ready(philosophers_system.whenTerminated, Duration.Inf)
    
  } finally {
    println("Shutting down philosophers system...")
    philosophers_system.terminate()
  }
}
