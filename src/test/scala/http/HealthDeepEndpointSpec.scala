package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.test._

object HealthDeepEndpointSpec extends ZIOSpecDefault:

  private val routes = Routes(HealthDeepEndpoint.route)

  def spec = suite("HealthDeepEndpoint")(
    test("should respond with OK status to GET requests on /health-deep") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("should respond with 200 status code") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status.code == 200
      )
    },
    test("should respond with empty body") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        body.isEmpty
      )
    },
    test("should respond to GET with consistent results") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.Ok
      )
    },
    test("should not respond to POST requests") {
      val request = Request.post(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root / "health-deep")

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root / "health-deep", Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should respond consistently to multiple requests") {
      val request = Request.get(URL.root / "health-deep")

      for {
        response1 <- routes(request)
        response2 <- routes(request)
        response3 <- routes(request)
      } yield assertTrue(
        response1.status == Status.Ok,
        response2.status == Status.Ok,
        response3.status == Status.Ok
      )
    },
    test("should handle rapid successive requests") {
      val request = Request.get(URL.root / "health-deep")

      for {
        responses <- ZIO.collectAll(
          List.fill(5)(
            routes(request)
          )
        )
      } yield assertTrue(
        responses.forall(_.status == Status.Ok)
      )
    }
  )
