package ru.influunt

import akka.stream.stage.{GraphStage, GraphStageLogic, OutHandler}
import akka.stream.{Attributes, Outlet, SourceShape}

import scala.io.Source

object CSVSource {

  def apply(fileName: String, delimiter: String = ",", skipLines: Int = 0): CSVSource = {
    new CSVSource(fileName, delimiter, skipLines)
  }
}

class CSVSource(fileName: String, delimiter: String, skipLines: Int = 0) extends GraphStage[SourceShape[Array[String]]] {

  private val out: Outlet[Array[String]] = Outlet("Rows")

  override def shape: SourceShape[Array[String]] = SourceShape(out)

  override def createLogic(inheritedAttributes: Attributes): GraphStageLogic = new GraphStageLogic(shape) {

    private val source = Source.fromResource(fileName)
    private val lines = source.getLines().map(_.split(delimiter + "(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)")).drop(skipLines)

    setHandler(out, new OutHandler {
      override def onPull(): Unit = {
        lines.nextOption() match {
          case Some(row) =>
            push(out, row)
          case _ =>
            completeStage()
        }
      }

      override def onDownstreamFinish(): Unit = {
        println(s"Source finished")
        super.onDownstreamFinish()
        source.close()
      }
    })
  }
}
