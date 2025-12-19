package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.test._

object EchoEndpointSpec extends ZIOSpecDefault:

  private val routes = Routes(EchoEndpoint.route)

  def spec = suite("EchoEndpoint")(
    test("should respond to POST requests on /echo") {
      val testMessage = "Hello, World!"
      val request =
        Request.post(URL.root / "echo", Body.fromString(testMessage))

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == testMessage
      )
    },
    test("should echo empty string when body is empty") {
      val request = Request.post(URL.root / "echo", Body.empty)

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == ""
      )
    },
    test("should echo multi-line text") {
      val testMessage = "Line 1\nLine 2\nLine 3"
      val request =
        Request.post(URL.root / "echo", Body.fromString(testMessage))

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == testMessage
      )
    },
    test("should echo special characters") {
      val testMessage = "Special chars: !@#$%^&*(){}[]|\\:;\"'<>,.?/~`"
      val request = Request
        .post(URL.root / "echo", Body.fromString(testMessage))

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == testMessage
      )
    },
    test("should echo unicode characters") {
      val testMessage = "Unicode: 你好世界 🌍 émojis 🎉"
      val request =
        Request.post(URL.root / "echo", Body.fromString(testMessage))

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == testMessage
      )
    },
    test("should echo large text body") {
      val testMessage = "a" * 10000
      val request =
        Request.post(URL.root / "echo", Body.fromString(testMessage))

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == testMessage,
        body.length == 10000
      )
    },
    test("should echo JSON content") {
      val testMessage = """{"name": "test", "value": 123}"""
      val request =
        Request.post(URL.root / "echo", Body.fromString(testMessage))

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == testMessage
      )
    },
    test("should not respond to GET requests") {
      val request = Request.get(URL.root / "echo")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "echo", Body.fromString("test"))

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "echo")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    }
  )
