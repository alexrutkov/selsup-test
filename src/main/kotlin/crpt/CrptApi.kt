package crpt

import com.fasterxml.jackson.databind.ObjectMapper
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

interface CrptApi {
	fun createDocument(command: CreateDocumentCommand): CreateDocumentResponse
}

class CrptApiImpl(
	private val timeUnit: TimeUnit,
	private val requestLimit: Int,
	private val token: String
) : CrptApi {
	private val uri = URI.create("https://ismp.crpt.ru/api/v3/lk/documents/create")
	private val objectMapper = ObjectMapper()
	private val httpClient = HttpClient.newBuilder().build()

	private val defaultRequest = HttpRequest.newBuilder()
		.uri(uri)
		.header("Content-Type", "application/json")
		.header("Authorization", "Bearer $token")

	private val semaphore = Semaphore(requestLimit, true)
	private val scheduler: ScheduledExecutorService = Executors.newSingleThreadScheduledExecutor()

	init {
		scheduler.scheduleAtFixedRate(
			{ semaphore.drainPermits(); semaphore.release(requestLimit) },
			timeUnit.toMillis(1),
			timeUnit.toMillis(1),
			TimeUnit.MILLISECONDS
		)
	}

	override fun createDocument(command: CreateDocumentCommand): CreateDocumentResponse {
		semaphore.acquire()
		val request = defaultRequest.copy()
			.POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(command)))
			.build()
		val response = httpClient.send(request, HttpResponse.BodyHandlers.ofString())
		return objectMapper.readValue(response.body(), CreateDocumentResponse::class.java)
	}
}


data class CreateDocumentCommand(
	val document: String,
	val signature: String,
	val format: String,
	val group: String,
	val type: String
)

data class CreateDocumentResponse(
	val value: UUID
)
