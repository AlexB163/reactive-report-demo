package ru.influunt

import akka.Done
import akka.actor.ActorSystem
import akka.stream.ActorMaterializer
import akka.stream.scaladsl.{Keep, Sink, Source}

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future}

object ReactiveReportDemo extends App {

  implicit val actorSystem: ActorSystem = ActorSystem()
  implicit val materializer: ActorMaterializer = ActorMaterializer()

  val source = Source.fromGraph(CSVSource("source.csv", skipLines = 1))
  val sink: Sink[Array[String], Future[Done]] = Sink.fromGraph(JasperSink("report.jasper"))
  val runnableGraph = source.toMat(sink)(Keep.right)
  Await.result(runnableGraph.run(), Duration.Inf)
  System.exit(0)
}
