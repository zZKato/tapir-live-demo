package eu.firstbird.tapirlivedemo

class SwaggerUI(yml: String) {
  import java.util.Properties

  import akka.http.scaladsl.model.StatusCodes
  import akka.http.scaladsl.server.Directives._
  import akka.http.scaladsl.server.Route

  val docs = "docs.yml"

  private val redirectToIndex = redirect(s"/docs/index.html?url=/docs/$docs", StatusCodes.PermanentRedirect)

  private val swaggerUiVersion = {
    val p = new Properties()
    p.load(getClass.getResourceAsStream("/META-INF/maven/org.webjars/swagger-ui/pom.properties"))
    p.getProperty("version")
  }

  val routes: Route =
    path("docs") {
      redirectToIndex
    } ~
      pathPrefix("docs") {
        path("") { // this is for trailing slash
          redirectToIndex
        } ~
          path(docs) {
            complete(yml)
          } ~
          getFromResourceDirectory(s"META-INF/resources/webjars/swagger-ui/$swaggerUiVersion/")
      }
}
