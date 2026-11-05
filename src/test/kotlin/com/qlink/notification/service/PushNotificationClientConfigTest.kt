package com.qlink.notification.service

import com.qlink.config.NotificationConfig
import com.qlink.device.domain.DevicePlatform
import com.qlink.push.client.ExpoPushClient
import com.qlink.push.client.FcmPushClient
import com.qlink.push.client.FirebaseAppProvider
import com.qlink.push.client.FirebaseInitializer
import com.qlink.push.client.PushNotificationSendRequest
import io.kotest.core.spec.style.BehaviorSpec
import io.kotest.matchers.collections.shouldContain
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import io.ktor.serialization.kotlinx.json.json
import io.ktor.server.config.MapApplicationConfig

class PushNotificationClientConfigTest :
    BehaviorSpec({
        Given("푸시 알림 설정 생성 테스트") {
            val yamlConfig =
                MapApplicationConfig().apply {
                    put("notification.expo.sendUrl", "https://yaml.example.com/push")
                    put("notification.expo.accessToken", "yaml-expo-token")
                    put("notification.fcm.serviceAccountJson", "yaml-fcm-json")
                }

            When("환경변수와 yaml 설정이 모두 있으면") {
                val config =
                    NotificationConfig.from(
                        config = yamlConfig,
                        env =
                            mapOf(
                                "EXPO_PUSH_SEND_URL" to "https://env.example.com/push",
                                "EXPO_ACCESS_TOKEN" to "env-expo-token",
                                "FCM_SERVICE_ACCOUNT_JSON" to "env-fcm-json",
                            ),
                    )

                Then("환경변수를 우선 사용한다") {
                    config.expo.sendUrl shouldBe "https://env.example.com/push"
                    config.expo.accessToken shouldBe "env-expo-token"
                    config.fcm.serviceAccountJson shouldBe "env-fcm-json"
                }
            }

            When("환경변수가 비어 있으면") {
                val config =
                    NotificationConfig.from(
                        config = yamlConfig,
                        env =
                            mapOf(
                                "EXPO_PUSH_SEND_URL" to "",
                                "EXPO_ACCESS_TOKEN" to "",
                                "FCM_SERVICE_ACCOUNT_JSON" to "",
                            ),
                    )

                Then("yaml 설정으로 대체한다") {
                    config.expo.sendUrl shouldBe "https://yaml.example.com/push"
                    config.expo.accessToken shouldBe "yaml-expo-token"
                    config.fcm.serviceAccountJson shouldBe "yaml-fcm-json"
                }
            }
        }

        Given("Firebase 초기화 테스트") {
            val provider = FakeFirebaseAppProvider()

            When("설정 JSON이 없으면") {
                val initializer =
                    FirebaseInitializer(
                        serviceAccountJson = null,
                        firebaseAppProvider = provider,
                    )

                initializer.initializeIfConfigured()

                Then("Firebase 초기화를 시도하지 않는다") {
                    provider.initializedJsons shouldBe emptyList()
                }
            }

            When("이미 초기화되어 있으면") {
                provider.hasApp = true
                val initializer =
                    FirebaseInitializer(
                        serviceAccountJson = """{"project_id":"qlink"}""",
                        firebaseAppProvider = provider,
                    )

                initializer.initializeIfConfigured()

                Then("재초기화하지 않는다") {
                    provider.initializedJsons shouldBe emptyList()
                }
            }

            When("FCM 설정 없이 발송하면") {
                val sender =
                    FcmPushClient(
                        firebaseInitializer =
                            FirebaseInitializer(
                                serviceAccountJson = null,
                                firebaseAppProvider = FakeFirebaseAppProvider(),
                            ),
                    )

                Then("명확한 설정 실패로 매핑한다") {
                    val result = sender.send(pushRequest())

                    result.success shouldBe false
                    result.errorMessage shouldBe "FCM service account JSON is not configured."
                }
            }
        }

        Given("Expo 푸시 재시도 테스트") {
            When("429 이후 성공 응답을 받으면") {
                val statuses = mutableListOf(HttpStatusCode.TooManyRequests, HttpStatusCode.OK)
                val delays = mutableListOf<Long>()
                val client = expoClient(statuses = statuses, delays = delays)

                Then("재시도 후 성공으로 매핑한다") {
                    val result = client.send(pushRequest())

                    result.success shouldBe true
                    result.messageId shouldBe "expo-message-id"
                    delays shouldBe listOf(100L)
                }
            }

            When("5xx 응답이 계속되면") {
                val statuses = MutableList(12) { HttpStatusCode.InternalServerError }
                val delays = mutableListOf<Long>()
                val client = expoClient(statuses = statuses, delays = delays)

                Then("최대 10회까지만 호출하고 실패로 매핑한다") {
                    val result = client.send(pushRequest())

                    result.success shouldBe false
                    statuses shouldHaveSize 2
                    delays shouldHaveSize 9
                }
            }

            When("Expo 티켓이 실패 상태이면") {
                val client =
                    expoClient(
                        statuses = mutableListOf(HttpStatusCode.OK),
                        responseBody = """{"data":{"status":"error","message":"DeviceNotRegistered"}}""",
                    )

                Then("실패로 매핑한다") {
                    val result = client.send(pushRequest())

                    result.success shouldBe false
                    result.errorMessage shouldBe "DeviceNotRegistered"
                }
            }

            When("설정한 URL과 토큰으로 발송하면") {
                val requestedUrls = mutableListOf<String>()
                val authHeaders = mutableListOf<String?>()
                val client =
                    expoClient(
                        statuses = mutableListOf(HttpStatusCode.OK),
                        requestedUrls = requestedUrls,
                        authHeaders = authHeaders,
                    )

                Then("hardcoding 없이 주입된 값을 사용한다") {
                    client.send(pushRequest())

                    requestedUrls shouldBe listOf("https://example.com/expo/send")
                    authHeaders shouldContain "Bearer expo-token"
                }
            }
        }
    })

private class FakeFirebaseAppProvider : FirebaseAppProvider {
    var hasApp: Boolean = false
    val initializedJsons: MutableList<String> = mutableListOf()

    override fun hasInitializedApp(): Boolean = hasApp

    override fun initialize(serviceAccountJson: String) {
        initializedJsons.add(serviceAccountJson)
        hasApp = true
    }
}

private fun expoClient(
    statuses: MutableList<HttpStatusCode>,
    responseBody: String = """{"data":{"status":"ok","id":"expo-message-id"}}""",
    delays: MutableList<Long> = mutableListOf(),
    requestedUrls: MutableList<String> = mutableListOf(),
    authHeaders: MutableList<String?> = mutableListOf(),
): ExpoPushClient {
    val engine =
        MockEngine { request ->
            requestedUrls.add(request.url.toString())
            authHeaders.add(request.headers[HttpHeaders.Authorization])

            respond(
                content = responseBody,
                status = statuses.removeFirst(),
                headers = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString()),
            )
        }

    return ExpoPushClient(
        httpClient =
            HttpClient(engine) {
                expectSuccess = false
                install(ContentNegotiation) {
                    json()
                }
            },
        sendUrl = "https://example.com/expo/send",
        accessToken = "expo-token",
        delayProvider = { delays.add(it) },
    )
}

private fun pushRequest(): PushNotificationSendRequest =
    PushNotificationSendRequest(
        token = "push-token",
        title = "title",
        message = "message",
        data = mapOf("platform" to DevicePlatform.NATIVE.name),
    )
