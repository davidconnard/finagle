package com.twitter.finagle.tracing

import com.twitter.finagle._

private[finagle] object TracingFilter {
  implicit val role = Stack.Role("Tracer")
  
  /**
   * Creates a [[com.twitter.finagle.Stackable]] [[com.twitter.finagle.tracing.TracingFilter]].
   */
  def module[Req, Rep]: Stackable[ServiceFactory[Req, Rep]] =
    new Stack.Simple[ServiceFactory[Req, Rep]] {
      val role = TracingFilter.role
      val description = "Handle span lifecycle events to report tracing from protocols"
      def make(next: ServiceFactory[Req, Rep])(implicit params: Params) = {
        val param.Tracer(tracer) = get[param.Tracer]
        val param.Label(label) = get[param.Label]
        val tracingFilter = new TracingFilter[Req,Rep](tracer, label)
        tracingFilter andThen next
      }
    }
}

/**
 * The TracingFilter takes care of span lifecycle events. It is always
 * placed first in the server filter chain so that protocols with
 * trace support will override the span resets, and still be properly
 * reported here.
 *
 * @param tracer An instance of a tracer to use. Eg: ZipkinTracer
 * @param label The name of the service being traced
 */
class TracingFilter[Req, Rep](tracer: Tracer, label: String) extends SimpleFilter[Req, Rep] {

  @deprecated("Please add a label to the tracing filter constructor", "6.13.x")
  def this(tracer: Tracer) = this(tracer, "Unknown")
  
  def apply(request: Req, service: Service[Req, Rep]) = {
    Trace.unwind {
      Trace.pushTracerAndSetNextId(tracer)
      Trace.recordBinary("finagle.version", Init.finagleVersion)
      Trace.recordServiceName(label)
      service(request)
    }
  }
}
