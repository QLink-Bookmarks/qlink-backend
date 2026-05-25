package com.qlink.folder.service

import com.qlink.common.error.BusinessException
import com.qlink.common.error.ErrorCode
import com.qlink.folder.domain.Folder
import com.qlink.folder.repository.FolderRepository
import com.qlink.link.domain.Link
import com.qlink.link.repository.LinkRepository
import com.qlink.support.BaseServiceTest
import com.qlink.support.fixture.FolderFixture
import com.qlink.support.fixture.LinkFixture
import com.qlink.support.fixture.RandomFixture
import com.qlink.support.fixture.TodoFixture
import com.qlink.support.fixture.UserFixture
import com.qlink.support.koinGet
import com.qlink.todo.domain.Todo
import com.qlink.todo.repository.TodoRepository
import com.qlink.user.domain.User
import com.qlink.user.repository.UserRepository
import io.kotest.assertions.throwables.shouldNotThrow
import io.kotest.assertions.throwables.shouldThrowWithMessage
import io.kotest.matchers.shouldBe

class DeleteFolderServiceTest :
    BaseServiceTest({
        val deleteFolderService = koinGet<DeleteFolderService>()
        val folderRepository = koinGet<FolderRepository>()
        val linkRepository = koinGet<LinkRepository>()
        val todoRepository = koinGet<TodoRepository>()
        val userRepository = koinGet<UserRepository>()

        Given("폴더 삭제 서비스 테스트") {
            lateinit var user: User
            lateinit var otherUser: User
            lateinit var folder: Folder
            lateinit var otherUserFolder: Folder

            beforeTest {
                user = userRepository.insert(UserFixture.createRandomValidUser())
                otherUser = userRepository.insert(UserFixture.createRandomValidUser())
                folder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(user.id!!))
                otherUserFolder = folderRepository.insert(FolderFixture.createValidUnsharedFolder(otherUser.id!!))
            }

            When("onDelete가 null이면") {
                lateinit var link: Link

                beforeTest {
                    link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = folder.id))
                }

                val delete =
                    suspend {
                        deleteFolderService.deleteFolder(user.id!!, folder.id!!, null)
                    }

                Then("링크는 남고 폴더 연결만 해제된다") {
                    val linkId = link.id!!

                    shouldNotThrow<BusinessException> {
                        delete()
                    }

                    folderRepository.findById(folder.id!!) shouldBe null
                    linkRepository.findById(linkId)?.folderId shouldBe null
                }
            }

            When("onDelete가 cascade이면") {
                lateinit var link: Link
                lateinit var todo: Todo

                beforeTest {
                    link = linkRepository.insert(LinkFixture.createRandomLinkOf(ownerId = user.id!!, folderId = folder.id))
                    todo = todoRepository.insert(TodoFixture.createRandomTodoOf(linkId = link.id!!, ownerId = user.id!!))
                }

                val delete =
                    suspend {
                        deleteFolderService.deleteFolder(user.id!!, folder.id!!, "CaScAdE")
                    }

                Then("링크와 할 일이 함께 삭제된다") {
                    val linkId = link.id!!
                    val todoId = todo.id!!

                    shouldNotThrow<BusinessException> {
                        delete()
                    }

                    folderRepository.findById(folder.id!!) shouldBe null
                    linkRepository.findById(linkId) shouldBe null
                    todoRepository.findById(todoId) shouldBe null
                }
            }

            When("로그인 사용자가 없으면") {
                val delete =
                    suspend {
                        deleteFolderService.deleteFolder(RandomFixture.randomId(), folder.id!!, null)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_OWNER_NOT_FOUND.message) {
                        delete()
                    }
                }
            }

            When("다른 사용자의 폴더 삭제를 시도하면") {
                val delete =
                    suspend {
                        deleteFolderService.deleteFolder(user.id!!, otherUserFolder.id!!, null)
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.FOLDER_DIFFERENT_OWNER.message) {
                        delete()
                    }
                }
            }

            When("없는 폴더를 삭제하면") {
                val delete =
                    suspend {
                        deleteFolderService.deleteFolder(user.id!!, RandomFixture.randomId(), null)
                    }

                Then("성공한다") {
                    shouldNotThrow<BusinessException> {
                        delete()
                    }
                }
            }

            When("onDelete 값이 올바르지 않으면") {
                val delete =
                    suspend {
                        deleteFolderService.deleteFolder(user.id!!, folder.id!!, "wrong")
                    }

                Then("예외를 반환한다") {
                    shouldThrowWithMessage<BusinessException>(ErrorCode.COMMON_BAD_REQUEST.message) {
                        delete()
                    }
                }
            }
        }
    })
