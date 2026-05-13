package com.qlink.common.error

enum class ErrorCode(val status: Int, val message: String) {
  INT_404_0001(404, "Page Not Found"),
  INT_500_0001(500, "Internal Server Error"),
}
