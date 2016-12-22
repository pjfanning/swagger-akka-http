package com.github.swagger.akka

import scala.collection.JavaConverters._
import scala.reflect.runtime.universe.Type
import com.github.swagger.akka.model.Info
import com.github.swagger.akka.model.scala2swagger
import akka.actor.ActorSystem
import akka.http.scaladsl.model.{HttpEntity, MediaTypes}
import akka.http.scaladsl.server.{Directives, PathMatchers, Route}
import akka.stream.ActorMaterializer
import io.swagger.jaxrs.Reader
import io.swagger.jaxrs.config.ReaderConfig
import io.swagger.models.{ExternalDocs, Scheme, Swagger}
import io.swagger.models.auth.SecuritySchemeDefinition
import io.swagger.util.Json
import org.apache.commons.lang3.StringUtils
import org.slf4j.LoggerFactory

/**
 * @author rleibman
 */
trait HasActorSystem {
  implicit val actorSystem: ActorSystem
  implicit val materializer: ActorMaterializer
}

object SwaggerHttpService {

  val logger = LoggerFactory.getLogger(classOf[SwaggerHttpService])

  val readerConfig = new ReaderConfig {
    def getIgnoredRoutes: java.util.Collection[String] = List().asJavaCollection
    def isScanAllResources: Boolean = false
  }

  def toJavaTypeSet(apiTypes: Seq[Type]): Set[Class[_]] ={
    apiTypes.map(t => Class.forName(getClassNameForType(t))).toSet
  }

  def getClassNameForType(t: Type): String ={
    val typeSymbol = t.typeSymbol
    val fullName = typeSymbol.fullName
    if (typeSymbol.isModuleClass) {
      val idx = fullName.lastIndexOf('.')
      if (idx >=0) {
        val mangledName = s"${fullName.slice(0, idx)}$$${fullName.slice(idx+1, fullName.length)}$$"
        mangledName
      } else fullName
    } else fullName
  }

  def removeInitialSlashIfNecessary(path: String): String =
    if(path.startsWith("/")) removeInitialSlashIfNecessary(path.substring(1)) else path
}

trait SwaggerHttpService extends Directives {
  this: HasActorSystem ⇒

  import SwaggerHttpService._
  val apiTypes: Seq[Type]
  val host: String = ""
  val basePath: String = "/"
  val apiDocsPath: String = "api-docs"
  val info: Info = Info()
  val scheme: Scheme = Scheme.HTTP
  val securitySchemeDefinitions: Map[String, SecuritySchemeDefinition] = Map()
  val externalDocs: Option[ExternalDocs] = None

  def swaggerConfig: Swagger = {
    val modifiedPath = prependSlashIfNecessary(basePath)
    val swagger = new Swagger().basePath(modifiedPath).info(info).scheme(scheme)
    if(StringUtils.isNotBlank(host)) swagger.host(host)
    swagger.setSecurityDefinitions(securitySchemeDefinitions.asJava)
    externalDocs match {
      case Some(ed) => swagger.externalDocs(ed)
      case None => swagger
    }
  }

  def reader = new Reader(swaggerConfig, readerConfig)
  def prependSlashIfNecessary(path: String): String  = if(path.startsWith("/")) path else s"/$path"

  def generateSwaggerDocs: String = {
    try {
      val swagger: Swagger = reader.read(toJavaTypeSet(apiTypes).asJava)
      Json.mapper().writeValueAsString(swagger)
    } catch {
      case t: Throwable => {
        logger.error("Issue with creating swagger.json", t)
        throw t
      }
    }
  }

  lazy val routes: Route =
    path(PathMatchers.separateOnSlashes(removeInitialSlashIfNecessary(apiDocsPath)) / "swagger.json") {
      get {
        complete(HttpEntity(MediaTypes.`application/json`, generateSwaggerDocs))
      }
    }
}
