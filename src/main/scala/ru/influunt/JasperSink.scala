package ru.influunt

import java.util
import java.util.concurrent.ArrayBlockingQueue

import akka.Done
import akka.dispatch.ExecutionContexts
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler}
import akka.stream.{Attributes, Inlet, SinkShape}
import net.sf.jasperreports.engine._
import net.sf.jasperreports.engine.fill.JRFileVirtualizer
import net.sf.jasperreports.engine.util.JRLoader

import scala.concurrent.duration.Duration
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}

object JasperSink {

  def apply(reportName: String): JasperSink = {
    new JasperSink(reportName)
  }
}

class JasperSink(reportName: String) extends GraphStageWithMaterializedValue[SinkShape[Array[String]], Future[Done]] {

  private val in: Inlet[Array[String]] = Inlet("Rows")

  override def shape: SinkShape[Array[String]] = SinkShape(in)


  override def createLogicAndMaterializedValue(inheritedAttributes: Attributes): (GraphStageLogic, Future[Done]) = {
    val promise = Promise[Done]()
    val stage: GraphStageLogic = new GraphStageLogic(shape) {

      val report: JasperReport = JRLoader.loadObject(JasperSink.getClass.getClassLoader.getResource(reportName)).asInstanceOf[JasperReport]
      val queue: ArrayBlockingQueue[Array[String]] = new ArrayBlockingQueue[Array[String]](5)
      val dataSource: JRDataSource = new JRDataSource {

        var el: Array[String] = Array.empty[String]

        override def next(): Boolean = {
          el = queue.take()
          el.nonEmpty
        }

        override def getFieldValue(jrField: JRField): AnyRef = {
          jrField.getName match {
            case "year" => el(0)
            case "industry_code_ANZSIC" => el(1)
            case "industry_name_ANZSIC" => el(2)
            case "rme_size_grp" => el(3)
            case "variable" => el(4)
            case "value" => el(5)
            case "unit" => el(6)
          }
        }
      }

      val future: Future[Unit] = Future {
        val virtualizer = new JRFileVirtualizer(2)
        val params = new util.HashMap[String, Object]()
        params.put(JRParameter.REPORT_VIRTUALIZER, virtualizer)
        val jasperPrint = JasperFillManager.fillReport(report, params, dataSource);
        JasperExportManager.exportReportToPdfFile(jasperPrint, "report.pdf");
      }(ExecutionContexts.global)

      override def preStart(): Unit = pull(in)

      setHandler(in, new InHandler {
        override def onPush(): Unit = {
          val next = grab(in)
          queue.put(next)
          pull(in)
        }

        override def onUpstreamFinish(): Unit = {
          super.onUpstreamFinish()
          completeReport(Success(Done))
        }

        override def onUpstreamFailure(ex: Throwable): Unit = {
          super.onUpstreamFailure(ex)
          completeReport(Failure(ex))
        }

        def completeReport(result: Try[Done]): Unit = {
          queue.offer(Array.empty[String])
          Await.result(future, Duration.Inf)
          promise.complete(result)
        }
      })
    }

    stage -> promise.future
  }
}
