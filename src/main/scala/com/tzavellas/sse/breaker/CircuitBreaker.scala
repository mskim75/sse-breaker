/* ----------------- sse-breaker ----------------- *\
 * Licensed under the Apache License, Version 2.0. *
 * Author: Spiros Tzavellas                        *
\* ----------------------------------------------- */
package com.tzavellas.sse.breaker

import java.util.concurrent.atomic.{AtomicInteger, AtomicLong}

/**
 * Holds the state of a circuit-breaker.
 *
 * Instances of this class are ''thread-safe''.
 * 
 * @param name       the name of the circuit-breaker (used in the JMX
 *                   `ObjectName`, can be used for logging, etc).
 * @param initConf   the initial configuration of the circuit-breaker.
 * @param listener   a listener that observes the state changes of this
 *                   circuit-breaker.
 * 
 * @see CircuitExecutor
 */
class CircuitBreaker(
  val name: String,
  initConf: CircuitConfiguration,
  listener: CircuitStateListener) {
  
  @volatile
  private[this] var conf = initConf
  
  private[this] val currentFailures = new AtomicInteger
  private[this] val openTimestamp = new AtomicLong
  private[this] val firstCurrentFailureTimestamp = new AtomicLong

  private[this] val calls = new AtomicInteger
  private[this] val failures = new AtomicInteger
  private[this] val timesOpened = new AtomicInteger
  
  private[breaker] def recordCall(): Unit =
    calls.incrementAndGet()
  
  private[breaker] def recordExecutionTime(nanos: Long): Unit = {
    if (nanos > conf.maxMethodDuration.toNanos) {
      recordFailure(new SlowMethodExecutionException(conf.maxMethodDuration))
    }
    else if (isHalfOpen) {
      close()
    }
  }
  
  private [breaker] def recordThrowable(throwable: Throwable): Unit = {
    if (conf.isFailure(throwable)) {
      recordFailure(throwable)
    }
  }
  
  private def recordFailure(failure: Throwable): Unit = {
    failures.incrementAndGet()
    initFirstFailureTimeStampIfNeeded()
    var tmpCurrentFailures = 0
    if (hasFailureCountExpired) {
      resetFailures()
      tmpCurrentFailures = 1
    } else {
      tmpCurrentFailures = currentFailures.incrementAndGet()
    }
    if (tmpCurrentFailures >= conf.maxFailures)
        open(failure)
  }
  
  private def resetFailures(): Unit = {
    currentFailures.set(1)
    firstCurrentFailureTimestamp.set(System.nanoTime)
  }
  
  private def initFirstFailureTimeStampIfNeeded(): Unit = {
    firstCurrentFailureTimestamp.compareAndSet(0, System.nanoTime)
  }
  
  private def hasFailureCountExpired = {
    System.nanoTime - firstCurrentFailureTimestamp.get >= conf.failureCountTimeout.toNanos
  }

  /**
   * Tests if the circuit-breaker is in the ''open'' state.
   * 
   * A circuit-breaker is in the ''open'' state when enough failures have
   * occurred in a configured amount of time and the opened state hasn't
   * expired yet.
   */
  def isOpen: Boolean = currentFailures.get >= conf.maxFailures && !isHalfOpen
  
  /**
   * Tests if the circuit-breaker is in the ''closed'' state.
   * 
   * A circuit-breaker is in the ''closed'' state when the error rate is low
   * and it allows the execution of the requested operations.
   */
  def isClosed: Boolean = !isOpen
  
  /**
   * Tests if the circuit-breaker is in the ''half-open'' state.
   * 
   * A circuit-breaker is in the ''half-open'' state when the ''open'' state
   * has expired.
   */
  def isHalfOpen: Boolean = {
    val timestamp = openTimestamp.get
    timestamp != 0 && timestamp + conf.openCircuitTimeout.toMillis <= System.currentTimeMillis
  }
  
  /** Closes the circuit-breaker. */
  def close(): Unit = {
    currentFailures.set(0)
    openTimestamp.set(0)
    try {
      listener.onClose(this)
    }
    catch {
      case e: Exception => throw new CircuitListenerException(name, "onClose", e) 
    }
  }
  
  /** Opens the circuit-breaker. */
  def open(): Unit = open(new ForcedOpenException(name))
  
  /** Opens the circuit-breaker. */
  def open(failure: Throwable): Unit = {
    timesOpened.incrementAndGet()
    openTimestamp.set(System.currentTimeMillis)  
    currentFailures.set(conf.maxFailures)
    try {
      listener.onOpen(this, failure)
    }
    catch {
      case e: Exception => throw new CircuitListenerException(name, "onOpen", e)
    }
  }

  /** The current configuration. */
  def configuration: CircuitConfiguration = conf
  
  /** Reconfigures this circuit-breaker using the specified configuration. */
  def reconfigureWith(newConf: CircuitConfiguration): Unit = { conf = newConf }
  
  /**
   * The timestamp of the last transition to the open state (zero when the
   * circuit-breaker is in the closed state).
   */
  def openedTimestamp: Long = openTimestamp.get
  
  /**
   * The number of failures since the circuit breaker entered the closed
   * state.
   */
  def numberOfCurrentFailures: Int = currentFailures.get
  
  /**
   * The number of operations that threw an exception or took too long to
   * complete.
   */
  def numberOfFailedOperations: Int = failures.get
  
  /** The number of operations (failed and successful). */
  def numberOfOperations: Int = calls.get
  
  /** The number of times this circuit breaker has entered the open state. */
  def numberOfTimesOpened: Int = timesOpened.get
  
  /**
   * Resets the statistics counters to zero.
   * 
   * The values of `numberOfFailedOperations`, `numberOfOperations` and
   * `numberOfTimesOpened` are set to zero.
   */
  def resetStatistics(): Unit = {
    failures.set(0)
    calls.set(0)
    timesOpened.set(0)
  }
}
