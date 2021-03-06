/* ----------------- sse-breaker ----------------- *\
 * Licensed under the Apache License, Version 2.0. *
 * Author: Spiros Tzavellas                        *
\* ----------------------------------------------- */
package com.tzavellas.sse.breaker.extras

import java.util.Properties
import javax.mail.internet.{ InternetAddress, MimeMessage }
import javax.mail.{ Message, Session, Transport }
import com.tzavellas.sse.breaker.CircuitBreaker
import com.tzavellas.sse.breaker.CircuitStateListener
import com.tzavellas.sse.breaker.extras.EmailCircuitListener._

class EmailCircuitListener(addresses: EmailConfig, config: SMTPConfig)
  extends CircuitStateListener {

  def onOpen(breaker: CircuitBreaker, error: Throwable): Unit = {
    sendEmail(
      subject = "Open circuit for " + breaker.name,
      body = s"The system had lots of errors so it will stop processing temporarily. Last error was...\n ${format(error)}")
  }

  def onClose(breaker: CircuitBreaker): Unit = {
    sendEmail(
      subject = "Closed circuit for '" + breaker.name + "'",
      body = "The system is back to normal")
  }


  private def sendEmail(subject: String, body: String): Unit = {
    val session = Session.getInstance(new Properties)

    val message = new MimeMessage(session)
    message.setFrom(new InternetAddress(addresses.from))
    message.setRecipients(Message.RecipientType.TO, addresses.to)
    message.setSubject(subject)
    message.setText(body)

    val transport = session.getTransport("smtp")
    transport.connect(config.host, config.port, config.username, config.password)
    Transport.send(message)
  }

  private def format(error: Throwable) = s"""
     |Exception: ${error.getClass}
     |
     |Message: ${error.getMessage}
     |
     |Stacktrace:
     |${error.getStackTrace.mkString("\n")}
   """.stripMargin
}

object EmailCircuitListener {

  case class EmailConfig(from: String, to: String)

  case class SMTPConfig(host: String, port: Int, username: String, password: String)
}
