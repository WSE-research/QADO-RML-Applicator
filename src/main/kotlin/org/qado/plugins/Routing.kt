package org.qado.plugins

import be.ugent.rml.Executor
import be.ugent.rml.Utils
import be.ugent.rml.records.RecordsFactory
import io.ktor.http.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.server.application.*
import io.ktor.server.request.*
import java.io.File
import java.io.FileInputStream
import be.ugent.rml.store.QuadStoreFactory
import be.ugent.rml.store.RDF4JStore
import be.ugent.rml.term.NamedNode
import java.io.StringWriter


class MappingFailedException(override val message: String): Throwable()


class DataMapping(private val rml: String, private val data: String, private var outputFormat: String?) {
    fun applyRml(): String {
        val source = Regex("rml:source [^;]+")
        val tempRml = File.createTempFile("rml", "")
        val tempData = File.createTempFile("data", "")

        try {
            tempRml.writeText(rml.replace(source, "rml:source \"${tempData.absoluteFile}\""))
            tempData.writeText(data)

            val mappingStream = FileInputStream(tempRml)
            val rmlStore = QuadStoreFactory.read(mappingStream)
            val factory = RecordsFactory(tempRml.parent)
            val outputStore = RDF4JStore()

            val executor = Executor(rmlStore, factory, outputStore, Utils.getBaseDirectiveTurtle(mappingStream), null)

            val result = executor.execute(null)[NamedNode("rmlmapper://default.store")]
            val out = StringWriter()
            result?.write(out, outputFormat ?: "turtle")

            return out.toString()
        }
        catch (e: Exception) {
            throw MappingFailedException(e.message!!)
        }
        finally {
            tempRml.delete()
            tempData.delete()
        }
    }

    fun outputFormat(acceptHeader: String): ContentType {
        val acceptTypes = acceptHeader.split(",")

        for(acceptType in acceptTypes) {
            if (acceptHeader != "*/*") {
                outputFormat = when (acceptType) {
                    "text/turtle" -> "turtle"
                    "application/ld+json" -> "jsonld"
                    "text/xml" -> "trix"
                    "application/n-triples" -> "ntriples"
                    else -> null
                }
            }

            if (outputFormat != null) {
                break
            }
        }

        return when (outputFormat) {
            "jsonld" -> ContentType("application", "ld+json")
            "trix" -> ContentType("text", "xml")
            "trig" -> ContentType("application", "trig")
            "nquads" -> ContentType("application", "n-triples")
            "ntriples" -> ContentType("application", "n-triples")
            else -> {
                outputFormat = "turtle"
                ContentType("text", "turtle")
            }
        }
    }
}

fun Application.configureRouting() {
    routing {
        get("/") {
            call.respondText("The QADO RML applicator is currently running. The documentation can be found on <a href=\"https://github.com/WSE-research/QADO-RML-Applicator\">Github</a>.<br/>" +
                    "The API specification can be viewed <a href=\"/openapi\">here</a>.", ContentType.Text.Html)
        }
        post("/data2rdf") {
            val accept = call.request.accept()
            val body = call.receive<DataMapping>()

            try {
                val contentType = body.outputFormat(accept!!)
                call.respondText(body.applyRml(), contentType)
            }
            catch (e: MappingFailedException) {
                call.respondText(e.message, status = HttpStatusCode.InternalServerError)
            }
        }
    }
}
