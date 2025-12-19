package org.roland.scala3_zio_template.http

import zio._
import zio.http._
import zio.test._

object RootEndpointSpec extends ZIOSpecDefault:

  private val routes = Routes(RootEndpoint.route)

  def spec = suite("RootEndpoint")(
    test("should respond to GET requests on root path") {
      val request = Request.get(URL.root)

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "Hello, Root Endpoint!"
      )
    },
    test("should return expected greeting message") {
      val request = Request.get(URL.root)

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        body.contains("Hello"),
        body.contains("Root Endpoint")
      )
    },
    test("should respond with 200 status code") {
      val request = Request.get(URL.root)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status.code == 200
      )
    },
    test("should return text content type") {
      val request = Request.get(URL.root)

      for {
        response <- routes(request)
        contentType = response.header(Header.ContentType)
      } yield assertTrue(
        contentType.isDefined
      )
    },
    test("should respond consistently") {
      val request = Request.get(URL.root)

      for {
        response <- routes(request)
        body <- response.body.asString
      } yield assertTrue(
        response.status == Status.Ok,
        body == "Hello, Root Endpoint!"
      )
    },
    test("should not respond to POST requests") {
      val request = Request.post(URL.root, Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PUT requests") {
      val request = Request.put(URL.root, Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to DELETE requests") {
      val request = Request.delete(URL.root)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should not respond to PATCH requests") {
      val request = Request.patch(URL.root, Body.empty)

      for {
        response <- routes(request)
      } yield assertTrue(
        response.status == Status.NotFound
      )
    },
    test("should respond consistently to multiple requests") {
      val request = Request.get(URL.root)

      for {
        response1 <- routes(request)
        response2 <- routes(request)
        response3 <- routes(request)
        body1 <- response1.body.asString
        body2 <- response2.body.asString
        body3 <- response3.body.asString
      } yield assertTrue(
        response1.status == Status.Ok,
        response2.status == Status.Ok,
        response3.status == Status.Ok,
        body1 == body2,
        body2 == body3
      )
    },
    test("should handle concurrent requests") {
      val request = Request.get(URL.root)

      for {
        responses <- ZIO.collectAll(
          List.fill(10)(
            routes(request)
          )
        )
        bodies <- ZIO.collectAll(responses.map(_.body.asString))
      } yield assertTrue(
        responses.forall(_.status == Status.Ok),
        bodies.forall(_ == "Hello, Root Endpoint!")
      )
    }
  )
