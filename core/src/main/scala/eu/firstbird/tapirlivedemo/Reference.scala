package eu.firstbird.tapirlivedemo

import java.util.UUID

object Reference extends App {

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

    val getColorTheme: Endpoint[UUID, (StatusCode, ErrorInfo), ColorTheme, Nothing] = endpoint.get
      .in("some" / "path" / "color_theme" / path[UUID]("themeId"))
      .errorOut(statusCode)
      .errorOut(jsonBody[ErrorInfo])
      .out(jsonBody[ColorTheme])

    val baseEndpoint: Endpoint[Unit, (StatusCode, ErrorInfo), Unit, Nothing] = endpoint
      .in("some" / "path")
      .errorOut(statusCode.and(jsonBody[ErrorInfo]))

    val postColorTheme: Endpoint[ColorTheme, (StatusCode, ErrorInfo), Unit, Nothing] = baseEndpoint.post
      .in("color_theme")
      .in(jsonBody[ColorTheme])

    val postColorThemeWithAuth: Endpoint[(AuthToken, ColorTheme), (StatusCode, ErrorInfo), Unit, Nothing] = baseEndpoint.post
      .in("color_theme")
      .in(auth.bearer)
      .in(jsonBody[ColorTheme])
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

    val getColorThemeRoute: Route = Endpoints.getColorTheme.toRoute { id =>
      val themes = colorThemes.find(_.id == id)

      themes match {
        case Some(theme) => Future.successful(Right(theme))
        case None => Future.successful(Left((StatusCodes.NotFound, ErrorInfo(s"Book $id not found"))))
      }
    }

    val postColorThemeRoute: Route = Endpoints.postColorTheme.toRoute { colorTheme =>
      colorThemes.find(_.id == colorTheme.id) match {
        case Some(_) => Future.successful(Left((StatusCodes.BadRequest, ErrorInfo(s"ColorTheme with ${colorTheme.id} exists"))))
        case None =>
          colorThemes = colorTheme :: colorThemes
          Future.successful(Right(()))
      }
    }

    val postColorThemeWithAuthRoute: Route = Endpoints.postColorThemeWithAuth.toRoute {
      case (auth, colorTheme) =>
        if (auth == "supersecure")
          insertNewTheme(colorTheme)
        else
          Future.successful(Left((StatusCodes.Unauthorized, ErrorInfo("Please login"))))
    }

    def insertNewTheme(colorTheme: ColorTheme): Future[Either[(Int, ErrorInfo), Unit]] = {
      colorThemes.find(_.id == colorTheme.id) match {
        case Some(_) => Future.successful(Left((StatusCodes.BadRequest, ErrorInfo(s"ColorTheme with ${colorTheme.id} exists"))))
        case None =>
          colorThemes = colorTheme :: colorThemes
          Future.successful(Right(()))
      }
    }
  }

  object Documentation {
    import Endpoints._
    import tapir.docs.openapi._
    import tapir.openapi.OpenAPI
    import tapir.openapi.circe.yaml._

    val openApi: OpenAPI = List(getColorThemes, getColorTheme, postColorTheme, postColorThemeWithAuth).toOpenAPI("Awesome Livedemo", version = "0.01")
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

    val routes = getColorThemesRoute ~ getColorThemeRoute ~ postColorThemeWithAuthRoute ~ new SwaggerUI(Documentation.yml).routes
    implicit val actorSystem: ActorSystem = ActorSystem()
    implicit val materializer: ActorMaterializer = ActorMaterializer()
    Await.result(Http().bindAndHandle(routes, "localhost", 8080), 1.minute)
    println("Server is now ready")
    println(s"DB: \n $colorThemes")
  }

  startServer()
}