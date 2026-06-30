package com.qlink.common.text

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe

class StringExtensionsTest :
    StringSpec({
        "최대 길이 이하면 그대로 둔다" {
            "abcde".truncate(5) shouldBe "abcde"
            "abc".truncate(5) shouldBe "abc"
        }

        "최대 길이를 넘으면 … 를 붙여 자른다" {
            "abcdef".truncate(5) shouldBe "abcd…"
            "abcdef".truncate(5).length shouldBe 5
        }

        "한글도 글자 수 기준으로 자른다" {
            "가나다라마바".truncate(4) shouldBe "가나다…"
        }
    })
