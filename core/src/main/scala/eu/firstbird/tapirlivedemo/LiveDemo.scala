package eu.firstbird.tapirlivedemo

import java.util.UUID

object LiveDemo extends App {

  //model
  case class Color(str: String)
  case class ColorTheme(id: UUID, color1: Color, color2: Color)

  //authentication & error handling
  type AuthToken = String
  case class ErrorInfo(error: String)






  object Database {
    var colorThemes: List[ColorTheme] = {
      List(
        ColorTheme(UUID.randomUUID(), Color("#FFFFFF"), Color("#0F0F0F")),
        ColorTheme(UUID.randomUUID(), Color("#0F0F0F"), Color("#0F0F0F")),
        ColorTheme(UUID.randomUUID(), Color("#FFFFFF"), Color("#FFFFFF"))
      )
    }
  }

  object Endpoints {
    import io.circe.generic.auto._
    import tapir._
    import tapir.json.circe._
    import tapir.model.StatusCode

    val getColorThemes: Endpoint[Unit, (StatusCode, ErrorInfo), List[ColorTheme], Nothing] = endpoint.get
      .in("some" / "path" / "color_theme")
      .errorOut(statusCode)
      .errorOut(jsonBody[ErrorInfo])
      .out(jsonBody[List[ColorTheme]])
  }

  object Routes {
    import Database._
    import akka.http.scaladsl.server.Route
    import tapir.server.akkahttp._
    import tapir.model.StatusCodes

    import scala.concurrent.Future

    val getColorThemesRoute: Route = Endpoints.getColorThemes.toRoute { _ =>
      Future.successful(Right(colorThemes))
    }
  }

  object Documentation {
    import Endpoints._
    import tapir.docs.openapi._
    import tapir.openapi.OpenAPI
    import tapir.openapi.circe.yaml._

    val openApi: OpenAPI = List(getColorThemes).toOpenAPI("Awesome Livedemo", version = "0.01")
    val yml: String = openApi.toYaml
  }

  def startServer(): Unit = {
    import Routes._
    import Database._
    import akka.actor.ActorSystem
    import akka.http.scaladsl.Http
    import akka.http.scaladsl.server.Directives._
    import akka.stream.ActorMaterializer

    import scala.concurrent.Await
    import scala.concurrent.duration._

    val routes = getColorThemesRoute ~ new SwaggerUI(Documentation.yml).routes
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    Await.result(Http().bindAndHandle(routes, "localhost", 8080), 1.minute)
    println("Server is now ready")
    println(s"DB: \n $colorThemes")
  }

  startServer()
}