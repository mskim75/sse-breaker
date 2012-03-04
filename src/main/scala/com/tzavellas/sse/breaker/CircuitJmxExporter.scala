/* ----------------- sse-breaker ----------------- *\
 * Licensed under the Apache License, Version 2.0. *
 * Author: Spiros Tzavellas                        *
\* ----------------------------------------------- */
package com.tzavellas.sse.breaker

import java.lang.management.ManagementFactory
import javax.management._

trait CircuitJmxExporter {
  
  val circuitBreaker: CircuitBreaker
  
  import CircuitJmxExporter._

  /**
   * Register a {@code CircuitBreakerControlMBean} for this executor in the
   * platform MBean server. 
   */
  def exportToJmx() {
    val mbean = new CircuitBreakerControl(circuitBreaker)
    server.registerMBean(mbean, objectName(circuitBreaker))
  }
  
  /**
   * Unregister the {@code CircuitBreakerControlMBean} that is associated with
   * this executor from the platform MBean server.
   */
  def removeFromJmx() {
    server.unregisterMBean(objectName(circuitBreaker))
  }
  
  private def server = ManagementFactory.getPlatformMBeanServer
}

private [breaker] object CircuitJmxExporter {
  def objectName(circuit: CircuitBreaker) = {
    new ObjectName("com.tzavellas.sse.breaker:type=CircuitBreaker,name="+circuit.name)
  }  
}