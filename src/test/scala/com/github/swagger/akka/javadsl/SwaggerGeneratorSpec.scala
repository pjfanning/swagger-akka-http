package com.github.swagger.akka.javadsl

import java.util

import com.github.swagger.akka.samples.DictHttpService

import scala.collection.JavaConverters._
import io.swagger.v3.oas.models.ExternalDocumentation
import io.swagger.v3.oas.models.info.{Contact, Info, License}
import io.swagger.v3.oas.models.security.SecurityScheme
import org.scalatest.{Matchers, WordSpec}

class SwaggerGeneratorSpec extends WordSpec with Matchers {

  "Java DSL SwaggerGenerator" should {
    "not fail when generating swagger doc" in {
      val generator = new SwaggerGenerator {
        override def apiClasses: util.Set[Class[_]] = util.Collections.singleton(classOf[DictHttpService])
      }
      generator.generateSwaggerJson should not be empty
      generator.generateSwaggerYaml should not be empty
    }

    "properly convert the javadsl settings" in {
      val contact = new Contact().email("a@b.com").name("a").url("http://b.com")
      val license = new License().name("z").url("http://b.com/license")
      val testInfo = new Info().contact(contact).description("desc").license(license)
            .termsOfService("T&C").title("Title").version("0.1")
      val bearerTokenScheme = new SecurityScheme()
        .bearerFormat("JWT")
        .description("my jwt token")
        .`type`(SecurityScheme.Type.HTTP)
        .in(SecurityScheme.In.HEADER)
        .scheme("bearer")

      val edocs = new ExternalDocumentation().description("edesc").url("http://b.com/docs")
      val generator = new SwaggerGenerator {
        override def apiClasses: util.Set[Class[_]] = util.Collections.singleton(classOf[DictHttpService])
        override def apiDocsPath: String = "docs"
        override def info: Info = testInfo
        override def securitySchemes: util.Map[String, SecurityScheme] = {
          val jmap = new util.HashMap[String, SecurityScheme]()
          jmap.put("bearerAuth", bearerTokenScheme)
          jmap
        }
        override def externalDocs: util.Optional[ExternalDocumentation] = util.Optional.of(edocs)
        override def vendorExtensions: util.Map[String, Object] = {
          val jmap = new util.HashMap[String, Object]()
          jmap.put("n1", "v1")
          jmap
        }
        override def unwantedDefinitions: util.List[String] = util.Collections.singletonList("unwanted")
      }

      generator.securitySchemes should not be empty
      generator.securitySchemes should have size 1
      generator.vendorExtensions should not be empty


      generator.converter.apiClasses shouldEqual Set(classOf[DictHttpService])
      generator.converter.apiDocsPath shouldEqual generator.apiDocsPath
      import com.github.swagger.akka.model.scala2swagger
      scala2swagger(generator.converter.info) shouldEqual testInfo
      generator.converter.securitySchemes.asJava shouldEqual generator.securitySchemes
      generator.converter.externalDocs.get shouldEqual generator.externalDocs.get()
      generator.converter.vendorExtensions.asJava shouldEqual generator.vendorExtensions
    }
  }
}
