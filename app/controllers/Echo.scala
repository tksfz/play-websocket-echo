package controllers

import play.api._
import play.api.mvc._
import play.api.libs.iteratee._

object Echo {

  def simpleIterateeWebSocket = WebSocket.using[String] {
    requestHeader =>
      val out = Enumerator.imperative[String]()
      // note you can't call out.push here yet
      val in = Iteratee.foreach[String] {
        event =>
          out.push(event)
      }
      (in, out)
  }
  
  import play.api.libs.concurrent.Akka
  import play.api.Play.current // needed by Akka.future
  
  def simpleAsyncWebSocket = WebSocket.async[String] {
    requestHeader => Akka.future {
      val out = Enumerator.imperative[String]()
      // note you can't call out.push here yet
      val in = Iteratee.foreach[String] {
        event =>
          out.push(event)
      }
      (in, out)
    }
  }
}

import play.api.libs.concurrent._
import akka.actor._
import play.api.Play.current // needed for Akka.system
import akka.util.duration._

object EchoWithActors {
  
  implicit val timeout = akka.util.Timeout(1 second)
  
  def naiveActorWebSocket = WebSocket.using[String] {
    requestHeader =>
      val actor = Akka.system.actorOf(Props[NaiveEchoActor])
      val out = Enumerator.imperative[String]()
      actor ! NaiveStart(out)
      val in = Iteratee.foreach[String] {
        event =>
          actor ! Message(event)
      }
      (in, out)
  }
  
  import akka.pattern.ask
  
  def actorWebSocket = WebSocket.async[String] {
    requestHeader =>
      val actor = Akka.system.actorOf(Props[EchoActor])
      (actor ? Start()).asPromise map {
        case Connected(out) =>
          val in = Iteratee.foreach[String] {
            event => actor ! Message(event)
          }
          (in, out)
      }
  }
}

// Actor messages
case class NaiveStart(out: PushEnumerator[String])
case class Start()
case class Connected(out: PushEnumerator[String])
case class Message(msg: String)

class NaiveEchoActor extends Actor {
  var out: PushEnumerator[String] = _
  
  override def receive = {
    case NaiveStart(out) => this.out = out
    case Message(msg) => this.out.push(msg)
  }
}

class EchoActor extends Actor {
  var out: PushEnumerator[String] = _
  override def receive = {
    case Start() =>
      this.out = Enumerator.imperative[String]()
      sender ! Connected(out)
    case Message(msg) => this.out.push(msg)
  }
}