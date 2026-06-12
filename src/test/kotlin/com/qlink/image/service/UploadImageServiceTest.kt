package com.qlink.image.service

import aws.sdk.kotlin.services.s3.S3Client
import aws.sdk.kotlin.services.s3.headObject
import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.common.transaction.TransactionRunner
import com.qlink.config.S3Config
import com.qlink.image.storage.S3ImageStorage
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.ImageFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.kotest.matchers.string.shouldEndWith

class UploadImageServiceTest :
    BaseServiceTest({
        val uploadImageService = koinGet<UploadImageService>()
        val userRepository = koinGet<UserRepository>()
        val tx = koinGet<TransactionRunner>()
        val s3Client = koinGet<S3Client>()
        val s3Config = koinGet<S3Config>()

        Given("이미지 업로드 서비스 테스트") {
            lateinit var user: User

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
            }

            When("유효한 이미지 파일을 업로드하면") {
                val bytes = ImageFixture.validPng()

                Then("S3에 업로드하고 접근 URL을 반환한다") {
                    val response = uploadImageService.upload(user.id!!, bytes)

                    response.url shouldContain s3Config.bucket
                    response.url shouldEndWith ".png"

                    val key = response.url.substringAfter("${s3Config.bucket}/")
                    val head =
                        s3Client.headObject {
                            bucket = s3Config.bucket
                            this.key = key
                        }
                    head.contentType shouldBe "image/png"
                }
            }

            When("로그인 사용자가 없으면") {
                Then("IMAGE_OWNER_NOT_FOUND 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_OWNER_NOT_FOUND.message) {
                        uploadImageService.upload(RandomFixture.randomId(), ImageFixture.validPng())
                    }
                }
            }

            When("이미지 파일 형식이 아니면") {
                val bytes = ImageFixture.notAnImage()

                Then("IMAGE_INVALID_FORMAT 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_INVALID_FORMAT.message) {
                        uploadImageService.upload(user.id!!, bytes)
                    }
                }
            }

            When("이미지 파일이 비어 있으면") {
                Then("IMAGE_FILE_REQUIRED 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_FILE_REQUIRED.message) {
                        uploadImageService.upload(user.id!!, null)
                    }
                }
            }

            When("S3 업로드 중 AWS 에러가 발생하면") {
                val failingService =
                    UploadImageService(
                        tx = tx,
                        userRepository = userRepository,
                        imageStorage = S3ImageStorage(s3Client, s3Config.copy(bucket = "qlink-nonexistent-bucket")),
                    )

                Then("IMAGE_UPLOAD_FAILED 예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.IMAGE_UPLOAD_FAILED.message) {
                        failingService.upload(user.id!!, ImageFixture.validPng())
                    }
                }
            }
        }
    })
