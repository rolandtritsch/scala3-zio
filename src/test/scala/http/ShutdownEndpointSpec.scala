package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.test._

object ShutdownEndpointSpec extends ZIOSpecDefault:

  def spec = suite("ShutdownEndpoint")(
    test("should respond with 202 Accepted to POST /shutdown") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.post(URL.root / "shutdown", Body.empty)
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.Accepted
      )
    },
    test("should respond with 202 status code") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.post(URL.root / "shutdown", Body.empty)
        response <- routes(request)
      } yield assertTrue(
        response.status.code == 202
      )
    },
    test("should respond with body 'Shutdown initiated'") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.post(URL.root / "shutdown", Body.empty)
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        body == "Shutdown initiated"
      )
    },
    test("should trigger shutdown promise when called") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.post(URL.root / "shutdown", Body.empty)
        _ <- routes(request)
        _ <- TestClock.adjust(100.millis)
        isDone <- promise.isDone
      } yield assertTrue(isDone)
    },
    test("should not respond to GET requests") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.get(URL.root / "shutdown")
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.put(URL.root / "shutdown", Body.empty)
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.delete(URL.root / "shutdown")
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.patch(URL.root / "shutdown", Body.empty)
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should handle multiple shutdown calls gracefully") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.post(URL.root / "shutdown", Body.empty)
        response1 <- routes(request)
        response2 <- routes(request)
        response3 <- routes(request)
      } yield assertTrue(
        response1.status == Status.Accepted,
        response2.status == Status.Accepted,
        response3.status == Status.Accepted
      )
    },
    test("should respond consistently to multiple requests") {
      for {
        promise <- Promise.make[Nothing, Unit]
        routes = Routes(ShutdownEndpoint.route(promise))
        request = Request.post(URL.root / "shutdown", Body.empty)
        response1 <- routes(request)
        response2 <- routes(request)
      } yield assertTrue(
        response1.status == Status.Accepted,
        response2.status == Status.Accepted
      )
    }
  )
