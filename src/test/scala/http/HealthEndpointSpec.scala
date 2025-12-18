package org.roland.scala3_zio_template.http

import zio._
import zio.test._
import zio.http._

object HealthEndpointSpec extends ZIOSpecDefault:

  private val routes = Routes(HealthEndpoint.route)

  def spec = suite("HealthEndpoint")(
    test("should respond with OK status to POST requests on /health") {
      val request = Request.post(URL.root / "health", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("should respond with 200 status code") {
      val request = Request.post(URL.root / "health", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status.code == 200
      )
    },
    test("should respond with empty body") {
      val request = Request.post(URL.root / "health", Body.empty)

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        body.isEmpty
      )
    },
    test("should respond to POST with request body") {
      val request = Request
        .post(URL.root / "health", Body.fromString("test body"))

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("should not respond to GET requests") {
      val request = Request.get(URL.root / "health")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "health", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "health")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root / "health", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should respond consistently to multiple requests") {
      val request = Request.post(URL.root / "health", Body.empty)

      for {
        response1 <- routes(request)
        response2 <- routes(request)
        response3 <- routes(request)
      } yield assertTrue(
        response1.status == Status.Ok,
        response2.status == Status.Ok,
        response3.status == Status.Ok
      )
    }
  )
